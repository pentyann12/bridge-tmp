package com.toru_otsuki.bridge_generator.gen;

import com.palantir.javapoet.CodeBlock;
import com.toru_otsuki.bridge_generator.model.CorbaType;
import com.toru_otsuki.bridge_generator.model.Operation;
import com.toru_otsuki.bridge_generator.model.Param;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/// コントローラーメソッド1つ分のコード文字列を組み立てるユーティリティ
///
/// リクエストは`XxxRpcRequest`（`params`フィールドに`XxxRequestDto`）で受け取り、in引数をDTOからCORBA型に変換して呼び出す。
/// out引数には空のHolderを、inout引数にはリクエストの値を入れたHolderを渡し、呼び出し後に戻り値と各`Holder.value`を
/// `XxxRpcResult`に詰めて`XxxRpcResponse`で返す。
final class ControllerMethodBuilder {
  private ControllerMethodBuilder() {}

  /// コントローラーメソッド1つ分のコード文字列を返す
  ///
  /// @param op 操作のモデル
  /// @param ctx コード生成コンテキスト
  /// @return `POST /操作名`を受けるメソッドのソースコード
  static String build(Operation op, GenContext ctx) {
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
    // paramsを省略したリクエストは、引数を既定値で補って呼び出さずに呼び出し側の誤りとして返す
    String paramsExtraction =
        op.hasInParams()
            ? badRequestIf("rpc.params == null", "params は必須です")
                + "    %s.%sRequestDto req = rpc.params;\n".formatted(dtoPackage, cap)
            : "";

    // 呼び出し引数を引数順にarg0, arg1, ...として用意する。in引数はDTOから変換し、out引数は空のHolderを、
    // inout引数はDTOから変換した値を入れたHolderを渡す
    TypeMapping types = ctx.types();
    List<String> argNames = new ArrayList<>();
    StringBuilder argConversions = new StringBuilder();
    for (Param param : op.params()) {
      String argName = "arg" + argNames.size();
      argNames.add(argName);
      String javaType = CorbaClientWriter.javaType(param, types);
      String fromDto =
          param.isInput()
              ? types.fromDto(param.type(), CodeBlock.of("req.$L", param.name())).toString()
              : "";
      String init =
          switch (param.direction()) {
            case IN -> fromDto;
            case OUT -> "new " + javaType + "()";
            case INOUT -> "new " + javaType + "(" + fromDto + ")";
          };
      // inout引数の値はCORBAサーバーがそのまま使う入力なので、nullのまま渡せない型は空の値で補わずに止める
      if (param.direction() == Param.Direction.INOUT && isNullable(param.type())) {
        argConversions.append(
            badRequestIf(
                "req.%s == null".formatted(param.name()),
                "params.%s は必須です（inout引数）".formatted(param.name())));
      }
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
      Operation op,
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
      Param param = op.params().get(i);
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

  /// JSONで省略するとnullになり、そのままCORBAに渡せない型か
  ///
  /// プリミティブは省略すると既定値（0やfalse）になり、Anyは空のAnyにできるので含めない。
  private static boolean isNullable(CorbaType type) {
    return type instanceof CorbaType.Str
        || type instanceof CorbaType.Named
        || type instanceof CorbaType.ArrayOf;
  }

  /// 条件を満たしたら、400 Bad Requestで呼び出しを止めるコードを返す
  ///
  /// @param condition 止める条件のJava式
  /// @param message 例外のメッセージ（Javaの文字列リテラルにそのまま埋め込むので、`"`や`\`を含めない）
  /// @return if文のコード
  private static String badRequestIf(String condition, String message) {
    return """
            if (%s) {
              throw new org.springframework.web.server.ResponseStatusException(
                  org.springframework.http.HttpStatus.BAD_REQUEST, "%s");
            }
        """
        .formatted(condition, message);
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
