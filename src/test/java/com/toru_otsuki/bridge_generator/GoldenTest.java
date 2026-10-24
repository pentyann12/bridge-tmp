package com.toru_otsuki.bridge_generator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/// 網羅用IDLから生成したプロジェクトを、`src/test/resources/golden/`の期待値とファイル単位で比較する
///
/// 生成ロジックを意図して変えたときは`./gradlew test -Dgolden.update=true`で期待値を更新し、差分をレビューしてからコミットする。
class GoldenTest {
  /// 期待値のルートディレクトリ。シナリオ名のサブディレクトリに生成物を置く。
  private static final Path GOLDEN_ROOT = Path.of("src/test/resources/golden");

  /// 比較するシナリオを返す
  ///
  /// @return シナリオ名とコマンドライン引数（IDLファイル以外）の組
  static Stream<Arguments> scenarios() {
    return Stream.of(
        Arguments.of("coverage-dto", List.of("--single-project", "--idl-package-prefix=com.corba")),
        Arguments.of("coverage-raw", List.of("--single-project", "--return-mode=raw")));
  }

  /// 生成したプロジェクトのファイル一覧と各ファイルの内容が期待値と一致することを確かめる。`golden.update`が指定されていれば、比較せずに期待値を書き換える。
  ///
  /// @param name シナリオ名（期待値のディレクトリ名）
  /// @param options コマンドライン引数（IDLファイル以外）
  /// @param tmp 作業ディレクトリ
  /// @throws Exception 生成やファイルの読み書きに失敗した場合
  @ParameterizedTest(name = "{0}")
  @MethodSource("scenarios")
  void generatedProjectMatchesGolden(String name, List<String> options, @TempDir Path tmp)
      throws Exception {
    String[] args =
        Stream.concat(options.stream(), Stream.of(TestGeneration.COVERAGE_IDL.toString()))
            .toArray(String[]::new);
    TestGeneration.Result result = TestGeneration.run(tmp, args);
    Map<String, String> actual =
        TestGeneration.generatedFiles(result.singleProject(), result.stubRoot());

    Path goldenDir = GOLDEN_ROOT.resolve(name);
    if (Boolean.getBoolean("golden.update")) {
      writeGolden(goldenDir, actual);
      return;
    }

    Map<String, String> expected = new TreeMap<>();
    try (Stream<Path> walk = Files.walk(goldenDir)) {
      for (Path p : walk.filter(Files::isRegularFile).toList()) {
        expected.put(goldenDir.relativize(p).toString().replace('\\', '/'), TestGeneration.read(p));
      }
    }
    assertEquals(expected.keySet(), actual.keySet(), "生成されたファイルの一覧が期待値と異なります");
    for (String path : expected.keySet()) {
      assertEquals(expected.get(path), actual.get(path), path);
    }
  }

  /// 期待値のディレクトリを空にしてから、生成物で置き換える
  ///
  /// @param goldenDir 期待値のディレクトリ
  /// @param files 相対パス → ファイル内容
  /// @throws Exception ファイルの読み書きに失敗した場合
  private static void writeGolden(Path goldenDir, Map<String, String> files) throws Exception {
    if (Files.exists(goldenDir)) {
      try (Stream<Path> walk = Files.walk(goldenDir)) {
        for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
      }
    }
    for (Map.Entry<String, String> e : files.entrySet()) {
      Path target = goldenDir.resolve(e.getKey());
      Files.createDirectories(target.getParent());
      Files.writeString(target, e.getValue());
    }
  }
}
