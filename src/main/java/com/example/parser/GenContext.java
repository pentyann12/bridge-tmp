package com.example.parser;

import java.nio.file.Path;

/**
 * サービス単位のコード生成に必要な設定と出力先ディレクトリをまとめたコンテキスト
 *
 * `SpringBootProjectGenerator.generateService`で生成され、下流の全ライター/ビルダーに渡される。
 *
 * - `basePackage` / `servicePackage` / `returnAsDto`: コード生成設定
 * - `idlPackagePrefix` / `repoIdPrefix`: CLIから受け取るIDL関連設定
 * - `javaDir` / `controllerDir` / `corbaDir` / `stubRoot`: 出力先ディレクトリ
 */
record GenContext(
    String basePackage,
    String servicePackage,
    boolean returnAsDto,
    String idlPackagePrefix,
    String repoIdPrefix,
    Path javaDir,
    Path controllerDir,
    Path corbaDir,
    Path stubRoot) {

  /** DTOパッケージ名（`basePackage + ".dto"`） */
  String dtoPackage() {
    return basePackage + ".dto";
  }

  /** DTO出力ディレクトリ（`javaDir/basePackage/dto`） */
  Path dtoDir() {
    return javaDir.resolve(basePackage.replace('.', '/') + "/dto");
  }

  /** Mapper出力ディレクトリ（`javaDir/basePackage/mapper`） */
  Path mapperDir() {
    return javaDir.resolve(basePackage.replace('.', '/') + "/mapper");
  }

  /** リソースファイル出力ディレクトリ（`javaDir/../resources`） */
  Path resourcesDir() {
    return javaDir.getParent().resolve("resources");
  }

  /**
   * ベースコンテキストにサービス固有の設定を適用した新しいコンテキストを返す
   *
   * @param servicePackage サービスが属するパッケージ
   * @param returnAsDto 複雑な戻り値型をDTOに変換するかどうか
   * @return サービス固有フィールドを持つ新しい`GenContext`
   */
  GenContext withService(String servicePackage, boolean returnAsDto) {
    return new GenContext(
        basePackage, servicePackage, returnAsDto, idlPackagePrefix, repoIdPrefix,
        javaDir, controllerDir, corbaDir, stubRoot);
  }
}
