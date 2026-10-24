package com.toru_otsuki.bridge_generator.gen;

import com.palantir.javapoet.CodeBlock;
import com.toru_otsuki.bridge_generator.stub.CorbaType;
import com.toru_otsuki.bridge_generator.stub.StubModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/// コントローラーメソッド1つ分のコード文字列を組み立てるユーティリティ
///
/// リクエストは`XxxRpcRequest`（`params`フィールドに`XxxRequestDto`）で受け取り、in引数をDTOからCORBA型に変換して呼び出す。
/// out引数には空のHolderを渡し、呼び出し後に戻り値と各`Holder.value`を`XxxRpcResult`に詰めて`XxxRpcResponse`で返す。
final class ControllerMethodBuilder {
  private ControllerMethodBuilder() {}

  /// コントローラーメソッド1つ分のコード文字列を返す
  ///
  /// @param op 操作のモデル
  /// @param ctx コード生成コンテキスト
  /// @return `POST /操作名`を受けるメソッドのソースコード
  static String build(StubModel.Operation op, GenContext ctx) {
    String dtoPackage = ctx.dtoPackage();
    String cap = op.capitalizedName();
    String rpcRequest = dtoPackage + "." + cap + "RpcRequest";
    String rpcResponse = dtoPackage + "." + cap + "RpcResponse";
    String rpcResult = dtoPackage + "." + cap + "RpcResult";

    String signature =
        """
          @PostMapping("/%s")
          public %s %s(%s) {
        """
            .formatted(
                op.name(),
                rpcResponse,
                op.name(),
                op.hasInParams() ? "@RequestBody " + rpcRequest + " rpc" : "");
    String paramsExtraction =
        op.hasInParams()
            ? "    %s.%sRequestDto req = rpc.params;\n".formatted(dtoPackage, cap)
            : "";

    // 呼び出し引数を引数順にarg0, arg1, ...として用意する。in引数はDTOから変換し、out引数は空のHolderを渡す
    TypeMapping types = ctx.types();
    List<String> argNames = new ArrayList<>();
    StringBuilder argConversions = new StringBuilder();
    for (StubModel.Param param : op.params()) {
      String argName = "arg" + argNames.size();
      argNames.add(argName);
      String javaType = CorbaClientWriter.javaType(param, types);
      String init =
          param.isOut()
              ? "new " + javaType + "()"
              : types.fromDto(param.type(), CodeBlock.of("req.$L", param.name())).toString();
      argConversions.append("    %s %s = %s;\n".formatted(javaType, argName, init));
    }

    String call = "client.%s(%s)".formatted(op.name(), String.join(", ", argNames));
    String body =
        op.hasOutParams()
            ? outParamResponse(op, call, argNames, rpcResult, rpcResponse, ctx)
            : directResponse(op.returnType(), call, rpcResult, rpcResponse, ctx);

    return signature
        + paramsExtraction
        + argConversions
        + body
        + """
          }

        """;
  }

  ///
  /// out引数を持つ操作の、呼び出しとレスポンス構築のコードを返す。呼び出し結果を`callResult`に受け、戻り値と各`Holder.value`を変換して`XxxRpcResult`に詰める。
  ///
  /// @param op 操作のモデル
  /// @param call CorbaClientの呼び出し式
  /// @param argNames 呼び出し引数の変数名（引数順）
  /// @param rpcResult `XxxRpcResult`の完全修飾名
  /// @param rpcResponse `XxxRpcResponse`の完全修飾名
  /// @param ctx コード生成コンテキスト
  /// @return メソッド本体のうち、呼び出し以降のコード
  private static String outParamResponse(
      StubModel.Operation op,
      String call,
      List<String> argNames,
      String rpcResult,
      String rpcResponse,
      GenContext ctx) {
    TypeMapping types = ctx.types();
    List<String> assignments = new ArrayList<>();
    op.returnType()
        .ifPresent(
            t ->
                assignments.add(
                    "__result.returnValue = " + toDto(t, CodeBlock.of("callResult"), ctx)));
    for (int i = 0; i < op.params().size(); i++) {
      StubModel.Param param = op.params().get(i);
      if (!param.isOut()) continue;
      assignments.add(
          "__result.%s = %s"
              .formatted(
                  param.name(),
                  toDto(param.type(), CodeBlock.of("$L.value", argNames.get(i)), ctx)));
    }

    String callLine =
        op.returnType()
            .map(t -> "%s callResult = %s".formatted(types.corbaType(t), call))
            .orElse(call);
    return """
            %1$s;
            %2$s __result = new %2$s();
        %3$s    %4$s __response = new %4$s();
            __response.result = __result;
            return __response;
        """
        .formatted(
            callLine,
            rpcResult,
            assignments.stream().map(a -> "    " + a + ";\n").collect(Collectors.joining()),
            rpcResponse);
  }

  /// out引数のない操作の、呼び出しとレスポンス構築のコードを返す
  ///
  /// プリミティブ・String・Any・プリミティブの配列は呼び出しを変換式に直接埋め込み、それ以外（構造体やその配列、要素ごとの変換が要る配列）は一度`result`変数に受けてから変換する。
  /// 配列の変換式は元の式を複数回評価するため、呼び出しを埋め込んではいけない。
  ///
  /// @param returnType 戻り値の型。voidなら空
  /// @param call CorbaClientの呼び出し式
  /// @param rpcResult `XxxRpcResult`の完全修飾名
  /// @param rpcResponse `XxxRpcResponse`の完全修飾名
  /// @param ctx コード生成コンテキスト
  /// @return メソッド本体のうち、呼び出し以降のコード
  private static String directResponse(
      Optional<CorbaType> returnType,
      String call,
      String rpcResult,
      String rpcResponse,
      GenContext ctx) {
    if (returnType.isEmpty()) {
      return "    " + call + ";\n" + rpcReturn("null", rpcResult, rpcResponse);
    }
    CorbaType type = returnType.get();
    boolean inline =
        type instanceof CorbaType.Str
            || type instanceof CorbaType.AnyType
            || isPrimitiveOrPrimitiveArray(type);
    if (inline) {
      return rpcReturn(toDto(type, CodeBlock.of("$L", call), ctx), rpcResult, rpcResponse);
    }
    return "    %s result = %s;\n".formatted(ctx.types().corbaType(type), call)
        + rpcReturn(toDto(type, CodeBlock.of("result"), ctx), rpcResult, rpcResponse);
  }

  private static boolean isPrimitiveOrPrimitiveArray(CorbaType type) {
    return type instanceof CorbaType.Primitive
        || (type instanceof CorbaType.ArrayOf a && isPrimitiveOrPrimitiveArray(a.element()));
  }

  private static String toDto(CorbaType type, CodeBlock rhs, GenContext ctx) {
    return ctx.types().toDto(type, rhs, ctx.returnAsDto()).toString();
  }

  /// `XxxRpcResult.returnValue`に値を入れ、`XxxRpcResponse`で包んで返すコードを返す
  ///
  /// @param valueExpr `returnValue`に入れる式
  /// @param rpcResult `XxxRpcResult`の完全修飾名
  /// @param rpcResponse `XxxRpcResponse`の完全修飾名
  /// @return レスポンスを組み立てて返すコード
  private static String rpcReturn(String valueExpr, String rpcResult, String rpcResponse) {
    return """
            %1$s __result = new %1$s();
            __result.returnValue = %2$s;
            %3$s __response = new %3$s();
            __response.result = __result;
            return __response;
        """
        .formatted(rpcResult, valueExpr, rpcResponse);
  }
}
