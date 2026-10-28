package com.toru_otsuki.bridge_generator.gen;

import java.nio.file.Path;

/// 1つのSpring Bootプロジェクトを生成するためのコンテキスト。生成設定と出力先ディレクトリをまとめ、全ライターに渡される。
///
/// @param settings コード生成全体の設定
/// @param outputDir 生成するプロジェクトのルートディレクトリ
record GenContext(GenSettings settings, Path outputDir) {

  String basePackage() {
    return settings.basePackage();
  }

  boolean returnAsDto() {
    return settings.returnAsDto();
  }

  TypeMapping types() {
    return settings.types();
  }

  String dtoPackage() {
    return types().dtoPackage();
  }

  Path javaDir() {
    return outputDir.resolve("src/main/java");
  }

  Path packageDir() {
    return javaDir().resolve(basePackage().replace('.', '/'));
  }

  Path controllerDir() {
    return packageDir().resolve("controller");
  }

  Path corbaDir() {
    return packageDir().resolve("corba");
  }

  Path dtoDir() {
    return packageDir().resolve("dto");
  }

  Path mapperDir() {
    return packageDir().resolve("mapper");
  }

  /// 共有ライブラリの、型付きクライアントとCORBA接続を置くディレクトリ
  Path clientDir() {
    return packageDir().resolve("client");
  }

  /// 共有ライブラリの、例外クラスを置くディレクトリ
  Path exceptionDir() {
    return packageDir().resolve("exception");
  }

  /// 共有ライブラリの、Spring Bootの自動設定を置くディレクトリ
  Path springDir() {
    return packageDir().resolve("spring");
  }

  Path resourcesDir() {
    return outputDir.resolve("src/main/resources");
  }
}
