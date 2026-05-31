package com.example.parser;

import com.github.javaparser.ast.body.MethodDeclaration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;

/** Spring MVC コントローラークラスを生成するユーティリティ */
final class ControllerWriter {
  private ControllerWriter() {}

  /**
   * コントローラークラスを生成してファイルに書き出す メソッドごとに {@link ControllerMethodBuilder#build} を呼び出してコードを組み立てます
   *
   * @param serviceName サービス名
   * @param servicePackage サービスが属するパッケージ
   * @param methods サービスインターフェースのメソッド一覧
   * @param controllerDir 出力ディレクトリ
   * @param basePackage ベースパッケージ名
   * @param returnAsDto 複雑な戻り値型を DTO に変換するかどうか
   * @param stubRoot スタブのルートディレクトリ（Holder value 型解決に使用）
   * @param clientClassName CorbaClient クラスの名前
   */
  static void write(
      String serviceName,
      String servicePackage,
      List<MethodDeclaration> methods,
      Path controllerDir,
      String basePackage,
      boolean returnAsDto,
      Path stubRoot,
      String clientClassName)
      throws IOException {

    String controllerName = serviceName + "Controller";
    String dtoPackage = basePackage + ".dto";
    String clientFqn = basePackage + ".corba." + clientClassName;

    String methodsCode =
        methods.stream()
            .map(
                method ->
                    ControllerMethodBuilder.build(
                        method, basePackage, dtoPackage, servicePackage, returnAsDto, stubRoot))
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
            .formatted(basePackage, controllerName, fieldAndCtor, methodsCode);

    Files.writeString(
        controllerDir.resolve(controllerName + ".java"),
        controller,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }
}
