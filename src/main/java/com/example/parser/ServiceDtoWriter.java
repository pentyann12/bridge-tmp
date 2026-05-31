package com.example.parser;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.element.Modifier;

/** サービスメソッド用 RequestDto / ResponseDto と application.yml を出力するユーティリティ */
final class ServiceDtoWriter {
  private ServiceDtoWriter() {}

  /**
   * CORBA サービスメソッド用のリクエスト DTO を出力する
   *
   * @param method サービスメソッドの宣言
   * @param outputRoot Java ソースのルートディレクトリ
   * @param basePkgName ベースパッケージ名
   */
  static void generateRequestDto(MethodDeclaration method, Path outputRoot, String basePkgName)
      throws IOException {
    String dtoPkgName = basePkgName + ".dto";
    TypeSpec.Builder typeBuilder =
        TypeSpec.classBuilder(CorbaTypeUtils.capitalize(method.getNameAsString()) + "RequestDto")
            .addModifiers(Modifier.PUBLIC);

    for (Parameter param : method.getParameters()) {
      if (CorbaTypeUtils.isHolderType(param.getType().asString())) {
        // out引数はリクエストボディから除外する
        continue;
      }
      String fieldType = CorbaTypeUtils.mapToDtoType(basePkgName, param.getType().asString());
      typeBuilder.addField(
          FieldSpec.builder(
                  CorbaTypeUtils.convToTypeName(fieldType, dtoPkgName),
                  param.getNameAsString(),
                  Modifier.PUBLIC)
              .build());
    }

    JavaFile.builder(dtoPkgName, typeBuilder.build()).build().writeTo(outputRoot);
  }

  /**
   * out引数を持つメソッド専用のレスポンス DTO を出力 非void戻り値は {@code returnValue} フィールド、out引数はパラメーター名のフィールドになります
   *
   * @param method サービスメソッドの宣言
   * @param outputRoot Java ソースのルートディレクトリ
   * @param basePkgName ベースパッケージ名
   * @param returnAsDto true の場合、複雑な型を DTO 型に変換する
   * @param servicePackage サービスが属するパッケージ（FQN 解決のフォールバック用）
   * @param stubRoot Holder の value 型解決でスタブファイルを参照するために使用
   */
  static void generateResponseDto(
      MethodDeclaration method,
      Path outputRoot,
      String basePkgName,
      boolean returnAsDto,
      String servicePackage,
      Path stubRoot)
      throws IOException {
    String dtoPkgName = basePkgName + ".dto";
    TypeSpec.Builder typeBuilder =
        TypeSpec.classBuilder(CorbaTypeUtils.capitalize(method.getNameAsString()) + "ResponseDto")
            .addModifiers(Modifier.PUBLIC);

    String returnType = method.getType().asString();
    if (!"void".equals(returnType)) {
      String fieldType =
          returnAsDto
              ? CorbaTypeUtils.mapToDtoType(basePkgName, returnType)
              : CorbaTypeUtils.resolveTypeFQN(returnType, method, servicePackage);
      typeBuilder.addField(
          FieldSpec.builder(
                  CorbaTypeUtils.convToTypeName(fieldType, dtoPkgName),
                  "returnValue",
                  Modifier.PUBLIC)
              .build());
    }

    for (Parameter param : method.getParameters()) {
      String paramType = param.getType().asString();
      if (!CorbaTypeUtils.isHolderType(paramType)) continue;
      String valueType =
          CorbaTypeUtils.resolveHolderValueType(paramType, method, servicePackage, stubRoot);
      String fieldType =
          returnAsDto
              ? CorbaTypeUtils.mapToDtoType(basePkgName, valueType)
              : (valueType.contains(".")
                  ? valueType
                  : CorbaTypeUtils.resolveTypeFQN(valueType, method, servicePackage));
      typeBuilder.addField(
          FieldSpec.builder(
                  CorbaTypeUtils.convToTypeName(fieldType, dtoPkgName),
                  param.getNameAsString(),
                  Modifier.PUBLIC)
              .build());
    }

    JavaFile.builder(dtoPkgName, typeBuilder.build()).build().writeTo(outputRoot);
  }

  /**
   * application.yml を出力 サービスが複数の場合は全サービス分の IOR 設定を出力
   *
   * @param outputDir プロジェクト出力先ディレクトリ
   * @param serviceNames サービス名の一覧
   */
  static void writeApplicationProperties(Path outputDir, List<String> serviceNames)
      throws IOException {
    String content =
        "corba:\n"
            + serviceNames.stream()
                .map(n -> "  %s:\n    ior:\n".formatted(n))
                .collect(Collectors.joining());
    Files.writeString(
        outputDir.resolve("src/main/resources/application.yml"),
        content,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }
}
