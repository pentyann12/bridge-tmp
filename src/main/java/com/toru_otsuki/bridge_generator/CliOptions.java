package com.toru_otsuki.bridge_generator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// コマンドライン引数を解析した結果
///
/// @param idlFiles 処理対象のIDLファイル
/// @param stubRoot idljの出力先ディレクトリ
/// @param outputRoot Springプロジェクトの出力先ディレクトリ
/// @param basePackage 生成プロジェクトのベースパッケージ
/// @param idlPackagePrefix `idlj -pkgPrefix`に渡すパッケージプレフィックス（空なら指定しない）
/// @param returnAsDto 戻り値・out引数をDTOに変換するかどうか
/// @param singleProject 全サービスを単一プロジェクトにまとめるかどうか
/// @param projectName 単一プロジェクト時のプロジェクト名
/// @param help ヘルプ表示が要求されたかどうか
record CliOptions(
    List<Path> idlFiles,
    Path stubRoot,
    Path outputRoot,
    String basePackage,
    String idlPackagePrefix,
    boolean returnAsDto,
    boolean singleProject,
    String projectName,
    boolean help) {

  CliOptions {
    idlFiles = List.copyOf(idlFiles);
  }

  /// コマンドライン引数を解析する。`--help`が現れた時点で以降の引数は読まない。
  ///
  /// `-`で始まらない引数はすべてIDLファイルとして扱う。
  ///
  /// @param args コマンドライン引数
  /// @return 解析結果
  /// @throws IllegalArgumentException 不明なオプション・不正な値・IDLファイル未指定の場合
  static CliOptions parse(String... args) {
    List<Path> idlFiles = new ArrayList<>();
    Path stubRoot = Path.of("generated-stubs");
    Path outputRoot = Path.of("generated-spring");
    String basePackage = "com.generated";
    String idlPackagePrefix = "";
    String returnMode = "dto";
    boolean singleProject = false;
    String projectName = "CombinedApi";

    for (String arg : args) {
      if (arg.startsWith("--stub-root=")) {
        stubRoot = Path.of(valueOf(arg));
      } else if (arg.startsWith("--output-root=")) {
        outputRoot = Path.of(valueOf(arg));
      } else if (arg.startsWith("--base-package=")) {
        basePackage = valueOf(arg);
      } else if (arg.startsWith("--idl-package-prefix=")) {
        idlPackagePrefix = valueOf(arg);
      } else if (arg.startsWith("--return-mode=")) {
        returnMode = valueOf(arg);
      } else if (arg.equals("--single-project")) {
        singleProject = true;
      } else if (arg.startsWith("--project-name=")) {
        projectName = valueOf(arg);
      } else if (arg.equals("--help") || arg.equals("-h")) {
        return new CliOptions(
            List.of(), stubRoot, outputRoot, basePackage, "", true, false, projectName, true);
      } else if (arg.startsWith("-")) {
        throw new IllegalArgumentException("不明なオプションです: " + arg);
      } else {
        idlFiles.add(Path.of(arg));
      }
    }

    boolean returnAsDto =
        switch (returnMode.toLowerCase()) {
          case "", "dto" -> true;
          case "raw" -> false;
          default ->
              throw new IllegalArgumentException(
                  "--return-mode には dto か raw を指定してください: " + returnMode);
        };
    if (idlFiles.isEmpty()) {
      throw new IllegalArgumentException("IDLファイルが指定されていません");
    }
    return new CliOptions(
        idlFiles,
        stubRoot,
        outputRoot,
        basePackage,
        idlPackagePrefix,
        returnAsDto,
        singleProject,
        projectName,
        false);
  }

  private static String valueOf(String arg) {
    return arg.substring(arg.indexOf('=') + 1);
  }

  static String usage() {
    return """
        Usage: [options] <idl-file>...

        Options:
          --stub-root=<path>          IDL生成Javaソースの出力先ディレクトリ(default: generated-stubs)
          --output-root=<path>        Springプロジェクト出力先ディレクトリ(default: generated-spring)
          --base-package=<package>    生成されるSpringプロジェクトのベースパッケージ(default: com.generated)
          --idl-package-prefix=<pkg>  IDLモジュール名に対してidljのパッケージプレフィックスを適用
          --return-mode=dto|raw       戻り値・out引数をDTOに変換するか(dto)、CORBA型のまま返すか(raw)(default: dto)
          --single-project            全サービスを1つのSpringプロジェクトにまとめる
          --project-name=<name>       --single-project 時のプロジェクト名(default: CombinedApi)
          --help, -h                  このヘルプを表示

        """;
  }
}
