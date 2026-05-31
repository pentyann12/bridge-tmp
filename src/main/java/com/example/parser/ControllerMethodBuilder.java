package com.example.parser;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * コントローラーメソッド1つ分のコード文字列を構築するユーティリティ
 *
 * <p>out引数（Holder型）の有無で挙動が変わります:
 *
 * <ul>
 *   <li>out引数なし: 戻り値を直接返す通常の POST メソッドを生成
 *   <li>out引数あり: 空の Holder を生成して CORBA を呼び出し、 戻り値と out 引数をまとめた ResponseDto を返すメソッドを生成
 * </ul>
 */
final class ControllerMethodBuilder {
  private ControllerMethodBuilder() {}

  /**
   * コントローラーメソッド1つ分のコード文字列を返す
   *
   * @param method サービスインターフェースのメソッド宣言
   * @param basePackage ベースパッケージ名
   * @param dtoPackage DTO パッケージ名
   * @param servicePackage サービスが属するパッケージ
   * @param returnAsDto 複雑な戻り値型を DTO に変換するかどうか
   * @param stubRoot スタブのルートディレクトリ（Holder value 型解決に使用）
   * @return コントローラーメソッドのソースコード文字列
   */
  static String build(
      MethodDeclaration method,
      String basePackage,
      String dtoPackage,
      String servicePackage,
      boolean returnAsDto,
      Path stubRoot) {

    String methodName = method.getNameAsString();
    String returnType = method.getType().asString();
    boolean hasOutParams =
        method.getParameters().stream()
            .anyMatch(p -> CorbaTypeUtils.isHolderType(p.getType().asString()));
    boolean hasInParams =
        method.getParameters().stream()
            .anyMatch(p -> !CorbaTypeUtils.isHolderType(p.getType().asString()));
    String requestDtoType = dtoPackage + "." + CorbaTypeUtils.capitalize(methodName) + "RequestDto";
    String controllerReturnType =
        resolveControllerReturnType(
            method, basePackage, dtoPackage, servicePackage, returnAsDto, hasOutParams);
    String reqParam = hasInParams ? "@RequestBody " + requestDtoType + " req" : "";

    String signature =
        """
            @PostMapping("/%s")
            public %s %s(%s) {
        """
            .formatted(methodName, controllerReturnType, methodName, reqParam);

    // 引数変換（in引数: DTO→CORBA変換、out引数: 空 Holder を生成）
    List<String> argNames = new ArrayList<>();
    StringBuilder argConversions = new StringBuilder();
    for (int i = 0; i < method.getParameters().size(); i++) {
      Parameter param = method.getParameters().get(i);
      String argName = "arg" + i;
      argNames.add(argName);
      if (CorbaTypeUtils.isHolderType(param.getType().asString())) {
        // out引数: 呼び出し前に空 Holder を生成し、呼び出し後に .value を取り出す
        String holderFqn =
            CorbaTypeUtils.resolveTypeFQN(param.getType().asString(), method, servicePackage);
        argConversions.append(
            "        %s %s = new %s();\n".formatted(holderFqn, argName, holderFqn));
      } else {
        argConversions.append(
            buildInArgConversion(param, argName, "req", basePackage, servicePackage, method));
      }
    }

    String callArgs = String.join(", ", argNames);
    String body;

    if (hasOutParams) {
      // CORBA 呼び出し後に ResponseDto を構築して返す
      String callLine =
          "void".equals(returnType)
              ? "        client.%s(%s);\n".formatted(methodName, callArgs)
              : "        %s callResult = client.%s(%s);\n"
                  .formatted(
                      CorbaTypeUtils.resolveTypeFQN(returnType, method, servicePackage),
                      methodName,
                      callArgs);
      body =
          callLine
              + buildOutParamResponseDto(
                  method,
                  returnType,
                  argNames,
                  dtoPackage,
                  basePackage,
                  servicePackage,
                  returnAsDto,
                  stubRoot);
    } else if ("void".equals(returnType)) {
      body =
          "        client.%s(%s);\n        return java.util.Collections.singletonMap(\"status\", \"ok\");\n"
              .formatted(methodName, callArgs);
    } else {
      body =
          buildDirectReturn(
              method, returnType, callArgs, basePackage, dtoPackage, servicePackage, returnAsDto);
    }

    String closing =
        """
            }

        """;

    return signature + argConversions + body + closing;
  }

  /** out引数を持つメソッドのレスポンス DTO 構築コード文字列を返す */
  private static String buildOutParamResponseDto(
      MethodDeclaration method,
      String returnType,
      List<String> argNames,
      String dtoPackage,
      String basePackage,
      String servicePackage,
      boolean returnAsDto,
      Path stubRoot) {

    String responseDtoType =
        dtoPackage + "." + CorbaTypeUtils.capitalize(method.getNameAsString()) + "ResponseDto";
    StringBuilder sb = new StringBuilder();
    sb.append("        %s res = new %s();\n".formatted(responseDtoType, responseDtoType));

    if (!"void".equals(returnType)) {
      sb.append(
          "        res.returnValue = %s;\n"
              .formatted(
                  CorbaTypeUtils.corbaToDtoExpr(
                      "callResult", returnType, basePackage, dtoPackage, returnAsDto)));
    }

    for (int i = 0; i < method.getParameters().size(); i++) {
      Parameter param = method.getParameters().get(i);
      if (!CorbaTypeUtils.isHolderType(param.getType().asString())) continue;
      String valueType =
          CorbaTypeUtils.resolveHolderValueType(
              param.getType().asString(), method, servicePackage, stubRoot);
      sb.append(
          "        res.%s = %s;\n"
              .formatted(
                  param.getNameAsString(),
                  CorbaTypeUtils.corbaToDtoExpr(
                      argNames.get(i) + ".value",
                      valueType,
                      basePackage,
                      dtoPackage,
                      returnAsDto)));
    }

    sb.append("        return res;\n");
    return sb.toString();
  }

  /** out引数なし・非void メソッドの return 文を返す */
  private static String buildDirectReturn(
      MethodDeclaration method,
      String returnType,
      String callArgs,
      String basePackage,
      String dtoPackage,
      String servicePackage,
      boolean returnAsDto) {

    String methodName = method.getNameAsString();

    if (CorbaTypeUtils.isPrimitiveType(returnType)) {
      return "        return client.%s(%s);\n".formatted(methodName, callArgs);
    } else if ("org.omg.CORBA.Any".equals(returnType)) {
      return returnAsDto
          ? "        return %s.mapper.AnyMapper.toAnyValue(client.%s(%s));\n"
              .formatted(basePackage, methodName, callArgs)
          : "        return client.%s(%s);\n".formatted(methodName, callArgs);
    } else {
      // 独自型または配列: result 変数に受けてから変換する
      return "        %s result = client.%s(%s);\n        return %s;\n"
          .formatted(
              CorbaTypeUtils.resolveTypeFQN(returnType, method, servicePackage),
              methodName,
              callArgs,
              CorbaTypeUtils.corbaToDtoExpr(
                  "result", returnType, basePackage, dtoPackage, returnAsDto));
    }
  }

  /**
   * in引数1つ分の DTO→CORBA 変換コード文字列を返す。Holder型（out引数）はこのメソッドの対象外です。
   *
   * <ul>
   *   <li>プリミティブ/String: {@code req} フィールドをそのまま代入
   *   <li>Any: {@code AnyMapper.toAny()} を経由
   *   <li>配列型: {@code Arrays.stream().map(Mapper::fromDto).toArray()} で変換
   *   <li>独自型: {@code XxxMapper.fromDto()} で変換
   * </ul>
   */
  private static String buildInArgConversion(
      Parameter param,
      String argName,
      String handlerName,
      String basePackage,
      String servicePackage,
      MethodDeclaration method) {

    String paramType = param.getType().asString();
    String paramSimple = CorbaTypeUtils.simpleName(paramType);
    String fieldRef = handlerName + "." + param.getNameAsString();

    if (CorbaTypeUtils.isPrimitiveType(paramType)) {
      return "        %s %s = %s;\n".formatted(paramType, argName, fieldRef);
    } else if ("org.omg.CORBA.Any".equals(paramType)) {
      return "        org.omg.CORBA.Any %s = %s.mapper.AnyMapper.toAny(%s);\n"
          .formatted(argName, basePackage, fieldRef);
    } else if (paramSimple.endsWith("[]")) {
      String elemSimple = paramSimple.substring(0, paramSimple.length() - 2);
      String fqnParam = CorbaTypeUtils.resolveTypeFQN(paramType, method, servicePackage);
      String elemFqn =
          fqnParam.endsWith("[]") ? fqnParam.substring(0, fqnParam.length() - 2) : fqnParam;
      return "        %s %s = %s == null ? null : java.util.Arrays.stream(%s).map(%s.mapper.%sMapper::fromDto).toArray(%s[]::new);\n"
          .formatted(fqnParam, argName, fieldRef, fieldRef, basePackage, elemSimple, elemFqn);
    } else {
      String fqnParam = CorbaTypeUtils.resolveTypeFQN(paramType, method, servicePackage);
      return "        %s %s = %s.mapper.%sMapper.fromDto(%s);\n"
          .formatted(fqnParam, argName, basePackage, paramSimple, fieldRef);
    }
  }

  /** コントローラーメソッドの戻り型文字列を解決 out引数ありなら ResponseDto、なければ CORBA 型または DTO 型を返す */
  private static String resolveControllerReturnType(
      MethodDeclaration method,
      String basePackage,
      String dtoPackage,
      String servicePackage,
      boolean returnAsDto,
      boolean hasOutParams) {
    if (hasOutParams)
      return dtoPackage + "." + CorbaTypeUtils.capitalize(method.getNameAsString()) + "ResponseDto";
    String returnType = method.getType().asString();
    if ("void".equals(returnType)) return "Object";
    if (CorbaTypeUtils.isPrimitiveType(returnType)) return returnType;
    if ("org.omg.CORBA.Any".equals(returnType)) {
      return returnAsDto ? basePackage + ".dto.AnyValue" : "org.omg.CORBA.Any";
    }
    if (!returnAsDto) return CorbaTypeUtils.resolveTypeFQN(returnType, method, servicePackage);
    boolean isArray = returnType.endsWith("[]");
    String elem = isArray ? returnType.substring(0, returnType.length() - 2) : returnType;
    return dtoPackage + "." + CorbaTypeUtils.simpleName(elem) + "Dto" + (isArray ? "[]" : "");
  }
}
