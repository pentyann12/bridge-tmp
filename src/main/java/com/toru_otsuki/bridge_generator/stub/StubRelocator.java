package com.toru_otsuki.bridge_generator.stub;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/// idljがデフォルトパッケージ（stubRoot直下）に出力した.javaファイルを名前付きパッケージへ移動し、参照側のimport文を整合させる
///
/// IDLの仕様上、グローバルスコープで定義された型（typedef sequence等）はモジュール外のデフォルトパッケージに出力されるため、
/// 名前付きパッケージからimportできない。このクラスはその問題を後処理として修正する。
public final class StubRelocator {
  /// package宣言の行（行末の改行まで）
  private static final Pattern PACKAGE_LINE =
      Pattern.compile("^package\\s+[\\w.]+\\s*;[ \\t]*(?:\\r\\n|\\r|\\n)", Pattern.MULTILINE);

  /// package宣言のパッケージ名
  private static final Pattern PACKAGE_NAME =
      Pattern.compile("^package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);

  /// パッケージを持たない`import X;`の行（行末の改行まで）
  private static final Pattern BARE_IMPORT =
      Pattern.compile(
          "^import [A-Za-z_][A-Za-z0-9_$]*;[ \\t]*(?:\\r\\n|\\r|\\n)?", Pattern.MULTILINE);

  /// 行頭に限らず現れる`import X;`のクラス名
  private static final Pattern ANY_SIMPLE_IMPORT = Pattern.compile("import ([\\w$]+);");

  /// 識別子（`\b名前\b`との一致判定に使う最大連続の単語文字）
  private static final Pattern WORD = Pattern.compile("\\w+");

  private StubRelocator() {}

  /// デフォルトパッケージのスタブを移動し、全スタブのimport文を更新する
  ///
  /// @param stubRoot idljの出力先ルートディレクトリ
  /// @param idlPackagePrefix `-pkgPrefix`に渡したパッケージプレフィックス（空の場合は移動先を自動推定する）
  /// @throws IOException スタブの読み書きに失敗した場合
  public static void relocate(Path stubRoot, String idlPackagePrefix) throws IOException {
    List<Path> defaultPkgFiles;
    try (Stream<Path> stream = Files.list(stubRoot)) {
      defaultPkgFiles = stream.filter(Files::isRegularFile).filter(StubRelocator::isJava).toList();
    }
    if (defaultPkgFiles.isEmpty()) return;
    Map<String, Path> classToFile = new LinkedHashMap<>();
    for (Path f : defaultPkgFiles) {
      classToFile.put(className(f), f);
    }

    // デフォルトパッケージのファイルにpackage宣言を付けて移動先へ移す
    String targetPackage = resolveTargetPackage(stubRoot, idlPackagePrefix, defaultPkgFiles);
    Path targetDir = stubRoot.resolve(targetPackage.replace('.', '/'));
    Files.createDirectories(targetDir);
    StubFiles.forEachParallel(
        defaultPkgFiles,
        src -> {
          Files.writeString(
              targetDir.resolve(src.getFileName()),
              "package " + targetPackage + ";\n\n" + Files.readString(src));
          Files.delete(src);
        });

    // 移動したクラスを参照している全スタブのimportを直す。書き換えはファイル単位で独立しているので、1ファイル1回の読み書きで並列に処理する
    ImportFixer fixer =
        new ImportFixer(
            classToFile.keySet(), targetPackage, targetDir.toAbsolutePath().normalize());
    StubFiles.forEachParallel(
        StubFiles.javaFiles(stubRoot),
        path -> {
          String content = Files.readString(path);
          String newContent = fixer.fix(path, content);
          if (!newContent.equals(content)) Files.writeString(path, newContent);
        });
  }

  /// 移動したクラスを参照するimport文を、1ファイルずつ整合させる
  static final class ImportFixer {
    private final Set<String> movedClasses;

    private final String targetPackage;

    private final Path targetDirFullPath;

    /// 移動したクラス名 → 移動順
    private final Map<String, Integer> order = new HashMap<>();

    ImportFixer(Set<String> movedClasses, String targetPackage, Path targetDirFullPath) {
      this.movedClasses = movedClasses;
      this.targetPackage = targetPackage;
      this.targetDirFullPath = targetDirFullPath;
      for (String cls : movedClasses) order.put(cls, order.size());
    }

    /// 1ファイル分のimport文を書き換える
    ///
    /// 明示importの書き換え、import無しの参照へのimport追加、残ったパッケージ無しimportの削除の順に適用する。
    /// 移動先ディレクトリ内のファイルは移動したクラスと同じパッケージになるので、importを足さない。
    ///
    /// @param path 書き換えるファイル
    /// @param content ファイルの内容
    /// @return 書き換え後の内容
    String fix(Path path, String content) {
      boolean inTargetDir = path.toAbsolutePath().normalize().getParent().equals(targetDirFullPath);
      String s = rewriteExplicitImports(content, inTargetDir);
      if (!inTargetDir) s = addImplicitImports(s);
      return removeBareImports(s);
    }

    /// `import Foo;`を`import 移動先.Foo;`に書き換える。移動先ディレクトリ内のファイルでは同じパッケージになるため削除する。
    ///
    /// 移動クラス全件で検索すると「ファイル数×クラス数」回の検索になり、グローバル型が数千あるIDLでは極端に遅い。そのためファイル中に現れる`import X;`から逆に引く。
    ///
    /// @param content ファイルの内容
    /// @param inTargetDir ファイルが移動先ディレクトリ内にあるかどうか
    /// @return 書き換え後の内容
    String rewriteExplicitImports(String content, boolean inTargetDir) {
      Set<String> imported = new LinkedHashSet<>();
      Matcher m = ANY_SIMPLE_IMPORT.matcher(content);
      while (m.find()) imported.add(m.group(1));
      String s = content;
      for (String cls : imported) {
        if (!movedClasses.contains(cls)) continue;
        String bare = "import " + cls + ";";
        s =
            inTargetDir
                ? s.replace(bare + "\n", "").replace(bare + "\r\n", "").replace(bare, "")
                : s.replace(bare, "import " + targetPackage + "." + cls + ";");
      }
      return s;
    }

    /// import無しで移動したクラスを参照しているファイルに、完全修飾importを足す。idljの生成コードの一部は、デフォルトパッケージのクラスをimport無しで参照するため。
    ///
    /// ファイル中の識別子を一度だけ字句分割して集合にし、移動したクラス名と突き合わせる。importは移動順に挿入するので、package行の直後に積まれた結果は移動順の逆に並ぶ。
    ///
    /// @param content ファイルの内容
    /// @return 書き換え後の内容
    String addImplicitImports(String content) {
      Set<String> words = new HashSet<>();
      Matcher m = WORD.matcher(content);
      while (m.find()) words.add(m.group());
      List<String> referenced =
          words.stream()
              .filter(order::containsKey)
              .sorted(Comparator.comparing(order::get))
              .toList();
      String s = content;
      for (String cls : referenced) {
        String fqImport = "import " + targetPackage + "." + cls + ";";
        if (s.contains(fqImport)) continue;
        s = insertImport(s, fqImport);
      }
      return s;
    }
  }

  /// パッケージを持たない`import X;`をすべて削除する。前の手順で解決しきれずに残ったものの後始末。
  ///
  /// @param content ファイルの内容
  /// @return 削除後の内容
  static String removeBareImports(String content) {
    return BARE_IMPORT.matcher(content).replaceAll("");
  }

  /// import文をpackage宣言の直後に挿入する。package宣言が無ければ先頭に挿入する。
  ///
  /// @param content Javaソースコード
  /// @param importStatement 挿入するimport文
  /// @return 挿入後のソースコード
  static String insertImport(String content, String importStatement) {
    Matcher pkg = PACKAGE_LINE.matcher(content);
    if (pkg.find()) {
      int insertPos = pkg.end();
      return content.substring(0, insertPos)
          + importStatement
          + "\n"
          + content.substring(insertPos);
    }
    return importStatement + "\n" + content;
  }

  /// グローバルスコープのスタブの移動先パッケージを決める
  ///
  /// `idlPackagePrefix`が空でなければそれを使う。空なら既存スタブのpackage宣言の共通プレフィックスを使い、それも無ければ`generated`にする。
  ///
  /// @param stubRoot スタブのルートディレクトリ
  /// @param idlPackagePrefix `-pkgPrefix`に渡したパッケージプレフィックス
  /// @param excludeFiles パッケージ収集から除外するファイル（移動対象のファイル）
  /// @return 移動先パッケージ名
  /// @throws IOException スタブの読み込みに失敗した場合
  private static String resolveTargetPackage(
      Path stubRoot, String idlPackagePrefix, List<Path> excludeFiles) throws IOException {
    if (!idlPackagePrefix.isBlank()) return idlPackagePrefix;

    Set<Path> excludeSet = new HashSet<>();
    for (Path f : excludeFiles) excludeSet.add(f.toAbsolutePath().normalize());

    List<String> packages = new ArrayList<>();
    for (Path p : StubFiles.javaFiles(stubRoot)) {
      if (excludeSet.contains(p.toAbsolutePath().normalize())) continue;
      Matcher m = PACKAGE_NAME.matcher(Files.readString(p));
      if (m.find() && !packages.contains(m.group(1))) packages.add(m.group(1));
    }

    String common = packages.isEmpty() ? "" : packages.get(0);
    for (String pkg : packages) {
      common = packageCommonPrefix(common, pkg);
      if (common.isEmpty()) break;
    }
    return common.isEmpty() ? "generated" : common;
  }

  /// ドット区切りのパッケージ名の共通プレフィックスを返す（例: `com.a.x`と`com.a.y`なら`com.a`）
  ///
  /// @param a パッケージ名1
  /// @param b パッケージ名2
  /// @return 共通プレフィックス。共通部分が無ければ空文字列
  static String packageCommonPrefix(String a, String b) {
    String[] partsA = a.split("\\.");
    String[] partsB = b.split("\\.");
    List<String> common = new ArrayList<>();
    for (int i = 0; i < Math.min(partsA.length, partsB.length); i++) {
      if (!partsA[i].equals(partsB[i])) break;
      common.add(partsA[i]);
    }
    return String.join(".", common);
  }

  private static String className(Path file) {
    String name = file.getFileName().toString();
    return name.substring(0, name.lastIndexOf('.'));
  }

  private static boolean isJava(Path p) {
    return p.toString().endsWith(".java");
  }
}
