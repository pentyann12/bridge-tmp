package com.toru_otsuki.bridge_generator.gen;

import com.toru_otsuki.bridge_generator.model.Param;
import com.toru_otsuki.bridge_generator.model.Service;
import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Collectors;

/// CORBAサービス呼び出しをラップするCorbaClientクラスを生成するユーティリティ
final class CorbaClientWriter {
  private CorbaClientWriter() {}

  /// アプリで1つのORBを持つ`CorbaOrb`クラスを出力する。全サービスのCorbaClientで共有し、アプリの終了時に破棄する
  ///
  /// ORBは既定ではJMXにMBeanを登録しない。glassfish-corbaのORBはJVMで共有されるMBeanサーバーに固定の名前で登録するため、
  /// 生成したアプリを同じTomcatに複数（パラレルデプロイを含む）置くと、2つ目以降のORBが初期化に失敗するため。
  /// `corba.orb.*`はORBの設定としてそのまま渡し、既定値より優先する。
  ///
  /// glassfish-corbaのORBが初期化に必要とするシステムプロパティ（gmbal）もここで設定するので、JVM引数は要らない。
  ///
  /// @param ctx コード生成コンテキスト
  /// @throws IOException ファイルの書き込みに失敗した場合
  static void writeOrb(GenContext ctx) throws IOException {
    Files.writeString(
        ctx.corbaDir().resolve("CorbaOrb.java"),
        """
        package %1$s.corba;

        import java.util.Properties;
        import org.omg.CORBA.ORB;
        import org.springframework.beans.factory.DisposableBean;
        import org.springframework.boot.context.properties.bind.Bindable;
        import org.springframework.boot.context.properties.bind.Binder;
        import org.springframework.core.env.Environment;
        import org.springframework.stereotype.Component;

        @Component
        public class CorbaOrb implements DisposableBean {
          private static final String GMBAL_PROPERTY = "org.glassfish.gmbal.no.multipleUpperBoundsException";
          private static final String REGISTER_MBEANS_PROPERTY = "com.sun.corba.ee.ORBRegisterMBeans";

          private final ORB orb;

          public CorbaOrb(Environment env) {
            if (System.getProperty(GMBAL_PROPERTY) == null) {
              System.setProperty(GMBAL_PROPERTY, "true");
            }
            Properties properties = new Properties();
            properties.setProperty(REGISTER_MBEANS_PROPERTY, "false");
            Binder.get(env).bind("corba.orb", Bindable.mapOf(String.class, String.class)).ifBound(properties::putAll);
            this.orb = ORB.init(new String[0], properties);
            %1$s.mapper.AnyMapper.useOrb(orb);
          }

          public ORB orb() {
            return orb;
          }

          @Override
          public void destroy() {
            %1$s.mapper.AnyMapper.releaseOrb(orb);
            orb.shutdown(false);
            orb.destroy();
          }
        }
        """
            .formatted(ctx.basePackage()));
  }

  /// CorbaClientクラスを出力する。IORは`corba.サービス名.ior`プロパティから受け取り、Beanの生成時に共有のORB（`CorbaOrb`）で一度だけ解決する。
  ///
  /// 各操作は、スタブと同じシグネチャでサービスへそのまま委譲するメソッドになる。
  ///
  /// @param service サービスのモデル
  /// @param clientClassName 生成するクラスの名前（例: `CorbaClient`、`ItemServiceCorbaClient`）
  /// @param ctx コード生成コンテキスト
  /// @throws IOException ファイルの書き込みに失敗した場合
  static void write(Service service, String clientClassName, GenContext ctx) throws IOException {
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

    // TODO raises（ユーザー例外）に対応する。スタブのメソッドはチェック例外を投げるが、ここでは宣言も捕捉もしていないので、
    //  raisesのあるIDLから生成するとコンパイルできない。対応するには、エラー時のJSONの形（例外の型名と中身の載せ方）と
    //  HTTPステータスを決めて、ControllerMethodBuilderで例外をレスポンスに変換する必要がある。
    String methods =
        service.operations().stream()
            .map(
                op -> {
                  String params =
                      op.params().stream()
                          .map(p -> javaType(p, types) + " " + p.name())
                          .collect(Collectors.joining(", "));
                  String args =
                      op.params().stream().map(Param::name).collect(Collectors.joining(", "));
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

        import org.springframework.beans.factory.annotation.Value;
        import org.springframework.stereotype.Component;
        %2$s
        @Component
        public class %3$s {
          private final %4$s service;

          public %3$s(CorbaOrb corbaOrb, @Value("${corba.%4$s.ior}") String ior) {
            org.omg.CORBA.Object obj = corbaOrb.orb().string_to_object(ior);
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
  static String javaType(Param param, TypeMapping types) {
    return param.isOut() ? param.holderType() : types.corbaType(param.type()).toString();
  }
}
