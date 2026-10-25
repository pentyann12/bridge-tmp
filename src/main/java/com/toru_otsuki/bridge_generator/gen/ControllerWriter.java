package com.toru_otsuki.bridge_generator.gen;

import com.toru_otsuki.bridge_generator.model.Service;
import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Collectors;

/// Spring MVCのコントローラークラスを生成するユーティリティ
final class ControllerWriter {
  private ControllerWriter() {}

  /// コントローラークラスを出力する。操作ごとに`POST /操作名`のメソッドを持ち、CorbaClientをコンストラクタで受け取る。
  ///
  /// @param service サービスのモデル
  /// @param clientClassName CorbaClientクラスの名前
  /// @param ctx コード生成コンテキスト
  /// @throws IOException ファイルの書き込みに失敗した場合
  static void write(Service service, String clientClassName, GenContext ctx) throws IOException {
    String controllerName = service.name() + "Controller";
    String clientFqn = ctx.basePackage() + ".corba." + clientClassName;

    String methods =
        service.operations().stream()
            .map(op -> ControllerMethodBuilder.build(op, ctx))
            .collect(Collectors.joining());

    String fieldAndCtor =
        """
          private final %1$s client;

          public %2$s(%1$s client) {
            this.client = client;
          }
        """
            .formatted(clientFqn, controllerName);

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
            .formatted(ctx.basePackage(), controllerName, fieldAndCtor, methods);

    Files.writeString(ctx.controllerDir().resolve(controllerName + ".java"), controller);
  }
}
