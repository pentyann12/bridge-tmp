package com.example.parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** CORBA ↔ DTO の型変換・名前解決に関するユーティリティ */
final class CorbaTypeUtils {
  private CorbaTypeUtils() {}

  /** "Operations" サフィックスを取り除いたサービス名を返す */
  static String toServiceName(ClassOrInterfaceDeclaration serviceIface) {
    String name = serviceIface.getNameAsString();
    return name.endsWith("Operations")
        ? name.substring(0, name.length() - "Operations".length())
        : name;
  }

  /** 完全修飾型名から単純名を取り出す（例: "com.pkg.Foo" → "Foo"、"Foo" → "Foo"） */
  static String simpleName(String type) {
    return type.contains(".") ? type.substring(type.lastIndexOf('.') + 1) : type;
  }

  /** 文字列の先頭を大文字にする */
  static String capitalize(String s) {
    return (s == null || s.isEmpty()) ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  /**
   * プリミティブ型または String かどうかを判定（配列は要素型で再帰判定） String を含むのは、IDL の string 型が Java の String にマップされ、 DTO
   * 変換不要のプリミティブ相当として扱うためです
   */
  static boolean isPrimitiveType(String type) {
    if (type == null) return false;
    if (type.endsWith("[]")) return isPrimitiveType(type.substring(0, type.length() - 2));
    switch (type) {
      case "int":
      case "long":
      case "short":
      case "byte":
      case "boolean":
      case "float":
      case "double":
      case "char":
      case "String":
        return true;
      default:
        return false;
    }
  }

  /** Holder 型（CORBA out/inout パラメーター）かどうかを判定 単純名が "Holder" で終わるクラスを Holder 型とみなす */
  static boolean isHolderType(String typeName) {
    return simpleName(typeName).endsWith("Holder");
  }

  /**
   * メソッド定義の import 文を参照して単純型名を完全修飾名に解決
   *
   * <p>JavaParser はシンボル解決なしでパースするため、IDLから生成されたスタブの import 文をスキャンして正しいパッケージを特定 グローバルスコープの型が
   * idlPackagePrefix 直下へ再配置された場合でも正しく解決できます
   *
   * @param type 解決したい型名（単純名または配列型、例: "GlobalTag[]"）
   * @param method 型が参照されているメソッド宣言（import 文のスキャンに使用）
   * @param fallbackPackage import で見つからない場合のフォールバックパッケージ
   * @return 完全修飾型名（すでに FQN または primitive の場合はそのまま返す）
   */
  static String resolveTypeFQN(String type, MethodDeclaration method, String fallbackPackage) {
    if (type.contains(".") || isPrimitiveType(type) || "void".equals(type)) return type;
    boolean isArray = type.endsWith("[]");
    String elem = isArray ? type.substring(0, type.length() - 2) : type;
    String fqn =
        method
            .findCompilationUnit()
            .map(
                cu ->
                    cu.getImports().stream()
                        .filter(imp -> !imp.isAsterisk())
                        .filter(imp -> imp.getNameAsString().endsWith("." + elem))
                        .findFirst()
                        .map(imp -> imp.getNameAsString())
                        .orElse(null))
            .orElse(null);
    if (fqn == null && !fallbackPackage.isEmpty()) fqn = fallbackPackage + "." + elem;
    if (fqn == null) return type;
    return isArray ? fqn + "[]" : fqn;
  }

  /**
   * Holder 型（CORBA out/inout パラメーター）の {@code value} フィールドの型名を返す
   *
   * <p>CORBA プリミティブ Holder（{@code org.omg.CORBA.IntHolder} 等）はハードコードで対応し、 独自 Holder はスタブファイルを解析して
   * {@code value} フィールドの型を取得 idlj は sequence typedef に対して配列型の {@code value} フィールドを生成するため （例: {@code
   * GlobalTag value[]}）、ファイル解析が必要です
   *
   * @param holderType Holder 型の名前（単純名または FQN）
   * @param method 型が参照されているメソッド宣言
   * @param fallbackPackage FQN 解決のフォールバックパッケージ
   * @param stubRoot スタブのルートディレクトリ（Holder ファイル解析に使用）
   * @return value フィールドの型名
   */
  static String resolveHolderValueType(
      String holderType, MethodDeclaration method, String fallbackPackage, Path stubRoot) {
    String fqn =
        holderType.contains(".") ? holderType : resolveTypeFQN(holderType, method, fallbackPackage);
    switch (fqn) {
      case "org.omg.CORBA.IntHolder":
        return "int";
      case "org.omg.CORBA.LongHolder":
        return "long";
      case "org.omg.CORBA.ShortHolder":
        return "short";
      case "org.omg.CORBA.BooleanHolder":
        return "boolean";
      case "org.omg.CORBA.FloatHolder":
        return "float";
      case "org.omg.CORBA.DoubleHolder":
        return "double";
      case "org.omg.CORBA.StringHolder":
        return "String";
      case "org.omg.CORBA.ByteHolder":
        return "byte";
      case "org.omg.CORBA.CharHolder":
        return "char";
      case "org.omg.CORBA.AnyHolder":
        return "org.omg.CORBA.Any";
    }
    if (fqn.contains(".") && stubRoot != null) {
      String pkg = fqn.substring(0, fqn.lastIndexOf('.'));
      String sName = fqn.substring(fqn.lastIndexOf('.') + 1);
      Path holderFile = stubRoot.resolve(pkg.replace('.', '/') + "/" + sName + ".java");
      if (Files.exists(holderFile)) {
        try {
          return StaticJavaParser.parse(holderFile)
              .findAll(ClassOrInterfaceDeclaration.class)
              .stream()
              .flatMap(c -> c.getFields().stream())
              .flatMap(f -> f.getVariables().stream())
              .filter(v -> v.getNameAsString().equals("value"))
              .findFirst()
              .map(v -> v.getType().asString())
              .orElseGet(() -> stripHolder(fqn));
        } catch (IOException e) {
          // フォールバックへ
        }
      }
    }
    return stripHolder(fqn);
  }

  /** "FooHolder" → "Foo" のように Holder サフィックスを除去する */
  static String stripHolder(String fqn) {
    return fqn.endsWith("Holder") ? fqn.substring(0, fqn.length() - "Holder".length()) : fqn;
  }

  /**
   * CORBA 型名をジェネレーター内部の DTO 型名へマップ
   *
   * <ul>
   *   <li>配列型: 要素型を再帰変換して {@code []} を付け直す（例: {@code com.pkg.Foo[] → dto.FooDto[]}）
   *   <li>{@code org.omg.CORBA.Any}: {@code AnyValue} へマップ
   *   <li>FQN を持つ型: 単純名 + {@code "Dto"}
   *   <li>プリミティブ/String: そのまま返す
   *   <li>単純名の独自型: パッケージプレフィックスを追加して {@code "Dto"} を付ける
   * </ul>
   *
   * @param basePackage ベースパッケージ名
   * @param typeNameString マップ元の型名
   * @return DTO 型名
   */
  static String mapToDtoType(String basePackage, String typeNameString) {
    if (typeNameString.endsWith("[]")) {
      return mapToDtoType(basePackage, typeNameString.substring(0, typeNameString.length() - 2))
          + "[]";
    }
    if ("org.omg.CORBA.Any".equals(typeNameString)) return basePackage + ".dto.AnyValue";
    if (typeNameString.contains("."))
      return basePackage + ".dto." + simpleName(typeNameString) + "Dto";
    if (isPrimitiveType(typeNameString)) return typeNameString;
    return basePackage + ".dto." + typeNameString + "Dto";
  }

  /**
   * CORBA 値を DTO へ変換する式（代入 RHS として使える文字列）を返す
   *
   * <p>{@code returnAsDto=false} またはプリミティブ/String/Any の場合は {@code rhs} をそのまま返す 配列型は {@code
   * Arrays.stream().map(Mapper::toDto).toArray()} 形式、 独自型は {@code XxxMapper.toDto(rhs)} 形式になります
   *
   * @param rhs 変換元の式（例: "result", "arg0.value"）
   * @param valueType CORBA 側の型名（配列型 "Foo[]" でも可）
   * @param basePackage ベースパッケージ名
   * @param dtoPackage DTO パッケージ名
   * @param returnAsDto false の場合は変換を行わず rhs をそのまま返す
   * @return 代入 RHS として使える式文字列
   */
  static String corbaToDtoExpr(
      String rhs, String valueType, String basePackage, String dtoPackage, boolean returnAsDto) {
    if (!returnAsDto || isPrimitiveType(valueType) || "org.omg.CORBA.Any".equals(valueType)) {
      return rhs;
    }
    boolean isArray = valueType.endsWith("[]");
    String elem = isArray ? valueType.substring(0, valueType.length() - 2) : valueType;
    String elemSimple = simpleName(elem);
    if (isArray) {
      return rhs
          + " == null ? null : java.util.Arrays.stream("
          + rhs
          + ")"
          + ".map("
          + basePackage
          + ".mapper."
          + elemSimple
          + "Mapper::toDto)"
          + ".toArray("
          + dtoPackage
          + "."
          + elemSimple
          + "Dto[]::new)";
    }
    return basePackage + ".mapper." + elemSimple + "Mapper.toDto(" + rhs + ")";
  }

  /**
   * 型名文字列を TypeName へ変換する
   *
   * @param type 変換元の型名
   * @param dtoPkgName DTO パッケージ名
   * @return TypeName
   */
  static TypeName convToTypeName(String type, String dtoPkgName) {
    if (type.endsWith("[]")) {
      return ArrayTypeName.of(convToTypeName(type.substring(0, type.length() - 2), dtoPkgName));
    }
    switch (type) {
      case "int":
        return TypeName.INT;
      case "long":
        return TypeName.LONG;
      case "short":
        return TypeName.SHORT;
      case "byte":
        return TypeName.BYTE;
      case "boolean":
        return TypeName.BOOLEAN;
      case "float":
        return TypeName.FLOAT;
      case "double":
        return TypeName.DOUBLE;
      case "char":
        return TypeName.CHAR;
      case "String":
        return ClassName.get(String.class);
      case "Object":
        return ClassName.get(Object.class);
    }
    if (type.startsWith(dtoPkgName + ".")) {
      return ClassName.get(dtoPkgName, type.substring(type.lastIndexOf('.') + 1));
    }
    if (type.contains(".")) {
      return ClassName.get(
          type.substring(0, type.lastIndexOf('.')), type.substring(type.lastIndexOf('.') + 1));
    }
    return ClassName.get("", type);
  }
}
