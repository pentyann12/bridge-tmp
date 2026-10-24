package com.toru_otsuki.bridge_generator.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.palantir.javapoet.CodeBlock;
import com.toru_otsuki.bridge_generator.stub.CorbaType;
import com.toru_otsuki.bridge_generator.stub.StubModel;
import org.junit.jupiter.api.Test;

/// 型の解決（[StubModel#resolve]）と[TypeMapping]の型名・変換式のテスト
class TypeMappingTest {
  private static final TypeMapping TYPES = new TypeMapping("b");

  private static final CodeBlock X = CodeBlock.of("x");

  /// 型名を解決する文脈（パッケージ`M`で、`g.Global`をimportしている）
  private static final CompilationUnit CU =
      StaticJavaParser.parse("package M;\nimport g.Global;\ninterface S { }");

  /// スタブ上の型名を[#CU]の文脈で型モデルにする
  ///
  /// @param javaType スタブ上の型名
  /// @return 型モデル
  private static CorbaType type(String javaType) {
    return StubModel.resolve(javaType, CU);
  }

  /// 単純名はimport、無ければ同じパッケージで解決し、配列とAnyを区別することを確かめる
  @Test
  void resolvesTypesFromImportsAndPackage() {
    assertEquals(new CorbaType.Named("g", "Global"), type("Global"));
    assertEquals(new CorbaType.Named("M", "Local"), type("Local"));
    assertEquals(new CorbaType.Named("A.B", "C"), type("A.B.C"));
    assertEquals(
        new CorbaType.ArrayOf(new CorbaType.ArrayOf(CorbaType.STRING)), type("String[][]"));
    assertEquals(CorbaType.ANY, type("org.omg.CORBA.Any"));
  }

  /// DTO側・CORBA側・レスポンス用の型名を確かめる
  @Test
  void typeNames() {
    assertEquals("b.dto.TagDto[]", TYPES.dtoType(type("A.Tag[]")).toString());
    assertEquals("A.Tag[]", TYPES.corbaType(type("A.Tag[]")).toString());
    assertEquals("b.dto.AnyValue", TYPES.dtoType(type("org.omg.CORBA.Any")).toString());
    assertEquals("String[]", TYPES.corbaType(type("String[]")).toString());
    assertEquals("int", TYPES.resultType(type("int"), true).toString());
  }

  /// Stringとその配列は、`--return-mode=raw`でも文字コードを変換することを確かめる
  @Test
  void stringIsAlwaysConverted() {
    assertEquals(
        "b.mapper.StringMapper.encode(x)", TYPES.toDto(CorbaType.STRING, X, false).toString());
    assertEquals("b.mapper.StringMapper.decode(x)", TYPES.fromDto(CorbaType.STRING, X).toString());
    assertEquals(
        "x == null ? null : java.util.Arrays.stream(x).map(b.mapper.StringMapper::encode).toArray(String[]::new)",
        TYPES.toDto(type("String[]"), X, false).toString());
  }

  /// `--return-mode=raw`では構造体とAnyを変換せず、プリミティブの配列はどちらのモードでも変換しないことを確かめる
  @Test
  void rawModeKeepsStructsAndAny() {
    assertEquals("x", TYPES.toDto(type("A.Tag"), X, false).toString());
    assertEquals("x", TYPES.toDto(type("A.Tag[]"), X, false).toString());
    assertEquals("x", TYPES.toDto(CorbaType.ANY, X, false).toString());
    assertEquals("x", TYPES.toDto(type("int[]"), X, true).toString());
  }

  /// DTO変換では構造体とAnyをMapperで変換し、配列は要素ごとに変換することを確かめる
  @Test
  void dtoModeConvertsStructsAndAny() {
    assertEquals("b.mapper.TagMapper.toDto(x)", TYPES.toDto(type("A.Tag"), X, true).toString());
    assertEquals(
        "b.mapper.AnyMapper.toAnyValue(x)", TYPES.toDto(CorbaType.ANY, X, true).toString());
    assertEquals(
        "x == null ? null : java.util.Arrays.stream(x).map(b.mapper.TagMapper::fromDto).toArray(A.Tag[]::new)",
        TYPES.fromDto(type("A.Tag[]"), X).toString());
  }

  /// 多次元配列は、内側の配列をラムダ式で入れ子に変換することを確かめる
  @Test
  void nestedArraysUseLambdas() {
    assertEquals(
        "x == null ? null : java.util.Arrays.stream(x).map(e0 -> e0 == null ? null :"
            + " java.util.Arrays.stream(e0).map(b.mapper.TagMapper::toDto).toArray(b.dto.TagDto[]::new))"
            + ".toArray(b.dto.TagDto[][]::new)",
        TYPES.toDto(type("A.Tag[][]"), X, true).toString());
  }
}
