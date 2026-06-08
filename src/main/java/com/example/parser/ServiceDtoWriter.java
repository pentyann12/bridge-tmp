package com.example.parser;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeName;
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
   * CORBA サービスメソッド用のリクエスト DTO と JSON-RPC ラッパークラスを出力する
   *
   * <p>{@code XxxRequestDto} に加え、JSON-RPC 形式のリクエストボディに対応する {@code XxxRpcRequest}
   * も生成します。{@code XxxRpcRequest} は {@code jsonrpc}・{@code method}・{@code id}・{@code params}
   * フィールドを持ち、{@code params} の型は {@code XxxRequestDto} です。
   *
   * @param method サービスメソッドの宣言
   * @param outputRoot Java ソースのルートディレクトリ
   * @param basePkgName ベースパッケージ名
   */
  static void generateRequestDto(MethodDeclaration method, Path outputRoot, String basePkgName)
      throws IOException {
    String dtoPkgName = basePkgName + ".dto";
    String capitalizedName = CorbaTypeUtils.capitalize(method.getNameAsString());

    // XxxRequestDto: 実際の引数フィールドを持つ DTO
    TypeSpec.Builder typeBuilder =
        TypeSpec.classBuilder(capitalizedName + "RequestDto").addModifiers(Modifier.PUBLIC);
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

    // XxxRpcRequest: JSON-RPC ラッパー（params フィールドで XxxRequestDto を保持）
    ClassName requestDtoClass = ClassName.get(dtoPkgName, capitalizedName + "RequestDto");
    TypeSpec rpcWrapper =
        TypeSpec.classBuilder(capitalizedName + "RpcRequest")
            .addModifiers(Modifier.PUBLIC)
            .addField(FieldSpec.builder(ClassName.get(String.class), "jsonrpc", Modifier.PUBLIC).build())
            .addField(FieldSpec.builder(ClassName.get(String.class), "method", Modifier.PUBLIC).build())
            .addField(FieldSpec.builder(TypeName.INT, "id", Modifier.PUBLIC).build())
            .addField(FieldSpec.builder(requestDtoClass, "params", Modifier.PUBLIC).build())
            .build();
    JavaFile.builder(dtoPkgName, rpcWrapper).build().writeTo(outputRoot);
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
   * JSON-RPC レスポンス用の型付きクラスを出力する
   *
   * <p>以下の2クラスを生成します:
   *
   * <ul>
   *   <li>{@code XxxRpcResult} — {@code @JsonProperty("return")} を付けた {@code returnValue} フィールドを持つ
   *   <li>{@code XxxRpcResponse} — {@code jsonrpc="2.0"}・{@code id=1}・{@code result: XxxRpcResult}
   * </ul>
   *
   * @param method サービスメソッドの宣言
   * @param outputRoot Java ソースのルートディレクトリ
   * @param basePkgName ベースパッケージ名
   * @param returnAsDto true の場合、複雑な型を DTO 型に変換する
   * @param servicePackage サービスが属するパッケージ（FQN 解決のフォールバック用）
   * @param stubRoot Holder の value 型解決でスタブファイルを参照するために使用
   */
  static void generateRpcResponse(
      MethodDeclaration method,
      Path outputRoot,
      String basePkgName,
      boolean returnAsDto,
      String servicePackage,
      Path stubRoot)
      throws IOException {
    String dtoPkgName = basePkgName + ".dto";
    String capitalizedName = CorbaTypeUtils.capitalize(method.getNameAsString());
    boolean hasOutParams =
        method.getParameters().stream()
            .anyMatch(p -> CorbaTypeUtils.isHolderType(p.getType().asString()));

    String returnTypeName =
        CorbaTypeUtils.resolveRpcReturnType(
            method, basePkgName, dtoPkgName, servicePackage, returnAsDto, hasOutParams);

    // XxxRpcResult: result.return の型を @JsonProperty("return") で定義
    AnnotationSpec jsonProperty =
        AnnotationSpec.builder(
                ClassName.get("com.fasterxml.jackson.annotation", "JsonProperty"))
            .addMember("value", "$S", "return")
            .build();
    TypeSpec rpcResult =
        TypeSpec.classBuilder(capitalizedName + "RpcResult")
            .addModifiers(Modifier.PUBLIC)
            .addField(
                FieldSpec.builder(
                        CorbaTypeUtils.convToTypeName(returnTypeName, dtoPkgName),
                        "returnValue",
                        Modifier.PUBLIC)
                    .addAnnotation(jsonProperty)
                    .build())
            .build();
    JavaFile.builder(dtoPkgName, rpcResult).build().writeTo(outputRoot);

    // XxxRpcResponse: JSON-RPC ラッパー
    ClassName rpcResultClass = ClassName.get(dtoPkgName, capitalizedName + "RpcResult");
    TypeSpec rpcResponse =
        TypeSpec.classBuilder(capitalizedName + "RpcResponse")
            .addModifiers(Modifier.PUBLIC)
            .addField(
                FieldSpec.builder(ClassName.get(String.class), "jsonrpc", Modifier.PUBLIC)
                    .initializer("$S", "2.0")
                    .build())
            .addField(
                FieldSpec.builder(TypeName.INT, "id", Modifier.PUBLIC)
                    .initializer("1")
                    .build())
            .addField(FieldSpec.builder(rpcResultClass, "result", Modifier.PUBLIC).build())
            .build();
    JavaFile.builder(dtoPkgName, rpcResponse).build().writeTo(outputRoot);
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
