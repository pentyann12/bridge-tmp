package com.toru_otsuki.bridge_generator.stub;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// idljが生成したスタブを一度だけパースして保持する索引
///
/// サービスインターフェース・構造体クラスの列挙と、完全修飾名によるクラス宣言の検索を提供する。列挙順はスタブディレクトリの走査順。
public final class StubIndex {
  /// 全スタブのコンパイル単位（走査順）
  private final List<CompilationUnit> units;

  /// 完全修飾名 → トップレベルのクラス宣言
  private final Map<String, ClassOrInterfaceDeclaration> byFqn = new HashMap<>();

  private StubIndex(List<CompilationUnit> units) {
    this.units = units;
    for (CompilationUnit cu : units) {
      String pkg = packageOf(cu);
      for (var type : cu.getTypes()) {
        if (type instanceof ClassOrInterfaceDeclaration c) {
          byFqn.putIfAbsent(
              pkg.isEmpty() ? c.getNameAsString() : pkg + "." + c.getNameAsString(), c);
        }
      }
    }
  }

  /// スタブディレクトリ以下の全.javaファイルをパースする
  ///
  /// 大きなIDLではスタブが1万ファイルに及ぶため並列にパースする。JavaParserはスレッドセーフでないので、パーサーはスレッドごとに持つ。
  ///
  /// @param stubRoot スタブのルートディレクトリ
  /// @return 索引
  /// @throws IOException スタブの読み込みに失敗した場合
  /// @throws ParseProblemException スタブのパースに失敗した場合
  public static StubIndex load(Path stubRoot) throws IOException {
    ThreadLocal<JavaParser> parser =
        ThreadLocal.withInitial(() -> new JavaParser(new ParserConfiguration()));
    try {
      List<CompilationUnit> units =
          StubFiles.javaFiles(stubRoot).parallelStream()
              .map(path -> parse(parser.get(), path))
              .toList();
      return new StubIndex(units);
    } catch (UncheckedIOException e) {
      throw e.getCause();
    }
  }

  private static CompilationUnit parse(JavaParser parser, Path path) {
    try {
      ParseResult<CompilationUnit> result = parser.parse(path);
      return result.getResult().orElseThrow(() -> new ParseProblemException(result.getProblems()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /// サービスインターフェース（名前が`Operations`で終わるもの）を列挙する
  ///
  /// @return サービスインターフェースの宣言（走査順）
  public List<ClassOrInterfaceDeclaration> services() {
    List<ClassOrInterfaceDeclaration> services = new ArrayList<>();
    for (CompilationUnit cu : units) {
      cu.findAll(ClassOrInterfaceDeclaration.class).stream()
          .filter(c -> c.getNameAsString().endsWith("Operations"))
          .forEach(services::add);
    }
    return services;
  }

  public List<ClassOrInterfaceDeclaration> structs() {
    List<ClassOrInterfaceDeclaration> structs = new ArrayList<>();
    for (CompilationUnit cu : units) {
      cu.findAll(ClassOrInterfaceDeclaration.class).stream()
          .filter(StubIndex::isTargetStruct)
          .forEach(structs::add);
    }
    return structs;
  }

  Optional<ClassOrInterfaceDeclaration> find(String fqn) {
    return Optional.ofNullable(byFqn.get(fqn));
  }

  /// DTO/Mapperを生成する構造体クラスかどうかを判定する
  ///
  /// idljのstructクラスはフィールドとコンストラクタだけを持つので、インターフェース・idljの補助クラス（Helper/Holder/POA等）・メソッドを持つクラスを除いたものを構造体とみなす。
  ///
  /// @param clazz 判定対象のクラス宣言
  /// @return 構造体クラスであればtrue
  private static boolean isTargetStruct(ClassOrInterfaceDeclaration clazz) {
    if (clazz.isInterface()) return false;
    String name = clazz.getNameAsString();
    return !name.endsWith("Helper")
        && !name.endsWith("Holder")
        && !name.endsWith("Operations")
        && !name.endsWith("POA")
        && !name.endsWith("Stub")
        && !name.startsWith("_")
        && clazz.getMethods().isEmpty();
  }

  static String packageOf(CompilationUnit cu) {
    return cu.getPackageDeclaration().map(PackageDeclaration::getNameAsString).orElse("");
  }
}
