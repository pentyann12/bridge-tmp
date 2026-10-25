package com.toru_otsuki.bridge_generator.gen;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import com.toru_otsuki.bridge_generator.model.Operation;
import com.toru_otsuki.bridge_generator.model.Param;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.element.Modifier;

/// サービスの操作ごとのDTO群とapplication.ymlを出力するユーティリティ
///
/// 1つの操作に対して次の4クラスを生成する。
///
/// - `XxxRequestDto` — in引数をフィールドに持つリクエストDTO
/// - `XxxRpcRequest` — JSON-RPCリクエストのラッパー（`params: XxxRequestDto`）
/// - `XxxRpcResult` — JSON-RPCレスポンスの`result`（`@JsonProperty("return") returnValue`とout引数）
/// - `XxxRpcResponse` — JSON-RPCレスポンスのラッパー（`result: XxxRpcResult`）
final class ServiceDtoWriter {
  private ServiceDtoWriter() {}

  /// リクエストDTO（`XxxRequestDto`）とJSON-RPCラッパー（`XxxRpcRequest`）を出力する。out引数はリクエストボディに含めない。
  ///
  /// @param op 操作のモデル
  /// @param ctx コード生成コンテキスト
  /// @throws IOException ファイルの書き込みに失敗した場合
  static void generateRequestDto(Operation op, GenContext ctx) throws IOException {
    TypeMapping types = ctx.types();
    String name = op.capitalizedName();

    TypeSpec.Builder requestDto =
        TypeSpec.classBuilder(name + "RequestDto").addModifiers(Modifier.PUBLIC);
    for (Param param : op.params()) {
      if (param.isOut()) continue;
      requestDto.addField(publicField(types.dtoType(param.type()), param.name()));
    }
    write(requestDto.build(), ctx);

    TypeSpec rpcRequest =
        TypeSpec.classBuilder(name + "RpcRequest")
            .addModifiers(Modifier.PUBLIC)
            .addField(publicField(TypeMapping.STRING, "jsonrpc"))
            .addField(publicField(TypeMapping.STRING, "method"))
            .addField(publicField(TypeName.INT, "id"))
            .addField(publicField(types.dtoClass(name + "RequestDto"), "params"))
            .build();
    write(rpcRequest, ctx);
  }

  /// JSON-RPCレスポンス用の`XxxRpcResult`と`XxxRpcResponse`を出力する
  ///
  /// `XxxRpcResult`は戻り値を`@JsonProperty("return")`付きの`returnValue`に持ち、out引数を引数名のフィールドとしてその隣に並べる（`result.return`,
  /// `result.引数名`, ...）。voidの戻り値は`Object`型で常にnullになる。
  ///
  /// @param op 操作のモデル
  /// @param ctx コード生成コンテキスト
  /// @throws IOException ファイルの書き込みに失敗した場合
  static void generateRpcResponse(Operation op, GenContext ctx) throws IOException {
    TypeMapping types = ctx.types();
    String name = op.capitalizedName();

    AnnotationSpec jsonReturn =
        AnnotationSpec.builder(ClassName.get("com.fasterxml.jackson.annotation", "JsonProperty"))
            .addMember("value", "$S", "return")
            .build();
    TypeName returnType =
        op.returnType()
            .map(t -> types.resultType(t, ctx.returnAsDto()))
            .orElse(ClassName.get(Object.class));
    TypeSpec.Builder rpcResult =
        TypeSpec.classBuilder(name + "RpcResult")
            .addModifiers(Modifier.PUBLIC)
            .addField(
                FieldSpec.builder(returnType, "returnValue", Modifier.PUBLIC)
                    .addAnnotation(jsonReturn)
                    .build());
    for (Param param : op.params()) {
      if (!param.isOut()) continue;
      rpcResult.addField(
          publicField(types.resultType(param.type(), ctx.returnAsDto()), param.name()));
    }
    write(rpcResult.build(), ctx);

    TypeSpec rpcResponse =
        TypeSpec.classBuilder(name + "RpcResponse")
            .addModifiers(Modifier.PUBLIC)
            .addField(
                FieldSpec.builder(TypeMapping.STRING, "jsonrpc", Modifier.PUBLIC)
                    .initializer("$S", "2.0")
                    .build())
            .addField(
                FieldSpec.builder(TypeName.INT, "id", Modifier.PUBLIC).initializer("1").build())
            .addField(publicField(types.dtoClass(name + "RpcResult"), "result"))
            .build();
    write(rpcResponse, ctx);
  }

  /// application.ymlを出力する
  ///
  /// 文字列の文字コード（`corba.string-charset`）には生成時の指定を書き、サービスごとに`corba.サービス名.ior`の設定欄を空で設ける。
  /// IDLの識別子に`-`は使えないので、サービス名と`string-charset`がぶつかることはない。
  ///
  /// @param ctx コード生成コンテキスト
  /// @param serviceNames サービス名の一覧
  /// @throws IOException ファイルの書き込みに失敗した場合
  static void writeApplicationProperties(GenContext ctx, List<String> serviceNames)
      throws IOException {
    String charset =
        """
          # CORBAサーバーが文字列に使う文字コード（noneなら読み替えない）
          string-charset: %s
        """
            .formatted(ctx.settings().stringCharset());
    String services =
        serviceNames.stream()
            .map(
                n ->
                    """
                      %s:
                        ior:
                    """
                        .formatted(n))
            .collect(Collectors.joining());
    Files.writeString(
        ctx.resourcesDir().resolve("application.yml"), "corba:\n" + charset + services);
  }

  private static FieldSpec publicField(TypeName type, String name) {
    return FieldSpec.builder(type, name, Modifier.PUBLIC).build();
  }

  private static void write(TypeSpec type, GenContext ctx) throws IOException {
    JavaFile.builder(ctx.dtoPackage(), type).build().writeTo(ctx.javaDir());
  }
}
