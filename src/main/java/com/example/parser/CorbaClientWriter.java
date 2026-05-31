package com.example.parser;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;

/** CORBA サービス呼び出しをラップする CorbaClient クラスを生成するユーティリティ */
final class CorbaClientWriter {
  private CorbaClientWriter() {}

  /**
   * CorbaClient クラスを生成してファイルに書き出す
   *
   * @param serviceName サービス名（例: "ItemService"）
   * @param servicePackage サービスが属するパッケージ
   * @param methods サービスインターフェースのメソッド一覧
   * @param corbaDir 出力ディレクトリ
   * @param basePackage ベースパッケージ名
   * @param clientClassName 生成するクラスの名前（例: "CorbaClient", "ItemServiceCorbaClient"）
   */
  static void write(
      String serviceName,
      String servicePackage,
      List<MethodDeclaration> methods,
      Path corbaDir,
      String basePackage,
      String clientClassName)
      throws IOException {

    String serviceImports =
        servicePackage.isEmpty()
            ? ""
            : """
          import %s.%s;
          import %s.%sHelper;
          """
                .formatted(servicePackage, serviceName, servicePackage, serviceName);

    String methodsCode =
        methods.stream()
            .map(
                method -> {
                  String returnType = method.getType().asString();
                  String params =
                      method.getParameters().stream()
                          .map(
                              p ->
                                  CorbaTypeUtils.resolveTypeFQN(
                                          p.getType().asString(), method, servicePackage)
                                      + " "
                                      + p.getNameAsString())
                          .collect(Collectors.joining(", "));
                  String args =
                      method.getParameters().stream()
                          .map(Parameter::getNameAsString)
                          .collect(Collectors.joining(", "));
                  String call =
                      ("void".equals(returnType) ? "service." : "return service.")
                          + method.getNameAsString()
                          + "("
                          + args
                          + ")";
                  return """
              public %s %s(%s) {
                  %s;
              }

          """
                      .formatted(
                          CorbaTypeUtils.resolveTypeFQN(returnType, method, servicePackage),
                          method.getNameAsString(),
                          params,
                          call);
                })
            .collect(Collectors.joining());

    String source =
        """
        package %s.corba;

        import org.omg.CORBA.ORB;
        import org.omg.CORBA.Object;
        import org.springframework.beans.factory.annotation.Value;
        import org.springframework.stereotype.Component;
        %s
        @Component
        public class %s {
            private final %s service;

            public %s(@Value("${corba.%s.ior}") String ior) {
                ORB orb = ORB.init(new String[0], null);
                org.omg.CORBA.Object obj = orb.string_to_object(ior);
                this.service = %sHelper.narrow(obj);
            }

        %s}
        """
            .formatted(
                basePackage,
                serviceImports,
                clientClassName,
                serviceName,
                clientClassName,
                serviceName,
                serviceName,
                methodsCode);

    Files.writeString(
        corbaDir.resolve(clientClassName + ".java"),
        source,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }
}
