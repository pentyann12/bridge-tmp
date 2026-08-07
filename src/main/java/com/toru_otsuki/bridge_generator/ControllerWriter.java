package com.toru_otsuki.bridge_generator;

import com.github.javaparser.ast.body.MethodDeclaration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;

/** Spring MVCコントローラークラスを生成するユーティリティ */
final class ControllerWriter {
  private ControllerWriter() {}

  /**
   * コントローラークラスを生成してファイルに書き出す メソッドごとに`ControllerMethodBuilder#build`を呼び出してコードを組み立てます
   *
   * @param serviceName サービス名
   * @param methods サービスインターフェースのメソッド一覧
   * @param clientClassName CorbaClient クラスの名前
   * @param ctx コード生成コンテキスト
   */
  static void write(
      String serviceName, List<MethodDeclaration> methods, String clientClassName, GenContext ctx)
      throws IOException {

    String controllerName = serviceName + "Controller";
    String clientFqn = ctx.basePackage() + ".corba." + clientClassName;

    String methodsCode =
        methods.stream()
            .map(method -> ControllerMethodBuilder.build(method, ctx))
            .collect(Collectors.joining());

    String fieldAndCtor =
        """
            private final %s client;

            public %s(%s client) {
                this.client = client;
            }
        """
            .formatted(clientFqn, controllerName, clientFqn);

    String controller =
        """
        package %s.controller;

        import org.springframework.web.bind.annotation.RestController;
        import org.springframework.web.bind.annotation.RequestMapping;
        import org.springframework.web.bind.annotation.PostMapping;
        import org.springframework.web.bind.annotation.RequestBody;

        @RestController
        @RequestMapping("/")
        public class %s {
        %s
        %s
        }
        """
            .formatted(ctx.basePackage(), controllerName, fieldAndCtor, methodsCode);

    Files.writeString(
        ctx.controllerDir().resolve(controllerName + ".java"),
        controller,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }
}
