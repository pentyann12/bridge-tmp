package com.toru_otsuki.bridge_generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/// テスト用にジェネレータを実行し、生成物を読み出すヘルパー
final class TestGeneration {
  /// 生成ロジックの各分岐を通るように作った回帰テスト用IDL
  static final Path COVERAGE_IDL = Path.of("src/test/resources/idl/coverage.idl");

  private TestGeneration() {}

  /// 生成結果
  ///
  /// @param stubRoot idljの出力先
  /// @param projects 生成したSpring Bootプロジェクトのディレクトリ
  record Result(Path stubRoot, List<Path> projects) {
    /// 生成したプロジェクトが1つだけであることを確かめて返す
    ///
    /// @return 唯一のプロジェクトのディレクトリ
    /// @throws IllegalStateException プロジェクトが1つでない場合
    Path singleProject() {
      if (projects.size() != 1) throw new IllegalStateException("projects: " + projects);
      return projects.get(0);
    }
  }

  /// 作業ディレクトリ配下にスタブとプロジェクトを生成する
  ///
  /// @param workDir 作業ディレクトリ
  /// @param args `--stub-root`と`--output-root`以外のコマンドライン引数
  /// @return 生成結果
  /// @throws Exception 生成に失敗した場合
  static Result run(Path workDir, String... args) throws Exception {
    Path stubRoot = workDir.resolve("stubs");
    List<String> all = new ArrayList<>();
    all.add("--stub-root=" + stubRoot);
    all.add("--output-root=" + workDir.resolve("spring"));
    all.addAll(List.of(args));
    List<Path> projects = new BridgeGenerator(CliOptions.parse(all.toArray(String[]::new))).run();
    return new Result(stubRoot, projects);
  }

  /// プロジェクト内のファイルを読み出す。idljスタブのコピーは除く。
  ///
  /// @param project プロジェクトのディレクトリ
  /// @param stubRoot idljの出力先（コピーされたスタブの判定に使う）
  /// @return プロジェクトからの相対パス（`/`区切り）→ ファイル内容
  /// @throws IOException ファイルの読み込みに失敗した場合
  static Map<String, String> generatedFiles(Path project, Path stubRoot) throws IOException {
    Path javaDir = project.resolve("src/main/java");
    Map<String, String> files = new TreeMap<>();
    try (Stream<Path> walk = Files.walk(project)) {
      for (Path p : walk.filter(Files::isRegularFile).toList()) {
        if (p.startsWith(javaDir) && Files.exists(stubRoot.resolve(javaDir.relativize(p)))) {
          continue;
        }
        files.put(project.relativize(p).toString().replace('\\', '/'), read(p));
      }
    }
    return files;
  }

  /// ファイルを読み、改行をLFにそろえる。チェックアウト時の改行変換に左右されないようにするため。
  ///
  /// @param p 読むファイル
  /// @return 改行をLFにそろえた内容
  /// @throws IOException ファイルの読み込みに失敗した場合
  static String read(Path p) throws IOException {
    return Files.readString(p).replace("\r\n", "\n");
  }
}
