package com.toru_otsuki.bridge_generator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** CORBA↔DTOの型変換・名前解決に関するユーティリティ */
final class CorbaTypeUtils {
  private CorbaTypeUtils() {}

  /**
   * JSON-RPCレスポンスの`result.return`フィールドの型名を返す
   *
   * <p>voidなら`Object`、それ以外は戻り値型に応じたDTO/プリミティブ型を返します。
   *
   * @param method サービスインターフェースのメソッド宣言
   * @param ctx コード生成コンテキスト
   * @return 型名文字列
   */
  static String resolveRpcReturnType(MethodDeclaration method, GenContext ctx) {
    String returnType = method.getType().asString();
    if ("void".equals(returnType)) return "Object";
    if (isPrimitiveType(returnType)) return returnType;
    if ("org.omg.CORBA.Any".equals(returnType))
      return ctx.returnAsDto() ? ctx.basePackage() + ".dto.AnyValue" : "org.omg.CORBA.Any";
    if (!ctx.returnAsDto()) return resolveTypeFQN(returnType, method, ctx.servicePackage());
    return mapToDtoType(ctx.basePackage(), returnType);
  }

  /**
   * "Operations" サフィックスを取り除いたサービス名を返す
   *
   * @param serviceIface サービスインターフェースのクラス宣言
   * @return "Operations" サフィックスを除いたサービス名
   */
  static String toServiceName(ClassOrInterfaceDeclaration serviceIface) {
    String name = serviceIface.getNameAsString();
    return name.endsWith("Operations")
        ? name.substring(0, name.length() - "Operations".length())
        : name;
  }

  /**
   * 完全修飾型名から単純名を取り出す（例: "com.pkg.Foo" → "Foo"、"Foo" → "Foo"）
   *
   * @param type 完全修飾型名または単純名
   * @return 単純名
   */
  static String simpleName(String type) {
    return type.contains(".") ? type.substring(type.lastIndexOf('.') + 1) : type;
  }

  /**
   * 文字列の先頭を大文字にする
   *
   * @param s 入力文字列
   * @return 先頭を大文字にした文字列。nullまたは空文字列の場合はそのまま返す
   */
  static String capitalize(String s) {
    return (s == null || s.isEmpty()) ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  /**
   * プリミティブ型またはStringかどうかを判定（配列は要素型で再帰判定）Stringを含むのは、IDLのstring型がJavaのStringにマップされ、DTO
   * 変換不要のプリミティブ相当として扱うためです
   *
   * @param type 判定する型名
   * @return プリミティブ型またはStringであれば`true`
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

  /**
   * Holder型（CORBA out/inoutパラメーター）かどうかを判定 単純名が "Holder" で終わるクラスをHolder型とみなす
   *
   * @param typeName 型名（単純名またはFQN）
   * @return Holder型であれば`true`
   */
  static boolean isHolderType(String typeName) {
    return simpleName(typeName).endsWith("Holder");
  }

  /**
   * メソッド定義のimport文を参照して単純型名を完全修飾名に解決
   *
   * <p>JavaParserはシンボル解決なしでパースするため、IDLから生成されたスタブのimport文をスキャンして正しいパッケージを特定 グローバルスコープの型が
   * idlPackagePrefix直下へ再配置された場合でも正しく解決できます
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
   * Holder型（CORBA out/inoutパラメーター）の`value`フィールドの型名を返す
   *
   * <p>CORBAプリミティブHolder（`org.omg.CORBA.IntHolder`等）はハードコードで対応し、
   * 独自Holderはスタブファイルを解析して`value`フィールドの型を取得します。 idljはsequence typedefに対して配列型の`value`フィールドを生成するため（例:
   * `GlobalTag value[]`）、ファイル解析が必要です
   *
   * @param holderType Holder型の名前（単純名またはFQN）
   * @param method 型が参照されているメソッド宣言
   * @param ctx コード生成コンテキスト
   * @return `value`フィールドの型名
   */
  static String resolveHolderValueType(
      String holderType, MethodDeclaration method, GenContext ctx) {
    String fqn =
        holderType.contains(".")
            ? holderType
            : resolveTypeFQN(holderType, method, ctx.servicePackage());
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
    if (fqn.contains(".") && ctx.stubRoot() != null) {
      String pkg = fqn.substring(0, fqn.lastIndexOf('.'));
      String sName = fqn.substring(fqn.lastIndexOf('.') + 1);
      Path holderFile = ctx.stubRoot().resolve(pkg.replace('.', '/') + "/" + sName + ".java");
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

  /**
   * "FooHolder" → "Foo" のようにHolderサフィックスを除去する
   *
   * @param fqn Holderサフィックスを持つ型名（単純名またはFQN）
   * @return Holderサフィックスを除去した型名
   */
  static String stripHolder(String fqn) {
    return fqn.endsWith("Holder") ? fqn.substring(0, fqn.length() - "Holder".length()) : fqn;
  }

  /**
   * CORBA型名をジェネレーター内部のDTO型名へマップ
   *
   * <p>- 配列型: 要素型を再帰変換して`[]`を付け直す（例: `com.pkg.Foo[]` → `dto.FooDto[]`） - `org.omg.CORBA.Any`:
   * `AnyValue` へマップ - FQNを持つ型: 単純名 + `"Dto"` - プリミティブ/String: そのまま返す - 単純名の独自型:
   * パッケージプレフィックスを追加して`"Dto"`を付ける
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
   * CORBA値をDTOへ変換する式（代入RHSとして使える文字列）を返す
   *
   * <p>Stringは`returnAsDto`フラグに関係なく常に`StringMapper.encode()`でエンコードします
   * （CORBAの文字列はISO-8859-1として渡されるSJISバイト列のため）。 それ以外のプリミティブ/Anyは`returnAsDto=false`のときそのまま返します。
   * 配列型は`Arrays.stream().map(Mapper::toDto).toArray()`形式、独自型は`XxxMapper.toDto(rhs)`形式になります
   *
   * @param rhs 変換元の式（例: "result", "arg0.value"）
   * @param valueType CORBA側の型名（配列型 "Foo[]" でも可）
   * @param ctx コード生成コンテキスト
   * @return 代入RHSとして使える式文字列
   */
  static String corbaToDtoExpr(String rhs, String valueType, GenContext ctx) {
    // StringはCORBAの文字コード（ISO-8859-1として渡されるSJISバイト）をSJISとして再解釈する
    if ("String".equals(valueType)) {
      return ctx.basePackage() + ".mapper.StringMapper.encode(" + rhs + ")";
    }
    if (!ctx.returnAsDto() || isPrimitiveType(valueType) || "org.omg.CORBA.Any".equals(valueType)) {
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
          + ctx.basePackage()
          + ".mapper."
          + elemSimple
          + "Mapper::toDto)"
          + ".toArray("
          + ctx.dtoPackage()
          + "."
          + elemSimple
          + "Dto[]::new)";
    }
    return ctx.basePackage() + ".mapper." + elemSimple + "Mapper.toDto(" + rhs + ")";
  }

  /**
   * 型名文字列をTypeNameへ変換する
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
