package com.example.parser;

import java.nio.file.Path;

/** 生成プロジェクトの出力先ディレクトリ群を保持する不変ホルダー */
final class ProjectLayout {
  final Path javaDir;
  final Path controllerDir;
  final Path corbaDir;

  /** Holder の value 型解決でスタブファイルを参照するために保持する */
  final Path stubSourceRoot;

  ProjectLayout(Path javaDir, Path controllerDir, Path corbaDir, Path stubSourceRoot) {
    this.javaDir = javaDir;
    this.controllerDir = controllerDir;
    this.corbaDir = corbaDir;
    this.stubSourceRoot = stubSourceRoot;
  }
}
