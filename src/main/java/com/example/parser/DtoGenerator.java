package com.example.parser;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.squareup.javapoet.*;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.nio.file.Path;

/**
 * DTO生成クラス
 *
 * JavaParserで解析したIDL由来のクラス情報をもとに、
 * JavaPoetを使用してDTOクラスを生成する
 *
 * <ul>
 * <li>クラス名からDTOクラス名への変換</li>
 * <li>フィールド型を変換（CORBA.Any → Object）</li>
 * <li>Javaソースコード生成とファイル出力</li>
 * </ul>
 */
public class DtoGenerator {
  /**
   * DTOクラスを生成し、指定ディレクトリに出力する
   *
   * @param clazz     解析済みクラス定義（IDL由来）
   * @param outputDir 出力先ディレクトリ
   */
  public static void generate(
      ClassOrInterfaceDeclaration clazz,
      Path outputDir,
      String basePackage) throws IOException {
    String className = clazz.getNameAsString();
    String dtoName = className + "Dto";
    String dtoPackage = basePackage + ".dto";

    // DTO用のクラスを作成し、全てのフィールドをDTOにコピーする
    // このとき、フィールドの型はDTO用の型へ変換する
    TypeSpec.Builder typeBuilder = TypeSpec.classBuilder(dtoName)
        .addModifiers(Modifier.PUBLIC);

    for (FieldDeclaration field : clazz.getFields()) {
      // getElementType() は [] を落とすため、変数ごとの型 (v.getType()) を使う
      // 例: "int relatedIds[]" → v.getType().asString() = "int[]"
      field.getVariables().forEach(v -> {
        String originalType = v.getType().asString();
        String dtoType = convertType(originalType);
        FieldSpec fieldSpec = FieldSpec.builder(
            toTypeName(dtoType, dtoPackage),
            v.getNameAsString(),
            Modifier.PUBLIC)
            .build();

        typeBuilder.addField(fieldSpec);
      });
    }

    // Javaファイルとして出力する
    JavaFile javaFile = JavaFile.builder(
        dtoPackage,
        typeBuilder.build())
        .build();
    javaFile.writeTo(outputDir);
  }

  /**
   * IDL由来の型名をDTO用の型名に変換する
   *
   * @param type 元の型名
   * @return 変換後の型名
   */
  private static String convertType(String type) {
    // 配列型: 要素型を変換して [] を戻す (例: int[] → int[], FooClass[] → FooClassDto[])
    if (type.endsWith("[]")) {
      return convertType(type.substring(0, type.length() - 2)) + "[]";
    }

    // 型名変換
    if (type.equals("org.omg.CORBA.Any")) {
      return "AnyValue";
    }

    // 名前空間変換
    if (type.contains(".")) {
      return type.substring(type.lastIndexOf(".") + 1) + "Dto";
    }

    return type;
  }

  /**
   * 型名文字列をJavaPoetのTypeNameへ変換する
   *
   * @param type 変換後の型名
   * @return TypeName
   */
  private static TypeName toTypeName(String type, String dtoPackage) {
    // 配列型: 要素型を再帰解決して ArrayTypeName を返す
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