package com.toru_otsuki.bridge_generator.stub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// [StubRelocator]のテスト
class StubRelocatorTest {
  /// 移動先ディレクトリ（`com.corba`パッケージ）。ファイルは作らず、パスの判定にだけ使う。
  private static final Path TARGET_DIR = Path.of("/stubs/com/corba").toAbsolutePath().normalize();

  /// `com.corba`へ移動したことにした書き換え器を作る
  ///
  /// @param moved 移動したクラス名（移動順）
  /// @return 書き換え器
  private static StubRelocator.ImportFixer fixer(String... moved) {
    return new StubRelocator.ImportFixer(
        new LinkedHashSet<>(List.of(moved)), "com.corba", TARGET_DIR);
  }

  /// 移動先以外のファイルでは、`import Names;`を完全修飾importに書き換えることを確かめる
  @Test
  void rewritesBareImportOutsideTargetPackage() {
    String fixed =
        fixer("Names")
            .fix(
                Path.of("/stubs/M/S.java"),
                """
                package M;

                import Names;

                class S { Names n; }
                """);
    assertEquals(
        """
        package M;

        import com.corba.Names;

        class S { Names n; }
        """,
        fixed);
  }

  /// 移動先のファイルでは同じパッケージになるので、`import Names;`を削除することを確かめる
  @Test
  void dropsBareImportInsideTargetPackage() {
    String fixed =
        fixer("Names")
            .fix(
                TARGET_DIR.resolve("NamesHelper.java"),
                """
                package com.corba;

                import Names;
                class NamesHelper { }
                """);
    assertEquals(
        """
        package com.corba;

        class NamesHelper { }
        """,
        fixed);
  }

  /// import無しの参照には、単語として一致するクラスだけimportを足すことを確かめる。package行の直後に移動順で挿入するので、結果は移動順の逆に並ぶ。
  @Test
  void addsImportsForImplicitReferencesInMovedOrder() {
    String fixed =
        fixer("A", "B", "Unused")
            .fix(
                Path.of("/stubs/M/S.java"),
                """
                package M;
                class S { B b; A a; AB notA; }
                """);
    assertEquals(
        """
        package M;
        import com.corba.B;
        import com.corba.A;
        class S { B b; A a; AB notA; }
        """,
        fixed);
  }

  /// 解決しきれずに残ったパッケージ無しのimportを削除することを確かめる
  @Test
  void removesLeftoverBareImports() {
    assertEquals(
        "package M;\nclass S { }\n",
        StubRelocator.removeBareImports("package M;\nimport Gone;\nclass S { }\n"));
  }

  /// package宣言が無いファイルでは、importを先頭に挿入することを確かめる
  @Test
  void insertImportWithoutPackageGoesFirst() {
    assertEquals(
        "import a.B;\nclass S { }", StubRelocator.insertImport("class S { }", "import a.B;"));
  }

  /// 共通プレフィックスを文字単位ではなくドット区切りの単位で求めることを確かめる
  @Test
  void packageCommonPrefixComparesSegments() {
    assertEquals("com.a", StubRelocator.packageCommonPrefix("com.a.x", "com.a.y"));
    assertEquals("", StubRelocator.packageCommonPrefix("com.ab", "com2.ab"));
    assertEquals("com", StubRelocator.packageCommonPrefix("com.ab", "com.abc"));
  }

  /// `-pkgPrefix`が無いとき、デフォルトパッケージのスタブを既存パッケージの共通プレフィックスへ移し、参照しているファイルにだけimportを足すことを確かめる
  ///
  /// @param root スタブのルートにする作業ディレクトリ
  /// @throws Exception ファイルの読み書きに失敗した場合
  @Test
  void relocateMovesDefaultPackageStubsToCommonPackage(@TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("org/x/M"));
    Files.createDirectories(root.resolve("org/x/N"));
    Files.writeString(root.resolve("org/x/M/S.java"), "package org.x.M;\nclass S { G g; }\n");
    Files.writeString(root.resolve("org/x/N/T.java"), "package org.x.N;\nclass T { }\n");
    Files.writeString(root.resolve("G.java"), "class G { }\n");

    StubRelocator.relocate(root, "");

    assertFalse(Files.exists(root.resolve("G.java")));
    assertEquals("package org.x;\n\nclass G { }\n", Files.readString(root.resolve("org/x/G.java")));
    assertTrue(Files.readString(root.resolve("org/x/M/S.java")).contains("import org.x.G;"));
    assertFalse(Files.readString(root.resolve("org/x/N/T.java")).contains("import"));
  }
}
