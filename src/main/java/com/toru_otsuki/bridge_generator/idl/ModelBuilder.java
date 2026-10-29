package com.toru_otsuki.bridge_generator.idl;

import com.sun.tools.corba.ee.idl.AttributeEntry;
import com.sun.tools.corba.ee.idl.EnumEntry;
import com.sun.tools.corba.ee.idl.ExceptionEntry;
import com.sun.tools.corba.ee.idl.InterfaceEntry;
import com.sun.tools.corba.ee.idl.MethodEntry;
import com.sun.tools.corba.ee.idl.ModuleEntry;
import com.sun.tools.corba.ee.idl.ParameterEntry;
import com.sun.tools.corba.ee.idl.PrimitiveEntry;
import com.sun.tools.corba.ee.idl.SequenceEntry;
import com.sun.tools.corba.ee.idl.StringEntry;
import com.sun.tools.corba.ee.idl.StructEntry;
import com.sun.tools.corba.ee.idl.SymtabEntry;
import com.sun.tools.corba.ee.idl.TypedefEntry;
import com.sun.tools.corba.ee.idl.toJavaPortable.Util;
import com.toru_otsuki.bridge_generator.model.CorbaType;
import com.toru_otsuki.bridge_generator.model.Field;
import com.toru_otsuki.bridge_generator.model.IdlEnum;
import com.toru_otsuki.bridge_generator.model.IdlModel;
import com.toru_otsuki.bridge_generator.model.Operation;
import com.toru_otsuki.bridge_generator.model.Param;
import com.toru_otsuki.bridge_generator.model.Service;
import com.toru_otsuki.bridge_generator.model.Struct;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// idljの記号表から、コード生成に必要な情報だけを取り出したモデルを作る
///
/// Javaの名前はidljの対応規則（[Util#javaQualifiedName]、[Util#holderName]）で求めるので、生成されたスタブの名前と必ず一致する。
/// パッケージの割り当て（[IdljCompiler#preEmit]）が済んだ後の記号表を渡すこと。
final class ModelBuilder {
  private static final Set<String> PRIMITIVES =
      Set.of("int", "long", "short", "byte", "boolean", "float", "double", "char");

  private ModelBuilder() {}

  /// トップレベルの定義から、module・interfaceの中も含めてサービスと構造体とenumを集める
  ///
  /// @param topLevel 構文解析で得たトップレベルの定義
  /// @param idlNames 定義 → IDL上のスコープ付きの名前（[#idlNames]で求めたもの）
  /// @return モデル
  static IdlModel build(List<SymtabEntry> topLevel, Map<SymtabEntry, String> idlNames) {
    List<Service> services = new ArrayList<>();
    List<Struct> structs = new ArrayList<>();
    List<IdlEnum> enums = new ArrayList<>();
    for (SymtabEntry entry : allEntries(topLevel)) {
      if (entry instanceof InterfaceEntry iface) {
        services.add(service(iface));
      } else if (entry instanceof StructEntry struct) {
        // exceptionもStructEntryで、Javaではフィールドだけのクラスになる
        structs.add(struct(struct, idlNames.get(struct)));
      } else if (entry instanceof EnumEntry enumEntry) {
        String javaName = Util.javaQualifiedName(enumEntry);
        enums.add(
            new IdlEnum(
                packageName(javaName),
                simpleName(javaName),
                enumEntry.repositoryID().ID(),
                idlNames.get(enumEntry),
                List.copyOf(enumEntry.elements())));
      }
    }
    return IdlModel.merge(List.of(new IdlModel(services, structs, enums)));
  }

  /// 各定義のIDL上のスコープ付きの名前をドットでつないで求める（例: `Coverage.Inner.Point`）
  ///
  /// パッケージを割り当てると、記号表の親の連なりに`-pkgPrefix`のmoduleが差し込まれ、module名もJavaのパッケージに書き換わる。
  /// そのため割り当て前（[IdljCompiler#preEmit]で元の処理を呼ぶ前）に呼ぶこと。
  ///
  /// @param topLevel 構文解析で得たトップレベルの定義
  /// @return 定義 → IDL上の名前
  static Map<SymtabEntry, String> idlNames(List<SymtabEntry> topLevel) {
    Map<SymtabEntry, String> names = new IdentityHashMap<>();
    for (SymtabEntry entry : allEntries(topLevel)) {
      Deque<String> parts = new ArrayDeque<>();
      for (SymtabEntry e = entry; e != null && !e.name().isEmpty(); e = e.container()) {
        parts.push(e.name());
      }
      names.put(entry, String.join(".", parts));
    }
    return names;
  }

  /// moduleとinterfaceの中（interfaceの中で定義したstructなど）も含めて、すべての定義を宣言順にたどる
  private static List<SymtabEntry> allEntries(List<SymtabEntry> topLevel) {
    List<SymtabEntry> all = new ArrayList<>();
    Deque<SymtabEntry> pending = new ArrayDeque<>(topLevel);
    while (!pending.isEmpty()) {
      SymtabEntry entry = pending.pop();
      all.add(entry);
      if (entry instanceof ModuleEntry module) {
        module.contained().reversed().forEach(pending::push);
      } else if (entry instanceof InterfaceEntry iface) {
        iface.contained().reversed().forEach(pending::push);
      }
    }
    return all;
  }

  /// インターフェースの操作からサービスを作る。`attribute`は含めない
  ///
  /// 継承した操作は`allMethods()`（親から順に、宣言した操作も含む）から宣言した操作を除いて求める。 ひし形の継承では同じ親の操作が2回入るので、同じ定義は1つにまとめる。
  private static Service service(InterfaceEntry iface) {
    String javaName = Util.javaQualifiedName(iface);
    Set<MethodEntry> declared = Collections.newSetFromMap(new IdentityHashMap<>());
    declared.addAll(iface.methods());
    Set<MethodEntry> inherited = Collections.newSetFromMap(new IdentityHashMap<>());
    for (MethodEntry method : iface.allMethods()) {
      if (!declared.contains(method)) inherited.add(method);
    }
    return new Service(
        simpleName(javaName),
        packageName(javaName),
        operations(iface.methods()),
        operations(iface.allMethods().stream().filter(inherited::remove).toList()));
  }

  private static List<Operation> operations(List<MethodEntry> methods) {
    List<Operation> operations = new ArrayList<>();
    for (MethodEntry method : methods) {
      if (method instanceof AttributeEntry) continue;
      Optional<CorbaType> returnType =
          method.type() == null ? Optional.empty() : Optional.of(resolve(method.type()));
      List<CorbaType.Named> raises =
          method.exceptions().stream().map(e -> (CorbaType.Named) resolve(e)).toList();
      operations.add(new Operation(method.name(), returnType, params(method), raises));
    }
    return operations;
  }

  /// 引数の向きはIDLの宣言から決まる。out/inoutはHolder型で渡すので、その名前もidljの規則で求める
  private static List<Param> params(MethodEntry method) {
    List<Param> params = new ArrayList<>();
    for (ParameterEntry p : method.parameters()) {
      CorbaType type = resolve(p.type());
      Param.Direction direction =
          switch (p.passType()) {
            case ParameterEntry.In -> Param.Direction.IN;
            case ParameterEntry.Out -> Param.Direction.OUT;
            default -> Param.Direction.INOUT;
          };
      String holder = direction == Param.Direction.IN ? null : Util.holderName(p.type());
      params.add(new Param(p.name(), type, holder, direction));
    }
    return params;
  }

  private static Struct struct(StructEntry struct, String idlName) {
    String javaName = Util.javaQualifiedName(struct);
    List<Field> fields =
        struct.members().stream().map(m -> new Field(m.name(), resolve(m))).toList();
    return new Struct(
        packageName(javaName),
        simpleName(javaName),
        struct.repositoryID().ID(),
        idlName,
        fields,
        struct instanceof ExceptionEntry);
  }

  /// IDLの型をコード生成上の分類に写す。typedefは元の型までたどり、配列の宣言（`long a[3]`）は配列にする
  static CorbaType resolve(SymtabEntry type) {
    if (type instanceof TypedefEntry typedef) {
      CorbaType resolved = resolve(typedef.type());
      for (int i = 0; i < typedef.arrayInfo().size(); i++) {
        resolved = new CorbaType.ArrayOf(resolved);
      }
      return resolved;
    }
    if (type instanceof SequenceEntry sequence) {
      return new CorbaType.ArrayOf(resolve(sequence.type()));
    }
    if (type instanceof StringEntry) {
      return CorbaType.STRING;
    }
    String javaName = Util.javaQualifiedName(type);
    if (type instanceof PrimitiveEntry) {
      if (javaName.equals("org.omg.CORBA.Any")) return CorbaType.ANY;
      if (PRIMITIVES.contains(javaName)) return new CorbaType.Primitive(javaName);
    }
    return new CorbaType.Named(packageName(javaName), simpleName(javaName));
  }

  private static String packageName(String javaName) {
    int dot = javaName.lastIndexOf('.');
    return dot < 0 ? "" : javaName.substring(0, dot);
  }

  private static String simpleName(String javaName) {
    return javaName.substring(javaName.lastIndexOf('.') + 1);
  }
}
