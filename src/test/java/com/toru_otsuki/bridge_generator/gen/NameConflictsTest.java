package com.toru_otsuki.bridge_generator.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.toru_otsuki.bridge_generator.model.CorbaType;
import com.toru_otsuki.bridge_generator.model.IdlEnum;
import com.toru_otsuki.bridge_generator.model.Operation;
import com.toru_otsuki.bridge_generator.model.Param;
import com.toru_otsuki.bridge_generator.model.Service;
import com.toru_otsuki.bridge_generator.model.Struct;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/// [NameConflicts]のテスト
class NameConflictsTest {
  private static final Naming EMPTY = new Naming("", "", "", "");

  /// 操作`findItems`（out引数あり）を持つサービス
  private static final Service SERVICE =
      new Service(
          "ItemService",
          "corba.m",
          List.of(
              new Operation(
                  "findItems",
                  Optional.empty(),
                  List.of(
                      new Param(
                          "message",
                          CorbaType.STRING,
                          "org.omg.CORBA.StringHolder",
                          Param.Direction.OUT)),
                  List.of())),
          List.of());

  /// 名前を空にしても、重ならなければ問題にしないことを確かめる
  @Test
  void emptyAffixesWithoutConflict() {
    GenSettings settings = settings(List.of(struct("corba.m", "Item")), EMPTY);
    assertEquals(List.of(), NameConflicts.find(List.of(SERVICE), settings, Target.REST));
    assertEquals(List.of(), NameConflicts.find(List.of(SERVICE), settings, Target.LIBRARY));
  }

  /// idljのクラスのパッケージが、生成するパッケージと同じなら問題にすることを確かめる
  @Test
  void idlPackageSameAsGenerated() {
    GenSettings settings = settings(List.of(struct("b.dto", "Item")), Naming.DEFAULT);
    assertMentions(NameConflicts.find(List.of(SERVICE), settings, Target.LIBRARY), "b.dto");
  }

  /// DTOが、RESTブリッジの操作ごとのクラス・ほかのDTO・java.langのクラスと重なれば問題にすることを確かめる
  @Test
  void dtoNameConflicts() {
    GenSettings rest =
        settings(
            List.of(struct("corba.m", "Item")), new Naming("Find", "sRpcRequest", "", "Result"));
    assertMentions(NameConflicts.find(List.of(SERVICE), rest, Target.REST), "FindItemsRpcRequest");
    // 共有ライブラリにはRpcRequestを作らない
    assertEquals(List.of(), NameConflicts.find(List.of(SERVICE), rest, Target.LIBRARY));

    GenSettings duplicate =
        settings(List.of(struct("corba.a", "Item"), struct("corba.b", "Item")), EMPTY);
    assertMentions(NameConflicts.find(List.of(), duplicate, Target.LIBRARY), "corba.a.Item");

    GenSettings javaLang = settings(List.of(struct("corba.m", "Object")), EMPTY);
    assertMentions(NameConflicts.find(List.of(), javaLang, Target.LIBRARY), "java.lang");
  }

  /// 結果のrecordが、クライアントの中で使うクラスと重なれば問題にすることを確かめる
  @Test
  void resultNameConflicts() {
    // ItemServiceのクライアントはItemServiceClient
    GenSettings client = settings(List.of(), new Naming("", "Dto", "", "ServiceClient"));
    Service item =
        new Service(
            "ItemService",
            "corba.m",
            List.of(
                new Operation(
                    "item", Optional.empty(), SERVICE.operations().get(0).params(), List.of())),
            List.of());
    assertMentions(NameConflicts.find(List.of(item), client, Target.LIBRARY), "ItemServiceClient");

    Service string =
        new Service(
            "S",
            "corba.m",
            List.of(
                new Operation(
                    "string", Optional.empty(), SERVICE.operations().get(0).params(), List.of())),
            List.of());
    List<String> problems =
        NameConflicts.find(List.of(string), settings(List.of(), EMPTY), Target.LIBRARY);
    assertMentions(problems, "S.string");
    // RESTブリッジには結果のrecordを作らない
    assertEquals(
        List.of(), NameConflicts.find(List.of(string), settings(List.of(), EMPTY), Target.REST));
  }

  private static void assertMentions(List<String> problems, String text) {
    assertEquals(1, problems.size(), problems.toString());
    assertTrue(problems.get(0).contains(text), problems.get(0));
  }

  private static Struct struct(String pkg, String name) {
    return new Struct(pkg, name, "IDL:" + name + ":1.0", name, List.of(), false);
  }

  private static GenSettings settings(List<Struct> structs, Naming naming) {
    return new GenSettings(
        "b", true, "SJIS", Path.of("stubs"), structs, List.<IdlEnum>of(), naming);
  }
}
