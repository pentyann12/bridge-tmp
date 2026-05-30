package com.example.parser;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.squareup.javapoet.*;
import java.io.IOException;
import java.nio.file.Path;
import javax.lang.model.element.Modifier;

/**
 * DTO生成クラス
 *
 * JavaParserで解析したIDL由来のクラス情報をもとに、 JavaPoetを使用してDTOクラスを生成する
 *
 * - クラス名からDTOクラス名への変換
 * - フィールド型を変換（CORBA.Any → Object）
 * - Javaソースコード生成とファイル出力
 */
public class DtoGenerator {
  /**
   * スタブクラスからDTOクラスを生成し、指定ディレクトリに出力する
   *
   * @param clazz スタブクラス定義
   * @param outputDir 出力先ディレクトリ
   */
  public static void generate(ClassOrInterfaceDeclaration clazz, Path outputDir, String basePackage)
      throws IOException {
    String className = clazz.getNameAsString();
    String dtoName = className + "Dto";
    String dtoPackage = basePackage + ".dto";

    // DTO用のクラスを作成し、全てのフィールドをDTOにコピーする
    // このとき、フィールドの型はDTO用の型へ変換する
    TypeSpec.Builder typeBuilder = TypeSpec.classBuilder(dtoName).addModifiers(Modifier.PUBLIC);

    for (FieldDeclaration field : clazz.getFields()) {
      field
          .getVariables()
          .forEach(
              v -> {
                String originalType = v.getType().asString();
                String dtoType = convertType(originalType);
                FieldSpec fieldSpec =
                    FieldSpec.builder(
                            toTypeName(dtoType, dtoPackage), v.getNameAsString(), Modifier.PUBLIC)
                        .build();

                typeBuilder.addField(fieldSpec);
              });
    }

    JavaFile javaFile = JavaFile.builder(dtoPackage, typeBuilder.build()).build();
    javaFile.writeTo(outputDir);
  }

  /**
   * IDL由来の型名をDTO用の型名に変換する
   *
   * @param type 元の型名
   * @return 変換後の型名
   */
  private static String convertType(String type) {
    // 配列: 剥がして再帰する
    if (type.endsWith("[]")) {
      return convertType(type.substring(0, type.length() - 2)) + "[]";
    }

    if (type.equals("org.omg.CORBA.Any")) {
      return "AnyValue";
    }

    // 完全修飾名: 末尾の単純名からDtoへ置換する
    if (type.contains(".")) {
      return type.substring(type.lastIndexOf(".") + 1) + "Dto";
    }

    // 単純名: 組み込み型でなければ独自型としてDtoへ置換する
    if (!isBuiltinJavaType(type)) {
      return type + "Dto";
    }

    return type;
  }

  /**
   * Java組み込み型であるか否か判定する
   * 
   * @param type 型名
   * @return 判定結果
   */
  private static boolean isBuiltinJavaType(String type) {
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
      case "Object":
      case "AnyValue":
        return true;
      default:
        return false;
    }
  }

  /**
   * 型名文字列をJavaPoetのTypeNameへ変換する
   *
   * @param type 変換後の型名
   * @return TypeName
   */
  private static TypeName toTypeName(String type, String dtoPackage) {
    // 配列型: 要素型を再帰してArrayTypeNameとして返す
    if (type.endsWith("[]")) {
      return ArrayTypeName.of(toTypeName(type.substring(0, type.length() - 2), dtoPackage));
    }

    return switch (type) {
      case "int" -> TypeName.INT;
      case "long" -> TypeName.LONG;
      case "boolean" -> TypeName.BOOLEAN;
      case "short" -> TypeName.SHORT;
      case "byte" -> TypeName.BYTE;
      case "float" -> TypeName.FLOAT;
      case "double" -> TypeName.DOUBLE;
      case "char" -> TypeName.CHAR;
      case "String" -> ClassName.get(String.class);
      case "Object" -> ClassName.get(Object.class);
      case "AnyValue" -> ClassName.get(dtoPackage, "AnyValue");
      default -> ClassName.get(dtoPackage, type);
    };
  }
}
