package com.toru_otsuki.bridge_generator.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.palantir.javapoet.CodeBlock;
import com.toru_otsuki.bridge_generator.model.CorbaType;
import org.junit.jupiter.api.Test;

/// [TypeMapping]の型名・変換式のテスト
class TypeMappingTest {
  /// ベースパッケージ`b`の型の対応付け
  private static final TypeMapping TYPES = new TypeMapping("b");

  private static final CodeBlock X = CodeBlock.of("x");

  /// IDLの構造体`A::Tag`
  private static final CorbaType TAG = new CorbaType.Named("A", "Tag");

  private static final CorbaType INT = new CorbaType.Primitive("int");

  private static CorbaType arrayOf(CorbaType element) {
    return new CorbaType.ArrayOf(element);
  }

  /// DTO側・CORBA側・レスポンス用の型名を確かめる
  @Test
  void typeNames() {
    assertEquals("b.dto.TagDto[]", TYPES.dtoType(arrayOf(TAG)).toString());
    assertEquals("A.Tag[]", TYPES.corbaType(arrayOf(TAG)).toString());
    assertEquals("b.dto.AnyValue", TYPES.dtoType(CorbaType.ANY).toString());
    assertEquals("String[]", TYPES.corbaType(arrayOf(CorbaType.STRING)).toString());
    assertEquals("int", TYPES.resultType(INT, true).toString());
  }

  /// Stringとその配列は、`--return-mode=raw`でも文字コードを変換することを確かめる
  @Test
  void stringIsAlwaysConverted() {
    assertEquals(
        "b.mapper.StringMapper.encode(x)", TYPES.toDto(CorbaType.STRING, X, false).toString());
    assertEquals("b.mapper.StringMapper.decode(x)", TYPES.fromDto(CorbaType.STRING, X).toString());
    assertEquals(
        "x == null ? null : java.util.Arrays.stream(x).map(b.mapper.StringMapper::encode).toArray(String[]::new)",
        TYPES.toDto(arrayOf(CorbaType.STRING), X, false).toString());
  }

  /// `--return-mode=raw`では構造体とAnyを変換せず、プリミティブの配列はどちらのモードでも変換しないことを確かめる
  @Test
  void rawModeKeepsStructsAndAny() {
    assertEquals("x", TYPES.toDto(TAG, X, false).toString());
    assertEquals("x", TYPES.toDto(arrayOf(TAG), X, false).toString());
    assertEquals("x", TYPES.toDto(CorbaType.ANY, X, false).toString());
    assertEquals("x", TYPES.toDto(arrayOf(INT), X, true).toString());
  }

  /// DTO変換では構造体とAnyをMapperで変換し、配列は要素ごとに変換することを確かめる
  @Test
  void dtoModeConvertsStructsAndAny() {
    assertEquals("b.mapper.TagMapper.toDto(x)", TYPES.toDto(TAG, X, true).toString());
    assertEquals(
        "b.mapper.AnyMapper.toAnyValue(x)", TYPES.toDto(CorbaType.ANY, X, true).toString());
    assertEquals(
        "x == null ? null : java.util.Arrays.stream(x).map(b.mapper.TagMapper::fromDto).toArray(A.Tag[]::new)",
        TYPES.fromDto(arrayOf(TAG), X).toString());
  }

  /// 多次元配列は、内側の配列をラムダ式で入れ子に変換することを確かめる
  @Test
  void nestedArraysUseLambdas() {
    assertEquals(
        "x == null ? null : java.util.Arrays.stream(x).map(e0 -> e0 == null ? null :"
            + " java.util.Arrays.stream(e0).map(b.mapper.TagMapper::toDto).toArray(b.dto.TagDto[]::new))"
            + ".toArray(b.dto.TagDto[][]::new)",
        TYPES.toDto(arrayOf(arrayOf(TAG)), X, true).toString());
  }
}
