package com.toru_otsuki.bridge_generator.stub;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

/// スタブの.javaファイルの列挙・並列処理・コピーを担うユーティリティ
public final class StubFiles {
  private StubFiles() {}

  /// 1ファイル分の処理
  @FunctionalInterface
  public interface FileAction {
    void apply(Path path) throws IOException;
  }

  public static List<Path> javaFiles(Path root) throws IOException {
    try (Stream<Path> walk = Files.walk(root)) {
      return walk.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".java")).toList();
    }
  }

  /// ファイルごとに独立した処理を並列に実行する。大きなIDLではスタブが1万ファイルに及ぶため。
  ///
  /// @param files 対象ファイル
  /// @param action 1ファイル分の処理
  /// @throws IOException いずれかのファイルの処理に失敗した場合
  public static void forEachParallel(List<Path> files, FileAction action) throws IOException {
    // 並列ストリームはチェック例外を投げられないので、包んで外で元に戻す
    try {
      files.parallelStream()
          .forEach(
              p -> {
                try {
                  action.apply(p);
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              });
    } catch (UncheckedIOException e) {
      throw e.getCause();
    }
  }

  /// スタブを生成先プロジェクトへ相対パスを保ったままコピーする
  ///
  /// @param stubSourceRoot スタブのルートディレクトリ
  /// @param javaDir コピー先のJavaソースディレクトリ
  /// @throws IOException コピーに失敗した場合
  public static void copyIdlStubSources(Path stubSourceRoot, Path javaDir) throws IOException {
    forEachParallel(
        javaFiles(stubSourceRoot),
        p -> {
          Path target = javaDir.resolve(stubSourceRoot.relativize(p));
          Files.createDirectories(target.getParent());
          Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
        });
  }
}
