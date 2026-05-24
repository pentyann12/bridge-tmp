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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * IDL から Spring Boot 生成を行う CLI のエントリポイント。
 *
 * <p>
 * `gen-spring` の引数を解釈し、`idlj` を呼び出して Java スタブを生成し、
 * 生成されたソースからサービスインターフェースを検出して Spring Boot
 * プロジェクトを生成する。
 * </p>
 */
public class Main {

    /**
     * アプリケーションのエントリポイント。
     *
     * @param args `gen-spring [options] <idl-file>...` 形式のコマンドライン引数。
     * @throws Exception ファイル入出力や外部プロセス実行でエラーが発生した場合。
     */
    public static void main(String[] args) throws Exception {

        /**
         * CLI のエントリポイント。
         *
         * `gen-spring` コマンドをサポートし、次の処理を順に実行します。
         * 1. 指定された IDL ファイルから `idlj` を呼び出して Java スタブを生成
         * 2. 生成されたスタブからサービスインターフェースを収集
         * 3. それぞれのサービスに対して Spring Boot プロジェクトを生成
         *
         * コマンドラインオプションの取り扱い詳細は `printUsage` を参照してください。
         */

        if (args.length > 0 && "gen-spring".equals(args[0])) {
            Path idljJar = Path.of("tools", "idlj.jar");
            // if (!Files.exists(idljJar)) {
            // System.err.println("tools/idlj.jar not found. Place idlj.jar in the tools/
            // directory.");
            // System.exit(1);
            // }
            // if (args.length < 2) {
            // System.err.println("Please specify one or more .idl files to generate
            // from.");
            // System.exit(1);
            // }

            List<Path> idlFiles = new ArrayList<>();
            Path stubRoot = Path.of("generated-stubs");
            Path outputRoot = Path.of("generated-spring");
            String basePackage = "com.generated";
            String idlPackagePrefix = "";
            String returnMode = "dto"; // dto | raw
            boolean singleProject = false;
            String singleProjectName = "CombinedApi";

            for (int i = 1; i < args.length; i++) {
                String arg = args[i];
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
                    idlFiles.add(Path.of(arg));
                }
            }

            // if (idlFiles.isEmpty()) {
            // System.err.println("Please specify one or more .idl files to generate
            // from.");
            // System.exit(1);
            // }

            Files.createDirectories(stubRoot);

            // idljを用いてIDLファイルごとにJavaスタブを生成する
            for (Path idlFile : idlFiles) {
                try {
                    List<String> packageTargets = collectIdlPackagePrefixTargets(idlFile);
                    runIdlj(idljJar, idlFile.toAbsolutePath(), stubRoot, idlPackagePrefix, packageTargets);
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

            boolean returnAsDto = "dto".equalsIgnoreCase(returnMode) || returnMode.isBlank();

            if (singleProject) {
                // 全サービスを単一プロジェクトにまとめる
                try {
                    Path output = outputRoot.resolve(singleProjectName);
                    SpringBootProjectGenerator.generateAll(services, stubRoot, output, basePackage, returnAsDto);
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
                        String shortName = serviceName.endsWith("Operations")
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
    }

    /**
     * コマンドの使用方法を標準出力に表示する。
     */
    private static void printUsage() {
        System.out.println("Usage: gen-spring [options] <idl-file>...\n");
        System.out.println("Options:");
        System.out.println("  --stub-root=<path>          IDL生成Javaソースの出力先ディレクトリ (default: generated-stubs)");
        System.out.println("  --output-root=<path>        Springプロジェクト出力先ディレクトリ (default: generated-spring)");
        System.out.println("  --base-package=<package>    生成されるSpringプロジェクトのベースパッケージ (default: com.generated)");
        System.out.println("  --idl-package-prefix=<pkg>  IDLモジュール名に対してidljのパッケージプレフィックスを適用");
        System.out.println("  --help, -h                  このヘルプを表示");
        System.out.println();
    }

    private static void runIdlj(
            Path idljJar,
            Path idlFile,
            Path stubRoot,
            String idlPackagePrefix,
            List<String> packageTargets)
            throws IOException, InterruptedException {

        Path includeDir = idlFile.getParent();

        /*
         * デバッグ用:
         * argfile を build/idlj-args/ に保存する
         */
        Path argDir = stubRoot.resolve("idlj-args");
        Files.createDirectories(argDir);

        String baseName = removeExtension(idlFile.getFileName().toString());

        Path argFile = argDir.resolve(baseName + ".args");

        List<String> args = new ArrayList<>();

        args.add("-jar");
        args.add(escapeArg(idljJar.toAbsolutePath().toString()));

        args.add("-emitAll");

        args.add("-I");
        args.add(escapeArg(includeDir != null ? includeDir.toString() : "."));

        args.add("-fclient");

        if (!idlPackagePrefix.isBlank() && !packageTargets.isEmpty()) {
            for (String target : packageTargets) {
                args.add("-pkgPrefix");
                args.add(escapeArg(target));
                args.add(escapeArg(idlPackagePrefix));
            }
        }

        args.add("-td");
        args.add(escapeArg(stubRoot.toAbsolutePath().toString()));

        args.add(escapeArg(idlFile.toAbsolutePath().toString()));

        /*
         * UTF-8 で保存
         */
        Files.write(
                argFile,
                args,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        System.out.println("idlj argfile: " + argFile.toAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(
                "java",
                "@" + argFile.toAbsolutePath());

        pb.directory(stubRoot.toFile());
        pb.inheritIO();

        Process process = pb.start();

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IOException(
                    "idlj failed for " + idlFile + " with exit code " + exitCode);
        }
    }

    private static String escapeArg(String s) {
        if (s.contains(" ") || s.contains("\"")) {
            return "\"" + s.replace("\"", "\\\"") + "\"";
        }
        return s;
    }

    private static String removeExtension(String name) {
        int idx = name.lastIndexOf('.');
        return idx >= 0 ? name.substring(0, idx) : name;
    }

    /**
     * 指定したIDLファイルとそのincludeファイルを解析し、`idlj -pkgPrefix` に渡すターゲット名を収集する。
     *
     * @param idlFile ルートの IDL ファイル。
     * @return IDL ファイル群から検出したパッケージプレフィックスターゲットの一覧。
     * @throws IOException IDL ファイルの読み取りに失敗した場合。
     */
    private static List<String> collectIdlPackagePrefixTargets(Path idlFile) throws IOException {
        List<String> packageTargets = new ArrayList<>();
        Set<Path> files = new LinkedHashSet<>();
        // collectIncludedFiles は include 参照の推移的閉包を構築する
        collectIncludedFiles(idlFile, files);

        // まず全ファイルを検索して module 名を見つける
        Pattern modulePat = Pattern.compile("\\bmodule\\s+([A-Za-z_][A-Za-z0-9_]*)");
        for (Path p : files) {
            String content = Files.readString(p);
            Matcher m = modulePat.matcher(content);
            while (m.find()) {
                String moduleName = m.group(1);
                if (!packageTargets.contains(moduleName))
                    packageTargets.add(moduleName);
            }
        }

        if (!packageTargets.isEmpty())
            return packageTargets;

        // フォールバック: トップレベル型名を収集する
        Pattern typePat = Pattern.compile("\\b(?:interface|struct|union|enum|exception)\\s+([A-Za-z_][A-Za-z0-9_]*)");
        for (Path p : files) {
            String content = Files.readString(p);
            Matcher m = typePat.matcher(content);
            while (m.find()) {
                String typeName = m.group(1);
                if (!packageTargets.contains(typeName))
                    packageTargets.add(typeName);
            }
        }

        return packageTargets;
    }

    /**
     * `#include "..."` を再帰的に解決し、参照された IDL ファイルを収集する。
     *
     * @param file      処理対象の IDL ファイル。
     * @param collected 収集済みファイルのパスを保持する集合。
     * @throws IOException 参照先ファイルの読み取りに失敗した場合。
     */
    private static void collectIncludedFiles(Path file, Set<Path> collected) throws IOException {
        if (file == null)
            return;
        Path abs = file.toAbsolutePath().normalize();
        if (collected.contains(abs) || !Files.exists(abs))
            return;
        collected.add(abs);
        String content = Files.readString(abs);
        Matcher inc = Pattern.compile("#include\\s+\"([^\"]+)\"").matcher(content);
        Path dir = abs.getParent();
        while (inc.find()) {
            String rel = inc.group(1);
            Path resolved = dir != null ? dir.resolve(rel).normalize() : Path.of(rel).toAbsolutePath().normalize();
            collectIncludedFiles(resolved, collected);
        }
    }

    /**
     * idlj がデフォルトパッケージ（stubRoot 直下）に出力した .java ファイルを
     * 適切なパッケージへ移動し、それを参照している import 文を更新する。
     *
     * IDL の仕様上、グローバルスコープで定義された typedef sequence は
     * モジュール外のデフォルトパッケージに出力されるため、名前付きパッケージから
     * import できない。このメソッドはその問題を後処理として修正する。
     *
     * @param stubRoot        idlj の出力先ルートディレクトリ。
     * @param idlPackagePrefix -pkgPrefix に渡したパッケージプレフィックス（空の場合は自動推定）。
     */
    private static void relocateDefaultPackageStubs(Path stubRoot, String idlPackagePrefix) throws IOException {
        List<Path> defaultPkgFiles;
        try (var stream = Files.list(stubRoot)) {
            defaultPkgFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }

        if (defaultPkgFiles.isEmpty()) {
            return;
        }

        Map<String, Path> classToFile = new LinkedHashMap<>();
        for (Path file : defaultPkgFiles) {
            classToFile.put(removeExtension(file.getFileName().toString()), file);
        }

        String targetPackage = resolveGlobalStubPackage(stubRoot, idlPackagePrefix, defaultPkgFiles);
        Path targetDir = stubRoot.resolve(targetPackage.replace('.', '/'));
        Files.createDirectories(targetDir);

        System.out.println("Relocating " + defaultPkgFiles.size()
                + " global-scope stub(s) to package: " + targetPackage);

        // パッケージ宣言を追加してファイルを移動する
        for (Map.Entry<String, Path> entry : classToFile.entrySet()) {
            Path src = entry.getValue();
            String content = Files.readString(src);
            String newContent = "package " + targetPackage + ";\n\n" + content;
            Path dst = targetDir.resolve(src.getFileName());
            Files.writeString(dst, newContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.delete(src);
        }

        // 第1パス: 全スタブファイルの import 文を修正する
        // targetDir 内のファイル（移動先と同一パッケージ）: "import ClassName;" を行ごと削除
        // targetDir 外のファイル: "import ClassName;" → "import <targetPackage>.ClassName;" に書き換え
        Set<String> movedClasses = classToFile.keySet();
        Path targetDirAbs = targetDir.toAbsolutePath().normalize();
        Files.walk(stubRoot)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        boolean inTargetDir = path.toAbsolutePath().normalize().getParent().equals(targetDirAbs);
                        String content = Files.readString(path);
                        boolean modified = false;
                        for (String className : movedClasses) {
                            String oldImport = "import " + className + ";";
                            if (!content.contains(oldImport)) continue;
                            if (inTargetDir) {
                                // 同一パッケージなので import 行ごと削除する
                                content = content.replace(oldImport + "\n", "")
                                                 .replace(oldImport + "\r\n", "")
                                                 .replace(oldImport, "");
                            } else {
                                content = content.replace(oldImport,
                                        "import " + targetPackage + "." + className + ";");
                            }
                            modified = true;
                        }
                        if (modified) {
                            Files.writeString(path, content, StandardOpenOption.TRUNCATE_EXISTING);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

        // 第2パス: import 文なしでクラス名を参照しているファイルに import を追加する
        // idlj はデフォルトパッケージのクラスを同一パッケージとして扱うため、
        // 移動先から参照されるクラスの import が生成されないことがある
        Files.walk(stubRoot)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                // 移動先パッケージ直下のファイルは同一パッケージなので import 不要
                .filter(p -> !p.toAbsolutePath().normalize().getParent().equals(targetDirAbs))
                .forEach(path -> {
                    try {
                        String content = Files.readString(path);
                        boolean modified = false;
                        for (String className : movedClasses) {
                            String fullImport = "import " + targetPackage + "." + className + ";";
                            if (content.contains(fullImport)) {
                                continue;
                            }
                            Pattern refPat = Pattern.compile("\\b" + Pattern.quote(className) + "\\b");
                            if (!refPat.matcher(content).find()) {
                                continue;
                            }
                            content = insertImport(content, fullImport);
                            modified = true;
                        }
                        if (modified) {
                            Files.writeString(path, content, StandardOpenOption.TRUNCATE_EXISTING);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

        // 第3パス: bare import（ドットなしの "import Xxx;"）を全スタブから削除する
        // リロケーション後はデフォルトパッケージにクラスが存在しないため、
        // idljがsequence typedef名に対して生成した対応クラスのないimportも含めて全て無効となる
        Pattern bareImportPat = Pattern.compile(
                "^import [A-Za-z_][A-Za-z0-9_$]*;[ \\t]*(?:\\r\\n|\\r|\\n)?",
                Pattern.MULTILINE);
        Files.walk(stubRoot)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        String content = Files.readString(path);
                        String newContent = bareImportPat.matcher(content).replaceAll("");
                        if (!newContent.equals(content)) {
                            Files.writeString(path, newContent, StandardOpenOption.TRUNCATE_EXISTING);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    /**
     * Java ソース文字列の package 宣言直後に import 文を挿入する。
     *
     * @param content         Java ソース全文
     * @param importStatement 挿入する import 文（例: "import com.pkg.Foo;"）
     * @return import 文が挿入された Java ソース全文
     */
    private static String insertImport(String content, String importStatement) {
        Pattern pkgPat = Pattern.compile("^package\\s+[\\w.]+\\s*;[ \\t]*(?:\\r\\n|\\r|\\n)", Pattern.MULTILINE);
        Matcher m = pkgPat.matcher(content);
        if (m.find()) {
            int insertPos = m.end();
            return content.substring(0, insertPos) + importStatement + "\n" + content.substring(insertPos);
        }
        return importStatement + "\n" + content;
    }

    /**
     * グローバルスコープスタブの移動先パッケージを決定する。
     *
     * <p>優先順位：
     * <ol>
     *   <li>{@code idlPackagePrefix} が空でなければそれを使用する。</li>
     *   <li>既存スタブのパッケージ名から共通プレフィックスを求めて使用する。</li>
     *   <li>上記が得られなければ {@code "generated"} を使用する。</li>
     * </ol>
     */
    private static String resolveGlobalStubPackage(
            Path stubRoot, String idlPackagePrefix, List<Path> excludeFiles) throws IOException {
        if (!idlPackagePrefix.isBlank()) {
            return idlPackagePrefix;
        }

        Set<Path> excludeSet = new LinkedHashSet<>();
        for (Path f : excludeFiles) {
            excludeSet.add(f.toAbsolutePath().normalize());
        }

        List<String> packages = new ArrayList<>();
        Pattern pkgPat = Pattern.compile("^package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
        Files.walk(stubRoot)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !excludeSet.contains(p.toAbsolutePath().normalize()))
                .forEach(p -> {
                    try {
                        Matcher m = pkgPat.matcher(Files.readString(p));
                        if (m.find()) {
                            String pkg = m.group(1);
                            if (!packages.contains(pkg)) {
                                packages.add(pkg);
                            }
                        }
                    } catch (IOException e) {
                        // 読み取り失敗は無視して続行する
                    }
                });

        if (!packages.isEmpty()) {
            String common = packages.get(0);
            for (String pkg : packages) {
                common = packageCommonPrefix(common, pkg);
                if (common.isEmpty()) break;
            }
            if (!common.isEmpty()) {
                return common;
            }
        }

        return "generated";
    }

    /**
     * ドット区切りパッケージ名の共通プレフィックスを返す。
     * 例: {@code "com.example.a"} と {@code "com.example.b"} → {@code "com.example"}
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
     * 生成済みスタブソースから `*Operations` という名前のサービスインターフェースを収集する。
     *
     * @param stubRoot idlj 生成済み Java ソースを含むディレクトリ。
     * @return スタブルート以下で見つかったサービスインターフェース定義の一覧。
     * @throws IOException スタブディレクトリの走査やファイル読み取りに失敗した場合。
     */
    private static List<ClassOrInterfaceDeclaration> collectServiceInterfaces(Path stubRoot) throws IOException {
        List<ClassOrInterfaceDeclaration> services = new ArrayList<>();
        Files.walk(stubRoot)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(path -> {
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
