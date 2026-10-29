package com.toru_otsuki.bridge_generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.toru_otsuki.bridge_generator.gen.Naming;
import com.toru_otsuki.bridge_generator.gen.Target;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/// [CliOptions]のテスト
class CliOptionsTest {

  /// IDLファイルだけを渡したとき、各オプションが既定値になることを確かめる
  @Test
  void defaults() {
    CliOptions o = CliOptions.parse("a.idl");
    assertEquals(List.of(Path.of("a.idl")), o.idlFiles());
    assertEquals(Path.of("generated-stubs"), o.stubRoot());
    assertEquals(Path.of("generated-spring"), o.outputRoot());
    assertEquals("com.generated", o.basePackage());
    assertEquals("", o.idlPackagePrefix());
    assertTrue(o.returnAsDto());
    assertEquals("SJIS", o.stringCharset());
    assertFalse(o.singleProject());
    assertEquals("CombinedApi", o.projectName());
    assertEquals(Naming.DEFAULT, o.naming());
    assertFalse(o.help());
  }

  /// 全オプションの値が反映され、`--return-mode`の大文字小文字を区別しないことを確かめる
  @Test
  void allOptions() {
    CliOptions o =
        CliOptions.parse(
            "--stub-root=s",
            "--output-root=o",
            "--base-package=com.bridge",
            "--idl-package-prefix=com.corba",
            "--return-mode=RAW",
            "--string-charset=Windows-31J",
            "--single-project",
            "--project-name=Bridge",
            "--dto-prefix=Cx",
            "--dto-suffix=",
            "--result-prefix=R",
            "--result-suffix=Out",
            "a.idl",
            "b.idl");
    assertEquals(List.of(Path.of("a.idl"), Path.of("b.idl")), o.idlFiles());
    assertEquals(Path.of("s"), o.stubRoot());
    assertEquals(Path.of("o"), o.outputRoot());
    assertEquals("com.bridge", o.basePackage());
    assertEquals("com.corba", o.idlPackagePrefix());
    assertFalse(o.returnAsDto());
    assertEquals("Windows-31J", o.stringCharset());
    assertTrue(o.singleProject());
    assertEquals("Bridge", o.projectName());
    assertEquals(new Naming("Cx", "", "R", "Out"), o.naming());
  }

  /// `--return-mode`が空のときはDTOに変換することを確かめる
  @Test
  void blankReturnModeMeansDto() {
    assertTrue(CliOptions.parse("--return-mode=", "a.idl").returnAsDto());
  }

  /// `--help`/`-h`があれば、以降の引数を読まずにヘルプ要求として返すことを確かめる
  @Test
  void helpStopsParsing() {
    assertTrue(CliOptions.parse("--help", "--unknown").help());
    assertTrue(CliOptions.parse("-h").help());
  }

  /// 既定はRESTブリッジで、`--target=library`ではプロジェクト名の既定値が`corba-client`になることを確かめる
  @Test
  void targetSelectsProjectKind() {
    CliOptions rest = CliOptions.parse("a.idl");
    assertEquals(Target.REST, rest.target());
    assertEquals("CombinedApi", rest.projectName());

    CliOptions library = CliOptions.parse("--target=LIBRARY", "a.idl");
    assertEquals(Target.LIBRARY, library.target());
    assertEquals("corba-client", library.projectName());
    assertEquals(
        "my-lib",
        CliOptions.parse("--target=library", "--project-name=my-lib", "a.idl").projectName());
  }

  /// `--string-charset=none`は文字コード名でなくても受け付けることを確かめる
  @Test
  void stringCharsetNoneMeansNoConversion() {
    assertEquals("none", CliOptions.parse("--string-charset=none", "a.idl").stringCharset());
  }

  ///
  /// 不明なオプション・不正な`--return-mode`・`--string-charset`・`--target`・クラス名に使えないプレフィックスとサフィックス・IDLファイル未指定を拒否することを確かめる
  @Test
  void rejectsInvalidInput() {
    assertThrows(IllegalArgumentException.class, () -> CliOptions.parse("--unknown", "a.idl"));
    assertThrows(
        IllegalArgumentException.class, () -> CliOptions.parse("--return-mode=x", "a.idl"));
    assertThrows(
        IllegalArgumentException.class, () -> CliOptions.parse("--string-charset=NoSuch", "a.idl"));
    assertThrows(IllegalArgumentException.class, () -> CliOptions.parse("--target=x", "a.idl"));
    assertThrows(
        IllegalArgumentException.class, () -> CliOptions.parse("--string-charset=a b", "a.idl"));
    assertThrows(IllegalArgumentException.class, () -> CliOptions.parse("--single-project"));
    assertThrows(
        IllegalArgumentException.class, () -> CliOptions.parse("--dto-suffix=-x", "a.idl"));
    assertThrows(IllegalArgumentException.class, () -> CliOptions.parse("--dto-prefix=1", "a.idl"));
    assertThrows(
        IllegalArgumentException.class, () -> CliOptions.parse("--result-suffix=a.b", "a.idl"));
  }

  /// サフィックスは数字で始めてもよいことを確かめる
  @Test
  void suffixMayStartWithDigit() {
    assertEquals("2", CliOptions.parse("--dto-suffix=2", "a.idl").naming().dtoSuffix());
  }

  /// 使い方の説明文に全オプションが載っていることを確かめる
  @Test
  void usageListsEveryOption() {
    String usage = CliOptions.usage();
    for (String option :
        List.of(
            "--stub-root",
            "--output-root",
            "--base-package",
            "--idl-package-prefix",
            "--target",
            "--return-mode",
            "--string-charset",
            "--single-project",
            "--project-name",
            "--dto-prefix",
            "--dto-suffix",
            "--result-prefix",
            "--result-suffix",
            "--help")) {
      assertTrue(usage.contains(option), option);
    }
  }
}
