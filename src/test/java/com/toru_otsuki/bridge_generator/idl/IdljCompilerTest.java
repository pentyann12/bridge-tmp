package com.toru_otsuki.bridge_generator.idl;

import static com.toru_otsuki.bridge_generator.model.Param.Direction.INOUT;
import static com.toru_otsuki.bridge_generator.model.Param.Direction.OUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.toru_otsuki.bridge_generator.GenerationException;
import com.toru_otsuki.bridge_generator.model.CorbaType;
import com.toru_otsuki.bridge_generator.model.IdlModel;
import com.toru_otsuki.bridge_generator.model.Operation;
import com.toru_otsuki.bridge_generator.model.Param;
import com.toru_otsuki.bridge_generator.model.Service;
import com.toru_otsuki.bridge_generator.model.Struct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// 網羅用IDLを[IdljCompiler]で処理し、スタブの出力先とモデルの中身を確かめる
class IdljCompilerTest {
  private static final Path COVERAGE_IDL = Path.of("src/test/resources/idl/coverage.idl");

  private static final CorbaType INT = new CorbaType.Primitive("int");
  private static final CorbaType TAG = new CorbaType.Named("com.corba.Coverage", "Tag");
  private static final CorbaType POINT = new CorbaType.Named("com.corba.Coverage.Inner", "Point");

  private static Service service(IdlModel model, String name) {
    return model.services().stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
  }

  private static Operation operation(Service service, String name) {
    return service.operations().stream()
        .filter(o -> o.name().equals(name))
        .findFirst()
        .orElseThrow();
  }

  /// グローバルスコープの型も含めて、すべてのスタブが名前付きパッケージに出力されることを確かめる
  ///
  /// @param stubs スタブの出力先
  /// @throws Exception 生成やファイルの走査に失敗した場合
  @Test
  void globalTypesGetAPackage(@TempDir Path stubs) throws Exception {
    IdljCompiler.compile(COVERAGE_IDL, stubs, "com.corba");
    try (Stream<Path> top = Files.list(stubs)) {
      assertFalse(top.anyMatch(p -> p.toString().endsWith(".java")), "無名パッケージにスタブが出ています");
    }
    assertTrue(Files.exists(stubs.resolve("com/corba/GlobalNamesHelper.java")));
    assertTrue(Files.exists(stubs.resolve("com/corba/Coverage/ItemServiceOperations.java")));
  }

  /// 接頭辞を指定しなければ、moduleはそのままで、グローバルスコープの型だけを`generated`に入れることを確かめる
  ///
  /// @param stubs スタブの出力先
  /// @throws Exception 生成に失敗した場合
  @Test
  void withoutPrefixOnlyGlobalTypesMove(@TempDir Path stubs) throws Exception {
    IdlModel model = IdljCompiler.compile(COVERAGE_IDL, stubs, "");
    assertTrue(Files.exists(stubs.resolve("generated/GlobalNamesHelper.java")));
    assertEquals("Coverage", service(model, "ItemService").pkg());
  }

  /// 引数の向き・Holder型・typedefをたどった型が、IDLの宣言どおりになることを確かめる
  ///
  /// @param stubs スタブの出力先
  /// @throws Exception 生成に失敗した場合
  @Test
  void operationsFollowIdlDeclarations(@TempDir Path stubs) throws Exception {
    IdlModel model = IdljCompiler.compile(COVERAGE_IDL, stubs, "com.corba");
    Service items = service(model, "ItemService");
    assertEquals("com.corba.Coverage", items.pkg());

    Operation findItems = operation(items, "findItems");
    assertEquals(Optional.of(INT), findItems.returnType());
    assertEquals(
        List.of(
            Param.in("keyword", CorbaType.STRING),
            new Param("tags", new CorbaType.ArrayOf(TAG), "com.corba.Coverage.TagSeqHolder", OUT),
            new Param("message", CorbaType.STRING, "org.omg.CORBA.StringHolder", OUT)),
        findItems.params());
    assertEquals(
        List.of(new Param("counter", INT, "org.omg.CORBA.IntHolder", INOUT)),
        operation(items, "touch").params());
    assertEquals(
        List.of(
            Param.in("p", POINT),
            new Param("previous", POINT, "com.corba.Coverage.Inner.PointHolder", OUT)),
        operation(items, "movePoint").params());
    assertEquals(Optional.empty(), operation(items, "putItem").returnType());
  }

  /// 構造体のリポジトリIDとIDL上の名前は、`#pragma prefix`を含めてIDLコンパイラが付けた値になることを確かめる
  ///
  /// @param stubs スタブの出力先
  /// @throws Exception 生成に失敗した場合
  @Test
  void structsCarryExactRepositoryIds(@TempDir Path stubs) throws Exception {
    IdlModel model = IdljCompiler.compile(COVERAGE_IDL, stubs, "com.corba");
    Struct point =
        model.structs().stream().filter(s -> s.name().equals("Point")).findFirst().orElseThrow();
    assertEquals("IDL:toru-otsuki.example/Coverage/Inner/Point:1.0", point.repoId());
    assertEquals("Coverage.Inner.Point", point.idlName());
    assertEquals("com.corba.Coverage.Inner", point.pkg());
  }

  /// 操作の`raises`と、`exception`であることがモデルに入ることを確かめる
  ///
  /// @param stubs スタブの出力先
  /// @throws Exception 生成に失敗した場合
  @Test
  void raisesAndExceptionsAreModeled(@TempDir Path stubs) throws Exception {
    IdlModel model =
        IdljCompiler.compile(Path.of("src/test/resources/idl/errors.idl"), stubs, "com.corba");
    Service guarded = service(model, "Guarded");
    assertEquals(
        List.of(
            new CorbaType.Named("com.corba.Errors", "NotFound"),
            new CorbaType.Named("com.corba.Errors", "AccessDeniedException")),
        operation(guarded, "fetch").raises());
    assertEquals(
        List.of(new CorbaType.Named("com.corba.Errors", "AccessDeniedException")),
        operation(guarded, "check").raises());

    Struct notFound =
        model.structs().stream().filter(s -> s.name().equals("NotFound")).findFirst().orElseThrow();
    assertTrue(notFound.exception());
    assertEquals("IDL:toru-otsuki.example/Errors/NotFound:1.0", notFound.repoId());
  }

  /// 文法の誤りがあるIDLは、生成を止めて利用者向けのエラーにすることを確かめる
  ///
  /// @param dir IDLとスタブを置く作業ディレクトリ
  /// @throws Exception ファイルの書き込みに失敗した場合
  @Test
  void syntaxErrorStopsGeneration(@TempDir Path dir) throws Exception {
    Path broken = dir.resolve("broken.idl");
    Files.writeString(broken, "module M { struct S { long x } };\n");
    assertThrows(
        GenerationException.class, () -> IdljCompiler.compile(broken, dir.resolve("stubs"), ""));
  }
}
