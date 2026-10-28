package com.toru_otsuki.bridge_generator.gen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/// 生成プロジェクトの共通ファイル（ビルド設定・エントリポイント・サーブレット）を出力するユーティリティ
final class ProjectScaffolder {
  private ProjectScaffolder() {}

  /// build.gradleを出力する。WARとしてもデプロイできるよう`war`プラグインを入れ、CORBAの実装にはglassfish-corba-orbとその実行に要るgmbalを使う。
  ///
  /// 組み込みTomcatは`providedRuntime`にする。外部のTomcatにデプロイしたとき、WARの中の組み込みTomcatとTomcat本体のクラスが
  /// 重ならないようにするため。`java -jar`で起動するときは、WARの`lib-provided`から読み込まれる。
  ///
  /// @param outputDir 生成プロジェクトのルートディレクトリ
  /// @throws IOException ファイルの書き込みに失敗した場合
  static void writeBuildGradle(Path outputDir) throws IOException {
    String content =
        """
        plugins {
          id 'java'
          id 'war'
          id 'org.springframework.boot' version '4.1.2'
          id 'io.spring.dependency-management' version '1.1.7'
        }

        group = 'generated'
        version = '0.0.1-SNAPSHOT'

        java {
          toolchain {
            languageVersion = JavaLanguageVersion.of(25)
          }
        }

        repositories {
          mavenCentral()
        }

        dependencies {
          implementation 'org.springframework.boot:spring-boot-starter-webmvc'
          implementation 'org.glassfish.corba:glassfish-corba-orb:5.0.3'
          // glassfish-corba-orb 5.xはgmbalのAPIだけに依存するが、ORBの初期化時に実装を探すため本体も必要
          runtimeOnly 'org.glassfish.gmbal:gmbal:4.1.2'
          implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1'
          // 組み込みTomcatは、外部のTomcatにデプロイするWARのWEB-INF/libには入れない（java -jarで動かすときは使われる）
          providedRuntime 'org.springframework.boot:spring-boot-starter-tomcat-runtime'
        }
        """;
    Files.writeString(
        outputDir.resolve("build.gradle"),
        content,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  /// Spring Bootのエントリポイント（Application.java）を出力する。glassfish-corbaが起動時に出す警告を抑えるシステムプロパティも設定する。
  ///
  /// @param basePkgName 生成プロジェクトのベースパッケージ
  /// @param pkgBaseDir ベースパッケージのディレクトリ
  /// @throws IOException ファイルの書き込みに失敗した場合
  static void writeApplicationClass(String basePkgName, Path pkgBaseDir) throws IOException {
    String content =
        """
        package %s;

        import org.springframework.boot.SpringApplication;
        import org.springframework.boot.autoconfigure.SpringBootApplication;

        @SpringBootApplication
        public class Application {
          public static void main(String[] args) {
            System.setProperty(
              "org.glassfish.gmbal.no.multipleUpperBoundsException",
              "true");
            SpringApplication.run(Application.class, args);
          }
        }
        """
            .formatted(basePkgName);
    Files.writeString(
        pkgBaseDir.resolve("Application.java"),
        content,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  /// WARデプロイ用のServletInitializer.javaを出力する。Applicationと同じシステムプロパティを設定する。
  ///
  /// @param basePkgName 生成プロジェクトのベースパッケージ
  /// @param pkgBaseDir ベースパッケージのディレクトリ
  /// @throws IOException ファイルの書き込みに失敗した場合
  static void writeServletClass(String basePkgName, Path pkgBaseDir) throws IOException {
    String content =
        """
        package %s;

        import org.springframework.boot.builder.SpringApplicationBuilder;
        import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

        public class ServletInitializer extends SpringBootServletInitializer {
          @Override
          protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
            System.setProperty(
              "org.glassfish.gmbal.no.multipleUpperBoundsException",
              "true");
            return application.sources(Application.class);
          }
        }
        """
            .formatted(basePkgName);
    Files.writeString(
        pkgBaseDir.resolve("ServletInitializer.java"),
        content,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }
}
