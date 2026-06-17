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
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.element.Modifier;

/**
 * サービスメソッドごとのDTO群とapplication.ymlを出力するユーティリティ
 *
 * 1つのサービスメソッドに対して以下の4クラスを生成します:
 *
 * - `XxxRequestDto` — in引数をフィールドに持つリクエストDTO
 * - `XxxRpcRequest` — JSON-RPCリクエストのラッパー（`params: XxxRequestDto`）
 * - `XxxRpcResult` — JSON-RPCレスポンスの`result`オブジェクト（`@JsonProperty("return") returnValue`フィールド＋out引数フィールド）
 * - `XxxRpcResponse` — JSON-RPCレスポンスのラッパー（`result: XxxRpcResult`）
 */
final class ServiceDtoWriter {
  private ServiceDtoWriter() {}

  /**
   * CORBAサービスメソッド用のリクエストDTOとJSON-RPCラッパークラスを出力する
   *
   * <p>{@code XxxRequestDto}に加え、JSON-RPC形式のリクエストボディに対応する{@code XxxRpcRequest} も生成します。{@code
   * XxxRpcRequest}は{@code jsonrpc}・{@code method}・{@code id}・{@code params} フィールドを持ち、{@code
   * params}の型は{@code XxxRequestDto}です。
   *
   * @param method サービスメソッドの宣言
   * @param ctx コード生成コンテキスト
   */
  static void generateRequestDto(MethodDeclaration method, GenContext ctx) throws IOException {
    String dtoPkgName = ctx.dtoPackage();
    String capitalizedName = CorbaTypeUtils.capitalize(method.getNameAsString());

    // XxxRequestDto: 実際の引数フィールドを持つDTO
    TypeSpec.Builder typeBuilder =
        TypeSpec.classBuilder(capitalizedName + "RequestDto").addModifiers(Modifier.PUBLIC);
    for (Parameter param : method.getParameters()) {
      if (CorbaTypeUtils.isHolderType(param.getType().asString())) {
        // out引数はリクエストボディから除外する
        continue;
      }
      String fieldType = CorbaTypeUtils.mapToDtoType(ctx.basePackage(), param.getType().asString());
      typeBuilder.addField(
          FieldSpec.builder(
                  CorbaTypeUtils.convToTypeName(fieldType, dtoPkgName),
                  param.getNameAsString(),
                  Modifier.PUBLIC)
              .build());
    }
    JavaFile.builder(dtoPkgName, typeBuilder.build()).build().writeTo(ctx.javaDir());

    // XxxRpcRequest: JSON-RPCラッパー（paramsフィールドでXxxRequestDtoを保持）
    ClassName requestDtoClass = ClassName.get(dtoPkgName, capitalizedName + "RequestDto");
    TypeSpec rpcWrapper =
        TypeSpec.classBuilder(capitalizedName + "RpcRequest")
            .addModifiers(Modifier.PUBLIC)
            .addField(
                FieldSpec.builder(ClassName.get(String.class), "jsonrpc", Modifier.PUBLIC).build())
            .addField(
                FieldSpec.builder(ClassName.get(String.class), "method", Modifier.PUBLIC).build())
            .addField(FieldSpec.builder(TypeName.INT, "id", Modifier.PUBLIC).build())
            .addField(FieldSpec.builder(requestDtoClass, "params", Modifier.PUBLIC).build())
            .build();
    JavaFile.builder(dtoPkgName, rpcWrapper).build().writeTo(ctx.javaDir());
  }

  /**
   * JSON-RPCレスポンス用の型付きクラスを出力する
   *
   * <p>以下の2クラスを生成します:
   *
   * <ul>
   *   - {@code XxxRpcResult}—{@code @JsonProperty("return")}を付けた{@code returnValue}フィールドを持つ -
   *   {@code XxxRpcResponse}—{@code jsonrpc="2.0"}・{@code id=1}・{@code result: XxxRpcResult}
   * </ul>
   *
   * @param method サービスメソッドの宣言
   * @param ctx コード生成コンテキスト
   */
  static void generateRpcResponse(MethodDeclaration method, GenContext ctx) throws IOException {
    String dtoPkgName = ctx.dtoPackage();
    String capitalizedName = CorbaTypeUtils.capitalize(method.getNameAsString());

    String returnTypeName = CorbaTypeUtils.resolveRpcReturnType(method, ctx);

    // XxxRpcResult: result.returnの型を@JsonProperty("return")で定義
    AnnotationSpec jsonProperty =
        AnnotationSpec.builder(ClassName.get("com.fasterxml.jackson.annotation", "JsonProperty"))
            .addMember("value", "$S", "return")
            .build();
    TypeSpec.Builder rpcResultBuilder =
        TypeSpec.classBuilder(capitalizedName + "RpcResult")
            .addModifiers(Modifier.PUBLIC)
            .addField(
                FieldSpec.builder(
                        CorbaTypeUtils.convToTypeName(returnTypeName, dtoPkgName),
                        "returnValue",
                        Modifier.PUBLIC)
                    .addAnnotation(jsonProperty)
                    .build());

    // out引数をresult直下のフィールドとして追加（result.hoge, result.fugaのように並列配置）
    for (Parameter param : method.getParameters()) {
      String paramType = param.getType().asString();
      if (!CorbaTypeUtils.isHolderType(paramType)) continue;
      String valueType = CorbaTypeUtils.resolveHolderValueType(paramType, method, ctx);
      String fieldType =
          ctx.returnAsDto()
              ? CorbaTypeUtils.mapToDtoType(ctx.basePackage(), valueType)
              : (valueType.contains(".")
                  ? valueType
                  : CorbaTypeUtils.resolveTypeFQN(valueType, method, ctx.servicePackage()));
      rpcResultBuilder.addField(
          FieldSpec.builder(
                  CorbaTypeUtils.convToTypeName(fieldType, dtoPkgName),
                  param.getNameAsString(),
                  Modifier.PUBLIC)
              .build());
    }

    JavaFile.builder(dtoPkgName, rpcResultBuilder.build()).build().writeTo(ctx.javaDir());

    // XxxRpcResponse: JSON-RPCラッパー
    ClassName rpcResultClass = ClassName.get(dtoPkgName, capitalizedName + "RpcResult");
    TypeSpec rpcResponse =
        TypeSpec.classBuilder(capitalizedName + "RpcResponse")
            .addModifiers(Modifier.PUBLIC)
            .addField(
                FieldSpec.builder(ClassName.get(String.class), "jsonrpc", Modifier.PUBLIC)
                    .initializer("$S", "2.0")
                    .build())
            .addField(
                FieldSpec.builder(TypeName.INT, "id", Modifier.PUBLIC).initializer("1").build())
            .addField(FieldSpec.builder(rpcResultClass, "result", Modifier.PUBLIC).build())
            .build();
    JavaFile.builder(dtoPkgName, rpcResponse).build().writeTo(ctx.javaDir());
  }

  /**
   * application.ymlを出力 サービスが複数の場合は全サービス分のIOR設定を出力
   *
   * @param ctx コード生成コンテキスト
   * @param serviceNames サービス名の一覧
   */
  static void writeApplicationProperties(GenContext ctx, List<String> serviceNames)
      throws IOException {
    String content =
        "corba:\n"
            + serviceNames.stream()
                .map(n -> "  %s:\n    ior:\n".formatted(n))
                .collect(Collectors.joining());
    Files.writeString(
        ctx.resourcesDir().resolve("application.yml"),
        content,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }
}
