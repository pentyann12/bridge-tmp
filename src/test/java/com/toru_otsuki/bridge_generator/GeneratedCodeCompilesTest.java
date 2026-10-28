package com.toru_otsuki.bridge_generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// 生成したプロジェクトのJavaソースが実際にコンパイルできることを確かめる
///
/// 起動クラス（Application・ServletInitializer）以外の全ソース（スタブ・DTO・Mapper・CorbaClient・Controller、共有ライブラリではクライアントと自動設定）を、
/// 生成コードが使うAPI（spring-web・spring-context・Spring Bootの自動設定・Jackson・CORBA API）だけをクラスパスに載せてコンパイルする。
class GeneratedCodeCompilesTest {
  /// Spring Bootの依存が要るためコンパイル対象から外すファイル
  private static final Set<String> SPRING_BOOT_ONLY =
      Set.of("Application.java", "ServletInitializer.java");

  /// 全サービスを1プロジェクトにまとめた場合に、DTO変換の有無それぞれでコンパイルできることを確かめる
  ///
  /// @param returnMode `--return-mode`の値
  /// @param tmp 作業ディレクトリ
  /// @throws Exception 生成やコンパイルの準備に失敗した場合
  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"dto", "raw"})
  void singleProjectCompiles(String returnMode, @TempDir Path tmp) throws Exception {
    TestGeneration.Result result =
        TestGeneration.run(
            tmp,
            "--single-project",
            "--idl-package-prefix=com.corba",
            "--return-mode=" + returnMode,
            TestGeneration.COVERAGE_IDL.toString());
    assertCompiles(result.singleProject(), tmp.resolve("classes"));
  }

  /// サービスごとにプロジェクトを分けた場合に、各プロジェクトがコンパイルでき、操作のないサービスにはControllerを作らないことを確かめる
  ///
  /// @param tmp 作業ディレクトリ
  /// @throws Exception 生成やコンパイルの準備に失敗した場合
  @Test
  void perServiceProjectsCompile(@TempDir Path tmp) throws Exception {
    TestGeneration.Result result = TestGeneration.run(tmp, TestGeneration.COVERAGE_IDL.toString());

    assertEquals(
        Set.of("ItemServiceApi", "EmptyServiceApi", "ClockServiceApi"),
        result.projects().stream()
            .map(p -> p.getFileName().toString())
            .collect(Collectors.toSet()));
    for (Path project : result.projects()) {
      Path controllers = project.resolve("src/main/java/com/generated/controller");
      boolean empty = project.getFileName().toString().equals("EmptyServiceApi");
      try (Stream<Path> list = Files.list(controllers)) {
        assertEquals(empty, list.findAny().isEmpty(), project.toString());
      }
      assertCompiles(project, tmp.resolve("classes-" + project.getFileName()));
    }
  }

  /// 共有ライブラリのソース（Spring Bootの自動設定を含む）がコンパイルできることを確かめる
  ///
  /// @param tmp 作業ディレクトリ
  /// @throws Exception 生成やコンパイルの準備に失敗した場合
  @Test
  void libraryCompiles(@TempDir Path tmp) throws Exception {
    TestGeneration.Result result =
        TestGeneration.run(
            tmp,
            "--target=library",
            "--idl-package-prefix=com.corba",
            TestGeneration.COVERAGE_IDL.toString(),
            TestGeneration.ERRORS_IDL.toString());
    assertCompiles(result.singleProject(), tmp.resolve("classes"));
  }

  /// プロジェクトのJavaソースをコンパイルし、エラーがあればその内容を添えて失敗させる
  ///
  /// @param project プロジェクトのディレクトリ
  /// @param classesDir クラスファイルの出力先
  /// @throws Exception コンパイルの準備に失敗した場合
  private static void assertCompiles(Path project, Path classesDir) throws Exception {
    List<Path> sources;
    try (Stream<Path> walk = Files.walk(project.resolve("src/main/java"))) {
      sources =
          walk.filter(p -> p.toString().endsWith(".java"))
              .filter(p -> !SPRING_BOOT_ONLY.contains(p.getFileName().toString()))
              .toList();
    }
    assertFalse(sources.isEmpty());
    Files.createDirectories(classesDir);

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, null)) {
      boolean ok =
          compiler
              .getTask(
                  new StringWriter(),
                  files,
                  diagnostics,
                  List.of(
                      "-classpath",
                      System.getProperty("java.class.path"),
                      "-d",
                      classesDir.toString(),
                      "-encoding",
                      "UTF-8",
                      "-nowarn",
                      "-proc:none"),
                  null,
                  files.getJavaFileObjectsFromPaths(sources))
              .call();
      String errors =
          diagnostics.getDiagnostics().stream()
              .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
              .map(Object::toString)
              .collect(Collectors.joining("\n"));
      assertTrue(ok, () -> project + " のコンパイルに失敗しました:\n" + errors);
    }
  }
}
