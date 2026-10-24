package com.toru_otsuki.bridge_generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    assertFalse(o.singleProject());
    assertEquals("CombinedApi", o.projectName());
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
            "--single-project",
            "--project-name=Bridge",
            "a.idl",
            "b.idl");
    assertEquals(List.of(Path.of("a.idl"), Path.of("b.idl")), o.idlFiles());
    assertEquals(Path.of("s"), o.stubRoot());
    assertEquals(Path.of("o"), o.outputRoot());
    assertEquals("com.bridge", o.basePackage());
    assertEquals("com.corba", o.idlPackagePrefix());
    assertFalse(o.returnAsDto());
    assertTrue(o.singleProject());
    assertEquals("Bridge", o.projectName());
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

  /// 不明なオプション・不正な`--return-mode`・IDLファイル未指定を拒否することを確かめる
  @Test
  void rejectsInvalidInput() {
    assertThrows(IllegalArgumentException.class, () -> CliOptions.parse("--unknown", "a.idl"));
    assertThrows(
        IllegalArgumentException.class, () -> CliOptions.parse("--return-mode=x", "a.idl"));
    assertThrows(IllegalArgumentException.class, () -> CliOptions.parse("--single-project"));
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
            "--return-mode",
            "--single-project",
            "--project-name",
            "--help")) {
      assertTrue(usage.contains(option), option);
    }
  }
}
