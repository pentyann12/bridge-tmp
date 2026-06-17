package com.example.parser;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import java.util.ArrayList;
import java.util.List;

/**
 * コントローラーメソッド1つ分のコード文字列を構築するユーティリティ
 *
 * <p>out引数（Holder型）の有無で挙動が変わります:
 *
 * <ul>
 *   - out引数なし: 戻り値を直接返す通常のPOSTメソッドを生成 - out引数あり: 空のHolderを生成してCORBAを呼び出し、
 *   戻り値とout引数をまとめたResponseDtoを返すメソッドを生成
 * </ul>
 *
 * リクエストは{@code XxxRpcRequest}（{@code params}フィールドに{@code XxxRequestDto}）で受け取り、 レスポンスは{@code
 * XxxRpcResponse}（{@code result.return}に実際の値）で返します。
 */
final class ControllerMethodBuilder {
  private ControllerMethodBuilder() {}

  /**
   * コントローラーメソッド1つ分のコード文字列を返す
   *
   * @param method サービスインターフェースのメソッド宣言
   * @param ctx コード生成コンテキスト
   * @return コントローラーメソッドのソースコード文字列
   */
  static String build(MethodDeclaration method, GenContext ctx) {

    String methodName = method.getNameAsString();
    String returnType = method.getType().asString();
    boolean hasOutParams =
        method.getParameters().stream()
            .anyMatch(p -> CorbaTypeUtils.isHolderType(p.getType().asString()));
    boolean hasInParams =
        method.getParameters().stream()
            .anyMatch(p -> !CorbaTypeUtils.isHolderType(p.getType().asString()));
    String cap = CorbaTypeUtils.capitalize(methodName);
    String requestDtoFqn = ctx.dtoPackage() + "." + cap + "RequestDto";
    String rpcRequestFqn = ctx.dtoPackage() + "." + cap + "RpcRequest";
    String rpcResponseFqn = ctx.dtoPackage() + "." + cap + "RpcResponse";
    String rpcResultFqn = ctx.dtoPackage() + "." + cap + "RpcResult";

    // JSON-RPC形式で受け取る: XxxRpcRequest.paramsがXxxRequestDto
    String reqParam = hasInParams ? "@RequestBody " + rpcRequestFqn + " rpc" : "";
    String paramsExtraction =
        hasInParams ? "        %s req = rpc.params;\n".formatted(requestDtoFqn) : "";

    String signature =
        """
            @PostMapping("/%s")
            public %s %s(%s) {
        """
            .formatted(methodName, rpcResponseFqn, methodName, reqParam);

    // 引数変換（in引数: DTO→CORBA変換、out引数: 空Holderを生成）
    List<String> argNames = new ArrayList<>();
    StringBuilder argConversions = new StringBuilder();
    for (int i = 0; i < method.getParameters().size(); i++) {
      Parameter param = method.getParameters().get(i);
      String argName = "arg" + i;
      argNames.add(argName);
      if (CorbaTypeUtils.isHolderType(param.getType().asString())) {
        // out引数: 呼び出し前に空Holderを生成し、呼び出し後に .valueを取り出す
        String holderFqn =
            CorbaTypeUtils.resolveTypeFQN(param.getType().asString(), method, ctx.servicePackage());
        argConversions.append(
            "        %s %s = new %s();\n".formatted(holderFqn, argName, holderFqn));
      } else {
        argConversions.append(buildInArgConversion(param, argName, "req", method, ctx));
      }
    }

    String callArgs = String.join(", ", argNames);
    String body;

    if (hasOutParams) {
      // CORBA呼び出し後にXxxRpcResultへ直接代入してXxxRpcResponseで返す
      String callLine =
          "void".equals(returnType)
              ? "        client.%s(%s);\n".formatted(methodName, callArgs)
              : "        %s callResult = client.%s(%s);\n"
                  .formatted(
                      CorbaTypeUtils.resolveTypeFQN(returnType, method, ctx.servicePackage()),
                      methodName,
                      callArgs);
      body = callLine + buildOutParamResponseDto(method, returnType, argNames, rpcResponseFqn, rpcResultFqn, ctx);
    } else if ("void".equals(returnType)) {
      body =
          "        client.%s(%s);\n".formatted(methodName, callArgs)
              + buildRpcReturn("null", rpcResponseFqn, rpcResultFqn);
    } else {
      body = buildDirectReturn(method, returnType, callArgs, rpcResponseFqn, rpcResultFqn, ctx);
    }

    String closing =
        """
            }

        """;

    return signature + paramsExtraction + argConversions + body + closing;
  }

  /**
   * out引数を持つメソッドのレスポンス構築コード文字列を返す。CORBA呼び出し後の`callResult`と各`Holder.value`を
   * `XxxRpcResult`のフィールドに直接代入し、`XxxRpcResponse`でラップして返す。
   *
   * @param method サービスインターフェースのメソッド宣言
   * @param returnType メソッドの戻り値型名
   * @param argNames CORBA呼び出し引数変数名の一覧（out引数はHolder変数名）
   * @param rpcResponseFqn `XxxRpcResponse`の完全修飾クラス名
   * @param rpcResultFqn `XxxRpcResult`の完全修飾クラス名
   * @param ctx コード生成コンテキスト
   * @return `XxxRpcResult`への代入とreturn文を含むコード文字列
   */
  private static String buildOutParamResponseDto(
      MethodDeclaration method,
      String returnType,
      List<String> argNames,
      String rpcResponseFqn,
      String rpcResultFqn,
      GenContext ctx) {

    StringBuilder sb = new StringBuilder();
    sb.append("        %s __result = new %s();\n".formatted(rpcResultFqn, rpcResultFqn));

    if (!"void".equals(returnType)) {
      sb.append(
          "        __result.returnValue = %s;\n"
              .formatted(CorbaTypeUtils.corbaToDtoExpr("callResult", returnType, ctx)));
    }

    for (int i = 0; i < method.getParameters().size(); i++) {
      Parameter param = method.getParameters().get(i);
      if (!CorbaTypeUtils.isHolderType(param.getType().asString())) continue;
      String valueType =
          CorbaTypeUtils.resolveHolderValueType(param.getType().asString(), method, ctx);
      sb.append(
          "        __result.%s = %s;\n"
              .formatted(
                  param.getNameAsString(),
                  CorbaTypeUtils.corbaToDtoExpr(argNames.get(i) + ".value", valueType, ctx)));
    }

    sb.append("        %s __response = new %s();\n".formatted(rpcResponseFqn, rpcResponseFqn));
    sb.append("        __response.result = __result;\n");
    sb.append("        return __response;\n");
    return sb.toString();
  }

  /**
   * out引数なし・非voidメソッドのreturn文を`XxxRpcResponse`でラップして返す
   *
   * @param method サービスインターフェースのメソッド宣言
   * @param returnType メソッドの戻り値型名
   * @param callArgs CORBA呼び出しの引数リスト文字列
   * @param rpcResponseFqn `XxxRpcResponse`の完全修飾クラス名
   * @param rpcResultFqn `XxxRpcResult`の完全修飾クラス名
   * @param ctx コード生成コンテキスト
   * @return CORBA呼び出しと`XxxRpcResponse`でラップしたreturn文のコード文字列
   */
  private static String buildDirectReturn(
      MethodDeclaration method,
      String returnType,
      String callArgs,
      String rpcResponseFqn,
      String rpcResultFqn,
      GenContext ctx) {

    String methodName = method.getNameAsString();

    if (CorbaTypeUtils.isPrimitiveType(returnType)) {
      // StringはCORBAから取得時に文字コード変換が必要
      String valueExpr =
          "String".equals(returnType)
              ? "%s.mapper.StringMapper.encode(client.%s(%s))"
                  .formatted(ctx.basePackage(), methodName, callArgs)
              : "client.%s(%s)".formatted(methodName, callArgs);
      return buildRpcReturn(valueExpr, rpcResponseFqn, rpcResultFqn);
    } else if ("org.omg.CORBA.Any".equals(returnType)) {
      String valueExpr =
          ctx.returnAsDto()
              ? "%s.mapper.AnyMapper.toAnyValue(client.%s(%s))"
                  .formatted(ctx.basePackage(), methodName, callArgs)
              : "client.%s(%s)".formatted(methodName, callArgs);
      return buildRpcReturn(valueExpr, rpcResponseFqn, rpcResultFqn);
    } else {
      // 独自型または配列: result変数に受けてから変換する
      return "        %s result = client.%s(%s);\n"
              .formatted(
                  CorbaTypeUtils.resolveTypeFQN(returnType, method, ctx.servicePackage()),
                  methodName,
                  callArgs)
          + buildRpcReturn(
              CorbaTypeUtils.corbaToDtoExpr("result", returnType, ctx),
              rpcResponseFqn,
              rpcResultFqn);
    }
  }

  /**
   * in引数1つ分のDTO→CORBA変換コード文字列を返す。Holder型（out引数）はこのメソッドの対象外です。
   *
   * - プリミティブ/String: `req`フィールドをそのまま代入
   * - Any: `AnyMapper.toAny()`を経由
   * - 配列型: `Arrays.stream().map(Mapper::fromDto).toArray()`で変換
   * - 独自型: `XxxMapper.fromDto()`で変換
   *
   * @param param 対象のメソッドパラメーター宣言
   * @param argName 生成するローカル変数名
   * @param handlerName リクエストオブジェクトの変数名（例: "req"）
   * @param method 型が参照されているメソッド宣言（FQN解決に使用）
   * @param ctx コード生成コンテキスト
   * @return DTO→CORBA変換の代入文コード文字列
   */
  private static String buildInArgConversion(
      Parameter param,
      String argName,
      String handlerName,
      MethodDeclaration method,
      GenContext ctx) {

    String paramType = param.getType().asString();
    String paramSimple = CorbaTypeUtils.simpleName(paramType);
    String fieldRef = handlerName + "." + param.getNameAsString();

    if (CorbaTypeUtils.isPrimitiveType(paramType)) {
      return "        %s %s = %s;\n".formatted(paramType, argName, fieldRef);
    } else if ("org.omg.CORBA.Any".equals(paramType)) {
      return "        org.omg.CORBA.Any %s = %s.mapper.AnyMapper.toAny(%s);\n"
          .formatted(argName, ctx.basePackage(), fieldRef);
    } else if (paramSimple.endsWith("[]")) {
      String elemSimple = paramSimple.substring(0, paramSimple.length() - 2);
      String fqnParam = CorbaTypeUtils.resolveTypeFQN(paramType, method, ctx.servicePackage());
      String elemFqn =
          fqnParam.endsWith("[]") ? fqnParam.substring(0, fqnParam.length() - 2) : fqnParam;
      return "        %s %s = %s == null ? null : java.util.Arrays.stream(%s).map(%s.mapper.%sMapper::fromDto).toArray(%s[]::new);\n"
          .formatted(fqnParam, argName, fieldRef, fieldRef, ctx.basePackage(), elemSimple, elemFqn);
    } else {
      String fqnParam = CorbaTypeUtils.resolveTypeFQN(paramType, method, ctx.servicePackage());
      return "        %s %s = %s.mapper.%sMapper.fromDto(%s);\n"
          .formatted(fqnParam, argName, ctx.basePackage(), paramSimple, fieldRef);
    }
  }

  /**
   * 型付き`XxxRpcResponse`を構築してreturnするコード文字列を生成する
   *
   * @param valueExpr `result.return`に設定する式
   * @param rpcResponseFqn `XxxRpcResponse`の完全修飾クラス名
   * @param rpcResultFqn `XxxRpcResult`の完全修飾クラス名
   * @return `XxxRpcResponse`を構築してreturnするコード文字列
   */
  private static String buildRpcReturn(String valueExpr, String rpcResponseFqn, String rpcResultFqn) {
    return """
            %s __result = new %s();
            __result.returnValue = %s;
            %s __response = new %s();
            __response.result = __result;
            return __response;
    """
        .formatted(rpcResultFqn, rpcResultFqn, valueExpr, rpcResponseFqn, rpcResponseFqn);
  }
}
