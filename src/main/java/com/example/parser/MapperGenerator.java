package com.example.parser;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.squareup.javapoet.*;
import java.io.IOException;
import java.nio.file.Path;
import javax.lang.model.element.Modifier;

/** CORBA ⇔ DTO のMapper自動生成クラス */
public class MapperGenerator {

  /**
   * 指定クラスからMapperクラスを生成する
   *
   * @param clazz 対象クラス
   * @param outputDir 生成ファイルの出力先ディレクトリ
   * @param basePackage 生成クラスのベースパッケージ
   * @throws IOException ファイル出力エラー
   */
  public static void generate(ClassOrInterfaceDeclaration clazz, Path outputDir, String basePackage)
      throws IOException {
    String name = clazz.getNameAsString();
    String dtoName = name + "Dto";
    String mapperName = name + "Mapper";
    String dtoPackage = basePackage + ".dto";
    ClassName dtoClass = ClassName.get(dtoPackage, dtoName);
    String corbaPackage =
        clazz
            .findCompilationUnit()
            .flatMap(cu -> cu.getPackageDeclaration().map(pd -> pd.getNameAsString()))
            .orElse("");
    ClassName corbaClass =
        corbaPackage.isEmpty() ? ClassName.bestGuess(name) : ClassName.get(corbaPackage, name);

    TypeSpec.Builder mapper =
        TypeSpec.classBuilder(mapperName).addModifiers(Modifier.PUBLIC, Modifier.FINAL);

    MethodSpec.Builder toDtoBody =
        MethodSpec.methodBuilder("toDto")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(dtoClass)
            .addParameter(corbaClass, "src")
            .addStatement("$T dto = new $T()", dtoClass, dtoClass);

    MethodSpec.Builder fromDtoBody =
        MethodSpec.methodBuilder("fromDto")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(corbaClass)
            .addParameter(dtoClass, "src")
            .addStatement("$T dst = new $T()", corbaClass, corbaClass);

    ClassName anyMapperClass = ClassName.get(basePackage + ".mapper", "AnyMapper");

    for (FieldDeclaration field : clazz.getFields()) {
      String type = field.getElementType().asString();
      String simple = type.contains(".") ? type.substring(type.lastIndexOf('.') + 1) : type;
      boolean isAny = type.equals("org.omg.CORBA.Any");
      boolean isPrimitive = isPrimitiveType(simple);

      for (var v : field.getVariables()) {
        String nameField = v.getNameAsString();
        boolean isArray = v.getType().asString().endsWith("[]");

        if (isAny) {
          toDtoBody.addStatement(
              "dto.$N = $T.toAnyValue($N.$N)", nameField, anyMapperClass, "src", nameField);
          fromDtoBody.addStatement(
              "dst.$N = $T.toAny(src.$N)", nameField, anyMapperClass, nameField);
        } else if (isPrimitive) {
          toDtoBody.addStatement("dto.$N = $N.$N", nameField, "src", nameField);
          fromDtoBody.addStatement("dst.$N = src.$N", nameField, nameField);
        } else if (isArray) {
          // 独自型の配列: 各要素を対応する Mapper で変換する
          ClassName nestedMapperClass = ClassName.get(basePackage + ".mapper", simple + "Mapper");
          ClassName dtoElemClass = ClassName.get(dtoPackage, simple + "Dto");

          // 要素型のパッケージを含む構造体のimport文から逆引きする。
          // NOTE:
          // グローバルスコープ由来で別パッケージに再配置された型の場合、
          // corbaPackage (含む構造体のパッケージ) と異なるため import を参照する必要がある。
          String elemPkg =
              clazz
                  .findCompilationUnit()
                  .map(
                      cu ->
                          cu.getImports().stream()
                              .filter(imp -> !imp.isAsterisk())
                              .filter(imp -> imp.getNameAsString().endsWith("." + simple))
                              .findFirst()
                              .map(
                                  imp -> {
                                    String fqn = imp.getNameAsString();
                                    return fqn.substring(0, fqn.lastIndexOf('.'));
                                  })
                              .orElse(null))
                  .orElse(null);
          ClassName corbaElemClass =
              elemPkg != null
                  ? ClassName.get(elemPkg, simple)
                  : (corbaPackage.isEmpty()
                      ? ClassName.bestGuess(simple)
                      : ClassName.get(corbaPackage, simple));
          toDtoBody.addStatement(
              "dto.$N = src.$N == null ? null : java.util.Arrays.stream(src.$N).map($T::toDto).toArray($T[]::new)",
              nameField,
              nameField,
              nameField,
              nestedMapperClass,
              dtoElemClass);
          fromDtoBody.addStatement(
              "dst.$N = src.$N == null ? null : java.util.Arrays.stream(src.$N).map($T::fromDto).toArray($T[]::new)",
              nameField,
              nameField,
              nameField,
              nestedMapperClass,
              corbaElemClass);
        } else {
          ClassName nestedMapperClass = ClassName.get(basePackage + ".mapper", simple + "Mapper");
          toDtoBody.addStatement(
              "dto.$N = $T.toDto($N.$N)", nameField, nestedMapperClass, "src", nameField);
          fromDtoBody.addStatement(
              "dst.$N = $T.fromDto(src.$N)", nameField, nestedMapperClass, nameField);
        }
      }
    }

    toDtoBody.addStatement("return dto");
    fromDtoBody.addStatement("return dst");

    mapper.addMethod(toDtoBody.build());
    mapper.addMethod(fromDtoBody.build());

    JavaFile.builder(basePackage + ".mapper", mapper.build()).build().writeTo(outputDir);
  }

  /**
   * プリミティブ型か否か判定する
   *
   * @param type 型名
   * @return 判定結果
   */
  private static boolean isPrimitiveType(String type) {
    if (type == null) return false;
    switch (type) {
      case "int":
      case "long":
      case "short":
      case "byte":
      case "boolean":
      case "float":
      case "double":
      case "char":
      case "String": // CORBA IDLのstringはJavaのStringにマッピングされるため、プリミティブ扱いする
        return true;
      default:
        return false;
    }
  }
}
