package com.toru_otsuki.bridge_generator.gen;

import com.toru_otsuki.bridge_generator.stub.StubModel;
import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Collectors;

/// CORBAサービス呼び出しをラップするCorbaClientクラスを生成するユーティリティ
final class CorbaClientWriter {
  private CorbaClientWriter() {}

  /// CorbaClientクラスを出力する。IORは`corba.サービス名.ior`プロパティから受け取り、Beanの生成時に一度だけ接続する。
  ///
  /// 各操作は、スタブと同じシグネチャでサービスへそのまま委譲するメソッドになる。
  ///
  /// @param service サービスのモデル
  /// @param clientClassName 生成するクラスの名前（例: `CorbaClient`、`ItemServiceCorbaClient`）
  /// @param ctx コード生成コンテキスト
  /// @throws IOException ファイルの書き込みに失敗した場合
  static void write(StubModel.Service service, String clientClassName, GenContext ctx)
      throws IOException {
    TypeMapping types = ctx.types();
    String serviceName = service.name();
    String serviceImports =
        service.pkg().isEmpty()
            ? ""
            : """
              import %1$s.%2$s;
              import %1$s.%2$sHelper;
              """
                .formatted(service.pkg(), serviceName);

    String methods =
        service.operations().stream()
            .map(
                op -> {
                  String params =
                      op.params().stream()
                          .map(p -> javaType(p, types) + " " + p.name())
                          .collect(Collectors.joining(", "));
                  String args =
                      op.params().stream()
                          .map(StubModel.Param::name)
                          .collect(Collectors.joining(", "));
                  String returnType =
                      op.returnType().map(t -> types.corbaType(t).toString()).orElse("void");
                  String call =
                      (op.returnType().isPresent() ? "return service." : "service.")
                          + op.name()
                          + "("
                          + args
                          + ")";
                  return """
                        public %s %s(%s) {
                          %s;
                        }

                      """
                      .formatted(returnType, op.name(), params, call);
                })
            .collect(Collectors.joining());

    String source =
        """
        package %1$s.corba;

        import org.omg.CORBA.ORB;
        import org.springframework.beans.factory.annotation.Value;
        import org.springframework.stereotype.Component;
        %2$s
        @Component
        public class %3$s {
          private final %4$s service;

          public %3$s(@Value("${corba.%4$s.ior}") String ior) {
            ORB orb = ORB.init(new String[0], null);
            org.omg.CORBA.Object obj = orb.string_to_object(ior);
            this.service = %4$sHelper.narrow(obj);
          }

        %5$s}
        """
            .formatted(ctx.basePackage(), serviceImports, clientClassName, serviceName, methods);

    Files.writeString(ctx.corbaDir().resolve(clientClassName + ".java"), source);
  }

  /// スタブを呼び出すときの引数の型を返す
  ///
  /// @param param 引数のモデル
  /// @param types 型の対応付け
  /// @return out/inout引数ならHolder型、in引数ならCORBA側の型（いずれも完全修飾名）
  static String javaType(StubModel.Param param, TypeMapping types) {
    return param.isOut() ? param.holderType() : types.corbaType(param.type()).toString();
  }
}
