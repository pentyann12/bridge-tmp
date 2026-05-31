package com.example.parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** 生成プロジェクトの共通ファイル（ビルド設定・エントリポイント・サーブレット）を出力するユーティリティ */
final class ProjectScaffolder {
  private ProjectScaffolder() {}

  /**
   * build.gradle を出力する
   *
   * @param outputDir 出力先ディレクトリ
   */
  static void writeBuildGradle(Path outputDir) throws IOException {
    String content =
        """
        plugins {
          id 'java'
          id 'war'
          id 'org.springframework.boot' version '4.0.6'
          id 'io.spring.dependency-management' version '1.1.7'
        }

        group = 'generated'
        version = '0.0.1-SNAPSHOT'

        java {
          toolchain {
            languageVersion = JavaLanguageVersion.of(21)
          }
        }

        repositories {
          mavenCentral()
        }

        dependencies {
          implementation 'org.springframework.boot:spring-boot-starter-web'
          implementation 'com.fasterxml.jackson.core:jackson-databind:2.15.2'
          implementation 'org.glassfish.corba:glassfish-corba-orb:4.2.5'
          implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0'
        }
        """;
    Files.writeString(
        outputDir.resolve("build.gradle"),
        content,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  /**
   * Spring Boot エントリポイントクラスを出力する
   *
   * @param basePkgName 基礎パッケージ名
   * @param pkgBaseDir 出力先ディレクトリ
   */
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

  /**
   * ServletInitializer を出力する
   *
   * @param basePkgName 基礎パッケージ名
   * @param pkgBaseDir 出力先ディレクトリ
   */
  static void writeServletClass(String basePkgName, Path pkgBaseDir) throws IOException {
    String content =
        """
        package %s;

        import org.springframework.boot.builder.SpringApplicationBuilder;
        import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

        public class ServletInitializer extends SpringBootServletInitializer {
          @Override
          protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
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
