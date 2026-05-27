package com.example.parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Main {
  /**
   * Entry point.
   *
   * @param args コマンドライン引数
   */
  public static void main(String[] args) throws Exception {
    /**
     * CLI のエントリポイント
     *
     * <p>`gen-spring` コマンドをサポートし、次の処理を順に実行します 1. 指定された IDL ファイルから `idlj` を呼び出して Java スタブを生成 2.
     * 生成されたスタブからサービスインターフェースを収集 3. それぞれのサービスに対して Spring Boot プロジェクトを生成
     *
     * <p>コマンドラインオプションの取り扱い詳細は `printUsage` を参照してください
     */
    // if (args.length > 0 && "gen-spring".equals(args[0])) {
    Path idljPath = Path.of("tools", "idlj.jar");
    List<Path> idlFiles = new ArrayList<>();
    Path stubRoot = Path.of("generated-stubs");
    Path outputRoot = Path.of("generated-spring");
    String basePackage = "com.generated";
    String idlPackagePrefix = "";
    String returnMode = "dto"; // dto | raw
    boolean singleProject = false;
    String singleProjectName = "CombinedApi";

    for (String arg : args) {
      if (arg.startsWith("--stub-root=")) {
        stubRoot = Path.of(arg.substring("--stub-root=".length()));
      } else if (arg.startsWith("--output-root=")) {
        outputRoot = Path.of(arg.substring("--output-root=".length()));
      } else if (arg.startsWith("--base-package=")) {
        basePackage = arg.substring("--base-package=".length());
      } else if (arg.startsWith("--idl-package-prefix=")) {
        idlPackagePrefix = arg.substring("--idl-package-prefix=".length());
      } else if (arg.startsWith("--return-mode=")) {
        returnMode = arg.substring("--return-mode=".length());
      } else if (arg.equals("--single-project")) {
        singleProject = true;
      } else if (arg.startsWith("--project-name=")) {
        singleProjectName = arg.substring("--project-name=".length());
      } else if (arg.equals("--help") || arg.equals("-h")) {
        printUsage();
        return;
      } else {
        // オプション以外は全てidlファイル名として解釈する
        idlFiles.add(Path.of(arg));
      }
    }
    boolean returnAsDto = "dto".equalsIgnoreCase(returnMode) || returnMode.isBlank();

    // idljを用いて、全てのidlファイルからJavaスタブを生成する
    Files.createDirectories(stubRoot);
    for (Path idlFile : idlFiles) {
      try {
        List<String> packageTargets = collectIdlPackagePrefixTargets(idlFile);
        runIdlj(idljPath, idlFile.toAbsolutePath(), stubRoot, idlPackagePrefix, packageTargets);
      } catch (IOException | InterruptedException e) {
        e.printStackTrace();
        System.exit(1);
      }
    }
    // デフォルトパッケージに出力されたグローバルスコープのスタブを修正する
    relocateDefaultPackageStubs(stubRoot, idlPackagePrefix);

    // 生成されたスタブからサービスインターフェースを収集する
    List<ClassOrInterfaceDeclaration> services = collectServiceInterfaces(stubRoot);
    if (services.isEmpty()) {
      System.err.println("No Operations interfaces found in generated stubs.");
      System.exit(1);
    }

    if (singleProject) {
      // 全サービスを単一プロジェクトにまとめる
      try {
        Path output = outputRoot.resolve(singleProjectName);
        SpringBootProjectGenerator.generateAll(
            services, stubRoot, output, basePackage, returnAsDto);
        System.out.println("Spring project generated at: " + output);
      } catch (Exception e) {
        e.printStackTrace();
        System.exit(1);
      }
    } else {
      // サービスごとに個別プロジェクトを生成する
      for (ClassOrInterfaceDeclaration service : services) {
        try {
          String serviceName = service.getNameAsString();
          String shortName =
              serviceName.endsWith("Operations")
                  ? serviceName.substring(0, serviceName.length() - "Operations".length())
                  : serviceName;
          Path output = outputRoot.resolve(shortName + "Api");
          SpringBootProjectGenerator.generate(service, stubRoot, output, basePackage, returnAsDto);
          System.out.println("Spring project generated at: " + output);
        } catch (Exception e) {
          e.printStackTrace();
          System.exit(1);
        }
      }
    }

    return;
  }

  /** コマンドの使用方法を標準出力に表示する */
  private static void printUsage() {
    System.out.print(
        """
            Usage: [options] <idl-file>...

            Options:
              --stub-root=<path>          IDL生成Javaソースの出力先ディレクトリ (default: generated-stubs)
              --output-root=<path>        Springプロジェクト出力先ディレクトリ (default: generated-spring)
              --base-package=<package>    生成されるSpringプロジェクトのベースパッケージ (default: com.generated)
              --idl-package-prefix=<pkg>  IDLモジュール名に対してidljのパッケージプレフィックスを適用
              --help, -h                  このヘルプを表示

            """);
  }

  /**
   * idljによりIDLファイルからJavaスタブを生成する
   *
   * @param idljPath idljのjarファイルパス
   * @param idlFile 処理対象のIDLファイル（絶対パス）
   * @param stubRoot 生成スタブの出力先ルートディレクトリ
   * @param idlPackagePrefix {@code -pkgPrefix} に渡すパッケージプレフィックス
   * @param packageTargets {@code -pkgPrefix} のターゲット名一覧
   */
  private static void runIdlj(
      Path idljPath,
      Path idlFile,
      Path stubRoot,
      String idlPackagePrefix,
      List<String> packageTargets)
      throws IOException, InterruptedException {
    // java -jar idlj.jar -emitAll -I <dir> -client -pkgPrefix <packageTargets> -td
    // <stubRoot> <idlFile>
    // を発火するこのとき、idlが長いとコマンド長さ上限に引っかかるのでファイル引数として用いる
    Path argDir = stubRoot.resolve("idlj-args");
    Files.createDirectories(argDir);
    Path argFile = argDir.resolve(removeExtension(idlFile.getFileName().toString()) + ".args");

    String includeDir = idlFile.getParent() != null ? idlFile.getParent().toString() : ".";
    List<String> args =
        new ArrayList<>(
            List.of(
                "-jar",
                escapeArg(idljPath.toAbsolutePath().toString()),
                "-emitAll",
                "-I",
                escapeArg(includeDir),
                "-fclient"));

    if (!idlPackagePrefix.isBlank() && !packageTargets.isEmpty()) {
      for (String target : packageTargets) {
        args.addAll(List.of("-pkgPrefix", escapeArg(target), escapeArg(idlPackagePrefix)));
      }
    }

    args.addAll(
        List.of(
            "-td",
            escapeArg(stubRoot.toAbsolutePath().toString()),
            escapeArg(idlFile.toAbsolutePath().toString())));

    Files.write(
        argFile,
        args,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);

    Process process =
        new ProcessBuilder("java", "@" + argFile.toAbsolutePath())
            .directory(stubRoot.toFile())
            .inheritIO()
            .start();

    if (process.waitFor() != 0) {
      throw new IOException(
          "idlj failed for " + idlFile + " with exit code " + process.exitValue());
    }
  }

  /**
   * argfile に書き込む引数をエスケープする
   *
   * @param s エスケープ対象文字列
   * @return エスケープ済み文字列
   */
  private static String escapeArg(String s) {
    if (s.contains(" ") || s.contains("\"")) {
      return "\"" + s.replace("\"", "\\\"") + "\"";
    }
    return s;
  }

  /**
   * ファイル名から拡張子を除いたベース名を返す
   *
   * @param name ファイル名
   * @return 拡張子を除いたファイル名
   */
  private static String removeExtension(String name) {
    int idx = name.lastIndexOf('.');
    return idx >= 0 ? name.substring(0, idx) : name;
  }

  /**
   * 指定したIDLファイルとそのincludeファイルを解析し、`idlj -pkgPrefix`に渡すターゲット名を収集する
   *
   * @param idlFile ルートのIDLファイル
   * @return パッケージプレフィックスターゲットの一覧
   */
  private static List<String> collectIdlPackagePrefixTargets(Path idlFile) throws IOException {
    List<String> packageTargets = new ArrayList<>();
    Set<Path> includedFiles = new LinkedHashSet<>();
    collectIncludedFiles(idlFile, includedFiles);

    // 全ての関連idlファイルから`module`を読み取る
    Pattern modulePattern = Pattern.compile("\\bmodule\\s+([A-Za-z_][A-Za-z0-9_]*)");
    for (Path file : includedFiles) {
      Matcher modules = modulePattern.matcher(Files.readString(file));
      while (modules.find()) {
        String moduleName = modules.group(1);
        if (!packageTargets.contains(moduleName)) {
          packageTargets.add(moduleName);
        }
      }
    }
    if (!packageTargets.isEmpty()) {
      return packageTargets;
    }

    // idlにmodule定義が無い場合、トップレベルから型名を収集する
    Pattern typePattern =
        Pattern.compile("\\b(?:interface|struct|union|enum|exception)\\s+([A-Za-z_][A-Za-z0-9_]*)");
    for (Path file : includedFiles) {
      Matcher types = typePattern.matcher(Files.readString(file));
      while (types.find()) {
        String typeName = types.group(1);
        if (!packageTargets.contains(typeName)) {
          packageTargets.add(typeName);
        }
      }
    }
    return packageTargets;
  }

  /**
   * idlがincludeしているidlファイルを再帰的に解決して返す
   *
   * @param idlFile 処理対象のIDLファイル
   * @param collected includeファイルの集合
   */
  private static void collectIncludedFiles(Path idlFile, Set<Path> collected) throws IOException {
    if (idlFile == null) {
      return;
    }

    Path idlFileFullPath = idlFile.toAbsolutePath().normalize();
    if (collected.contains(idlFileFullPath) || !Files.exists(idlFileFullPath)) {
      return;
    }

    // `#include`を読み取る
    collected.add(idlFileFullPath);
    Pattern includePattern = Pattern.compile("#include\\s+\"([^\"]+)\"");
    Matcher includes = includePattern.matcher(Files.readString(idlFileFullPath));

    // 読み取ったincludeについて、相対PATHでファイルを探索する
    Path dir = idlFileFullPath.getParent();
    while (includes.find()) {
      String path = includes.group(1);
      Path resolved =
          (dir != null)
              ? dir.resolve(path).normalize()
              : Path.of(path).toAbsolutePath().normalize();

      collectIncludedFiles(resolved, collected);
    }
  }

  /**
   * idlj がデフォルトパッケージ（stubRoot 直下）に出力した .java ファイルを 適切なパッケージへ移動し、それを参照している import 文を更新する
   *
   * <p>IDL の仕様上、グローバルスコープで定義された typedef sequence は モジュール外のデフォルトパッケージに出力されるため、名前付きパッケージから import
   * できないこのメソッドはその問題を後処理として修正する
   *
   * @param stubRoot idlj の出力先ルートディレクトリ
   * @param idlPackagePrefix -pkgPrefix に渡したパッケージプレフィックス（空の場合は自動推定）
   */
  private static void relocateDefaultPackageStubs(Path stubRoot, String idlPackagePrefix)
      throws IOException {
    // グローバルに直接出力されたファイルを収集し、クラス名とPATHのマップを作る
    List<Path> defaultPkgFiles;
    try (var stream = Files.list(stubRoot)) {
      defaultPkgFiles =
          stream
              .filter(Files::isRegularFile)
              .filter(p -> p.toString().endsWith(".java"))
              .collect(Collectors.toList());
    }
    if (defaultPkgFiles.isEmpty()) return;
    Map<String, Path> classToFile = new LinkedHashMap<>();
    for (Path f : defaultPkgFiles) {
      classToFile.put(removeExtension(f.getFileName().toString()), f);
    }

    // package宣言を付けて移動先に書き出し、元ファイルを削除する
    String targetPackage = resolveGlobalStubPackage(stubRoot, idlPackagePrefix, defaultPkgFiles);
    Path targetDir = stubRoot.resolve(targetPackage.replace('.', '/'));
    Files.createDirectories(targetDir);
    for (Map.Entry<String, Path> e : classToFile.entrySet()) {
      Path src = e.getValue();
      Files.writeString(
          targetDir.resolve(src.getFileName()),
          "package " + targetPackage + ";\n\n" + Files.readString(src),
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING);
      Files.delete(src);
    }

    Set<String> movedClasses = classToFile.keySet();
    Path targetDirFullPath = targetDir.toAbsolutePath().normalize();

    // インポート文を完全修飾名へ置換
    rewriteStubs(
        stubRoot,
        p -> true,
        (path, content) -> {
          String s = content;
          for (String cls : movedClasses) {
            String bare = "import " + cls + ";";
            if (!s.contains(bare)) {
              continue;
            }

            s =
                path.toAbsolutePath().normalize().getParent().equals(targetDirFullPath)
                    ? s.replace(bare + "\n", "").replace(bare + "\r\n", "").replace(bare, "")
                    : s.replace(bare, "import " + targetPackage + "." + cls + ";");
          }
          return s;
        });

    // 暗黙的にクラス名を参照しているファイルにimportを追加
    rewriteStubs(
        stubRoot,
        p -> !p.toAbsolutePath().normalize().getParent().equals(targetDirFullPath),
        (path, content) -> {
          String s = content;
          for (String cls : movedClasses) {
            String fqImport = "import " + targetPackage + "." + cls + ";";
            if (s.contains(fqImport)) continue;
            if (!Pattern.compile("\\b" + Pattern.quote(cls) + "\\b").matcher(s).find()) continue;
            s = insertImport(s, fqImport);
          }
          return s;
        });

    // ベアインポートを削除
    Pattern bareImport =
        Pattern.compile(
            "^import [A-Za-z_][A-Za-z0-9_$]*;[ \\t]*(?:\\r\\n|\\r|\\n)?", Pattern.MULTILINE);
    rewriteStubs(
        stubRoot, p -> true, (path, content) -> bareImport.matcher(content).replaceAll(""));
  }

  /**
   * 指定ディレクトリ内のJavaファイルに対して、rewriterを適用する
   *
   * @param root 走査対象ディレクトリ
   * @param filter 処理対象を絞り込む条件
   * @param rewriter ファイルパスと現在の内容を受け取り、書き換え後の内容を返す関数
   */
  private static void rewriteStubs(
      Path root, Predicate<Path> filter, BiFunction<Path, String, String> rewriter)
      throws IOException {
    Files.walk(root)
        .filter(Files::isRegularFile)
        .filter(path -> path.toString().endsWith(".java"))
        .filter(filter)
        .forEach(
            path -> {
              try {
                String content = Files.readString(path);
                String newContent = rewriter.apply(path, content);
                if (!newContent.equals(content)) {
                  Files.writeString(path, newContent, StandardOpenOption.TRUNCATE_EXISTING);
                }
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            });
  }

  /**
   * import文を挿入する
   *
   * @param content Javaソースコード
   * @param importStatement 挿入するimport文
   * @return 挿入後ソースコード
   */
  private static String insertImport(String content, String importStatement) {
    Pattern pkgPattern =
        Pattern.compile("^package\\s+[\\w.]+\\s*;[ \\t]*(?:\\r\\n|\\r|\\n)", Pattern.MULTILINE);
    Matcher pkgs = pkgPattern.matcher(content);
    if (pkgs.find()) {
      int insertPos = pkgs.end();
      return content.substring(0, insertPos)
          + importStatement
          + "\n"
          + content.substring(insertPos);
    }
    return importStatement + "\n" + content;
  }

  /**
   * グローバルスコープスタブの移動先パッケージを決定する 優先順位は
   *
   * <p>1. idlPackagePrefixが空でなければそれ 2. 既存スタブのpackage宣言から共通プレフィックスを求めて使用 3. 上記が得られなければgenerated
   *
   * @param stubRoot スタブのディレクトリ
   * @param idlPackagePrefix コマンド引数の-pkgPrefix
   * @param excludeFiles パッケージ収集から除外するファイル
   * @return 移動先パッケージ名
   */
  private static String resolveGlobalStubPackage(
      Path stubRoot, String idlPackagePrefix, List<Path> excludeFiles) throws IOException {
    if (!idlPackagePrefix.isBlank()) return idlPackagePrefix;

    Set<Path> excludeSet =
        excludeFiles.stream().map(f -> f.toAbsolutePath().normalize()).collect(Collectors.toSet());

    List<String> packages = new ArrayList<>();
    Pattern pkgPat = Pattern.compile("^package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    Files.walk(stubRoot)
        .filter(Files::isRegularFile)
        .filter(p -> p.toString().endsWith(".java"))
        .filter(p -> !excludeSet.contains(p.toAbsolutePath().normalize()))
        .forEach(
            p -> {
              try {
                Matcher m = pkgPat.matcher(Files.readString(p));
                if (m.find()) {
                  String pkg = m.group(1);
                  if (!packages.contains(pkg)) packages.add(pkg);
                }
              } catch (IOException e) {
                // pass
              }
            });

    if (!packages.isEmpty()) {
      String common = packages.get(0);
      for (int i = 1; i < packages.size(); i++) {
        common = packageCommonPrefix(common, packages.get(i));
        if (common.isEmpty()) break;
      }
      if (!common.isEmpty()) return common;
    }

    return "generated";
  }

  /**
   * ドット区切りパッケージ名の共通プレフィックスを返す
   *
   * @param a パッケージ名１
   * @param b パッケージ名２
   * @return 共通プレフィックス
   */
  private static String packageCommonPrefix(String a, String b) {
    String[] partsA = a.split("\\.");
    String[] partsB = b.split("\\.");
    List<String> common = new ArrayList<>();
    for (int i = 0; i < Math.min(partsA.length, partsB.length); i++) {
      if (partsA[i].equals(partsB[i])) {
        common.add(partsA[i]);
      } else {
        break;
      }
    }
    return String.join(".", common);
  }

  /**
   * スタブソースから`*Operations`という名前のサービスインターフェースを収集する
   *
   * @param stubRoot スタブソースディレクトリ
   * @return サービスインターフェース定義一覧
   */
  private static List<ClassOrInterfaceDeclaration> collectServiceInterfaces(Path stubRoot)
      throws IOException {
    List<ClassOrInterfaceDeclaration> services = new ArrayList<>();
    Files.walk(stubRoot)
        .filter(p -> p.toString().endsWith(".java"))
        .forEach(
            path -> {
              try {
                CompilationUnit cu = StaticJavaParser.parse(path);
                cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                    .filter(c -> c.getNameAsString().endsWith("Operations"))
                    .forEach(services::add);
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            });
    return services;
  }
}
