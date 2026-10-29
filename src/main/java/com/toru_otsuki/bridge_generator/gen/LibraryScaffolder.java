package com.toru_otsuki.bridge_generator.gen;

import com.toru_otsuki.bridge_generator.model.Service;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/// 共有ライブラリの、IDLのコード以外の部分（ビルド設定・Spring Bootの自動設定・README）を生成する
final class LibraryScaffolder {
  private LibraryScaffolder() {}

  /// build.gradleとsettings.gradleを出力する
  ///
  /// jarを直接配布するので依存は利用側に引き継がれない。そのため依存の一覧はREADMEにも書く。 Spring Bootは利用側にあるときだけ使うので、コンパイル時だけの依存にする。
  ///
  /// @param outputDir プロジェクトのルートディレクトリ
  /// @param projectName プロジェクト名（jarの名前になる）
  /// @throws IOException ファイルの書き込みに失敗した場合
  static void writeBuildFiles(Path outputDir, String projectName) throws IOException {
    Files.writeString(
        outputDir.resolve("build.gradle"),
        """
        plugins {
          id 'java-library'
        }

        group = 'generated'
        version = '0.0.1'

        java {
          toolchain {
            languageVersion = JavaLanguageVersion.of(25)
          }
          withSourcesJar()
        }

        repositories {
          mavenCentral()
        }

        dependencies {
          api 'org.glassfish.corba:glassfish-corba-omgapi:5.0.3'
          runtimeOnly 'org.glassfish.corba:glassfish-corba-orb:5.0.3'
          // glassfish-corba-orb 5.xはgmbalのAPIだけに依存するが、ORBの初期化時に実装を探すため本体も必要
          runtimeOnly 'org.glassfish.gmbal:gmbal:4.1.2'
          // Spring Bootの自動設定。利用側がSpring Bootのときだけ働く
          compileOnly 'org.springframework.boot:spring-boot-autoconfigure:4.1.2'
        }
        """);
    Files.writeString(
        outputDir.resolve("settings.gradle"), "rootProject.name = '%s'\n".formatted(projectName));
  }

  /// Spring Bootの自動設定クラスと、それを登録するファイルを出力する
  ///
  /// `CorbaConnection`をBeanにし、IORが設定されたサービスだけクライアントをBeanにする。使わないサービスのIORを書かなくても
  /// 起動できるようにするため。`corba.orb.*`はORBの設定としてそのまま渡す。
  ///
  /// @param services クライアントを生成したサービス
  /// @param ctx コード生成コンテキスト
  /// @throws IOException ファイルの書き込みに失敗した場合
  static void writeSpringAutoConfiguration(List<Service> services, GenContext ctx)
      throws IOException {
    String base = ctx.basePackage();
    String beans =
        services.stream()
            .map(
                s ->
                    """

                      @Bean
                      @ConditionalOnMissingBean
                      @ConditionalOnProperty("corba.%1$s.ior")
                      public %2$s %3$s(CorbaConnection connection, Environment env) {
                        return new %2$s(connection, env.getRequiredProperty("corba.%1$s.ior"));
                      }
                    """
                        .formatted(s.name(), ClientWriter.className(s), beanName(s)))
            .collect(Collectors.joining());

    Files.writeString(
        ctx.springDir().resolve("CorbaClientAutoConfiguration.java"),
        """
        package %1$s.spring;

        import %1$s.client.*;
        import %1$s.mapper.StringMapper;
        import org.springframework.boot.autoconfigure.AutoConfiguration;
        import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
        import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
        import org.springframework.boot.context.properties.bind.Bindable;
        import org.springframework.boot.context.properties.bind.Binder;
        import org.springframework.context.annotation.Bean;
        import org.springframework.core.env.Environment;

        /**
         * Spring Bootの自動設定。CorbaConnectionと、corba.サービス名.ior が設定されたサービスのクライアントをBeanにする。
         *
         * <p>corba.string-charset で、CORBAサーバーが文字列に使う文字コードを指定できる（既定は %2$s、noneなら読み替えない）。
         * corba.orb.* はORBの設定としてそのまま渡す（例: corba.orb.[org.omg.CORBA.ORBInitialHost]）。
         */
        @AutoConfiguration
        public class CorbaClientAutoConfiguration {
          public CorbaClientAutoConfiguration(Environment env) {
            StringMapper.configure(env.getProperty("corba.string-charset", "%2$s"));
          }

          @Bean(destroyMethod = "close")
          @ConditionalOnMissingBean
          public CorbaConnection corbaConnection(Environment env) {
            java.util.Properties orbProperties = new java.util.Properties();
            Binder.get(env).bind("corba.orb", Bindable.mapOf(String.class, String.class)).ifBound(orbProperties::putAll);
            return new CorbaConnection(orbProperties);
          }
        %3$s}
        """
            .formatted(base, ctx.settings().stringCharset(), beans));

    Path imports = ctx.resourcesDir().resolve("META-INF/spring");
    Files.createDirectories(imports);
    Files.writeString(
        imports.resolve("org.springframework.boot.autoconfigure.AutoConfiguration.imports"),
        base + ".spring.CorbaClientAutoConfiguration\n");
  }

  /// 利用者向けのREADME.mdを出力する。依存の一覧・設定・呼び出し方・例外を、生成した最初のサービスを例に説明する
  ///
  /// @param services クライアントを生成したサービス
  /// @param projectName プロジェクト名
  /// @param ctx コード生成コンテキスト
  /// @throws IOException ファイルの書き込みに失敗した場合
  static void writeReadme(List<Service> services, String projectName, GenContext ctx)
      throws IOException {
    String base = ctx.basePackage();
    String clients =
        services.stream()
            .map(
                s ->
                    "| `%s` | `%s.client.%s` | `corba.%s.ior` |"
                        .formatted(s.name(), base, ClientWriter.className(s), s.name()))
            .collect(Collectors.joining("\n"));
    String iors =
        services.stream()
            .map(s -> "  %s:\n    ior: IOR:...".formatted(s.name()))
            .collect(Collectors.joining("\n"));
    String example = services.isEmpty() ? "ItemService" : services.get(0).name();

    Files.writeString(
        ctx.outputDir().resolve("README.md"),
        """
        # %1$s

        IDLから生成した、CORBAサービスを型付きのJavaメソッドで呼ぶためのライブラリです。
        Holder・Any・CORBAの型・文字コードの読み替え・ORBの管理は、ライブラリの中で行います。

        ## ビルド

        ```
        gradle jar
        ```

        `build/libs/%1$s-0.0.1.jar` ができます（`-sources.jar` はIDEでの参照用）。

        ## 利用側の設定（Spring Boot）

        jarと、このライブラリが使う依存を追加します。jarを直接使うため、依存は利用側で書く必要があります。

        ```groovy
        dependencies {
          implementation files('libs/%1$s-0.0.1.jar')
          implementation 'org.glassfish.corba:glassfish-corba-omgapi:5.0.3'
          runtimeOnly 'org.glassfish.corba:glassfish-corba-orb:5.0.3'
          runtimeOnly 'org.glassfish.gmbal:gmbal:4.1.2'
        }
        ```

        application.yml に、使うサービスのIORを書きます。IORを書いたサービスのクライアントだけがBeanになります。

        ```yaml
        corba:
          # CORBAサーバーが文字列に使う文字コード（noneなら読み替えない）
          string-charset: %2$s
        %3$s
        ```

        | サービス | クライアント | IORの設定 |
        |---|---|---|
        %4$s

        ORBの設定を変えるときは `corba.orb` の下に書きます。キーにドットを含むので `[ ]` で囲みます。

        ```yaml
        corba:
          orb:
            "[com.sun.corba.ee.ORBRegisterMBeans]": true
        ```

        ### 同じTomcatに複数のアプリを置く場合

        このライブラリを使うWebアプリを同じTomcatに複数置くこと（パラレルデプロイを含む）ができます。

        - jarは各アプリの `WEB-INF/lib` に入れてください（Spring Bootのwarなら標準でそうなります）。Tomcatの共有lib（`$CATALINA_HOME/lib`）に置くと、文字コードの設定がアプリ間で混ざります。
        - ORBは既定でJMXにMBeanを登録しません。glassfish-corbaのORBはJVMで共有されるMBeanサーバーに固定の名前で登録するため、登録すると2つ目以降のアプリのORBが初期化に失敗します。`com.sun.corba.ee.ORBRegisterMBeans` を `true` にするのは、Tomcatにアプリが1つだけのときに限ってください。

        ## 呼び出し方

        ```java
        @Service
        class MyService {
          private final %5$sClient client;

          MyService(%5$sClient client) {
            this.client = client;
          }
        }
        ```

        - 引数・戻り値は、構造体ならDTO（`%6$s.dto.XxxDto`）、文字列は `String` です。
        - out/inout引数がある操作は、戻り値とout/inout引数をまとめたrecord（`XxxClient.操作名Result`）を返します。
        - サービスへの接続は、最初の呼び出しのときに行います。CORBAサーバーが止まっていてもアプリは起動できます。

        ### 例外

        - IDLの `raises` で宣言された例外は、`%6$s.exception.<名前>Exception` で投げられます。中身は `detail()` でDTOとして取り出せます。
        - 接続できない・通信が切れたなどのCORBAのシステム例外は、`%6$s.exception.CorbaCallException` で投げられます。

        どちらも `RuntimeException` です。

        ### Any

        `any` 型は `%6$s.dto.AnyValue`（型名と値の組）で受け渡します。

        ```java
        AnyValue v = AnyValue.of(tagDto);                  // 構造体・enumのDTOから作る（型名は自動で決まる）
        AnyValue n = new AnyValue("long", 42);              // 基本型は型名を指定する
        Optional<TagDto> tag = v.as(TagDto.class);          // 型を指定して取り出す
        ```

        sequence・union などはAnyに入れて送れません（受け取りはできます）。

        ## Spring Bootを使わない場合

        ```java
        try (CorbaConnection connection = new CorbaConnection()) {
          var client = new %5$sClient(connection, ior);
          ...
        }
        ```

        文字コードを変えるときは、最初の呼び出しの前に `StringMapper.configure("Windows-31J")` を呼びます。
        """
            .formatted(projectName, ctx.settings().stringCharset(), iors, clients, example, base));
  }

  private static String beanName(Service service) {
    String name = ClientWriter.className(service);
    return Character.toLowerCase(name.charAt(0)) + name.substring(1);
  }
}
