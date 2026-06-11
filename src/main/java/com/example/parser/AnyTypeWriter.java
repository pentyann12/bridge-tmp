package com.example.parser;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** CORBA Any ↔ AnyValue 相互変換に使う雛形クラスを出力するユーティリティ */
final class AnyTypeWriter {
  private AnyTypeWriter() {}

  // 1つの initStructHandlersN() に詰める最大エントリ数（65535バイト制限対策）
  private static final int STRUCT_CHUNK = 50;

  /**
   * AnyValue クラスを出力する
   *
   * @param dtoDir 出力先ディレクトリ
   * @param basePkgName ベースパッケージ名
   */
  static void writeAnyValueDto(Path dtoDir, String basePkgName) throws IOException {
    String content =
        """
        package %s.dto;

        public class AnyValue {
          public String type;
          public Object value;
          public AnyValue() {}
          public AnyValue(String type, Object value) {
            this.type = type;
            this.value = value;
          }
        }
        """
            .formatted(basePkgName);
    Files.writeString(
        dtoDir.resolve("AnyValue.java"),
        content,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  /**
   * CORBA.Any ⇔ AnyValue 相互変換 Mapper を出力する
   *
   * <p>プリミティブ型の分岐は {@code KIND_HANDLERS}（{@code Map<Integer, Function<Any,AnyValue>>}）で処理し、
   * {@code toAnyValue} 自体のバイト数を最小化します。
   *
   * <p>{@code tk_struct} については {@code STRUCT_HANDLERS}（{@code Map<String, Function<Any,AnyValue>>}）で
   * TypeCode ID から直接 Mapper を引きます。エントリ数が多くても static initializer の 65535 バイト制限に
   * 引っかからないよう、{@code initStructHandlersN()} メソッドへチャンク分割して登録します。
   *
   * @param mapperDir Mapper ディレクトリ
   * @param basePkgName ベースパッケージ名
   * @param structClasses IDL スタブから収集した構造体クラス一覧
   */
  static void writeAnyMapper(
      Path mapperDir, String basePkgName, List<ClassOrInterfaceDeclaration> structClasses)
      throws IOException {
    String pkg = basePkgName + ".mapper";

    // 構造体ごとの Map.put エントリ文字列を生成
    List<String> entries =
        structClasses.stream()
            .map(
                clazz -> {
                  String className = clazz.getNameAsString();
                  String classPkg =
                      clazz
                          .findCompilationUnit()
                          .flatMap(cu -> cu.getPackageDeclaration().map(pd -> pd.getNameAsString()))
                          .orElse("");
                  String repoId =
                      "IDL:"
                          + (classPkg.isEmpty() ? "" : classPkg.replace('.', '/') + "/")
                          + className
                          + ":1.0";
                  String helperFqn =
                      classPkg.isEmpty()
                          ? className + "Helper"
                          : classPkg + "." + className + "Helper";
                  String mapperFqn = basePkgName + ".mapper." + className + "Mapper";
                  return "    m.put(\"%s\", any -> new AnyValue(\"%s\", %s.toDto(%s.extract(any))));\n"
                      .formatted(repoId, className, mapperFqn, helperFqn);
                })
            .collect(Collectors.toList());

    // STRUCT_CHUNK 件ずつ initStructHandlersN() メソッドに分割
    List<String> chunkMethods = new ArrayList<>();
    for (int i = 0; i < entries.size(); i += STRUCT_CHUNK) {
      int idx = i / STRUCT_CHUNK;
      String body =
          String.join("", entries.subList(i, Math.min(i + STRUCT_CHUNK, entries.size())));
      chunkMethods.add(
          "  private static void initStructHandlers"
              + idx
              + "(java.util.Map<String, java.util.function.Function<Any, AnyValue>> m) {\n"
              + body
              + "  }\n");
    }

    // STRUCT_HANDLERS フィールド宣言 + static init + initStructHandlersN メソッド群
    String structHandlersBlock;
    if (entries.isEmpty()) {
      structHandlersBlock =
          "  private static final java.util.Map<String, java.util.function.Function<Any, AnyValue>> STRUCT_HANDLERS\n"
              + "      = java.util.Collections.emptyMap();\n";
    } else {
      String initCalls =
          IntStream.range(0, chunkMethods.size())
              .mapToObj(i -> "    initStructHandlers" + i + "(m);\n")
              .collect(Collectors.joining());
      structHandlersBlock =
          "  private static final java.util.Map<String, java.util.function.Function<Any, AnyValue>> STRUCT_HANDLERS;\n"
              + "  static {\n"
              + "    java.util.Map<String, java.util.function.Function<Any, AnyValue>> m = new java.util.HashMap<>();\n"
              + initCalls
              + "    STRUCT_HANDLERS = java.util.Collections.unmodifiableMap(m);\n"
              + "  }\n"
              + "\n"
              + String.join("\n", chunkMethods);
    }

    String content =
        """
        package %s;

        import org.omg.CORBA.Any;
        import org.omg.CORBA.ORB;
        import org.omg.CORBA.TCKind;
        import org.omg.CORBA.Object;
        import %s.dto.AnyValue;

        public final class AnyMapper {
          private AnyMapper() {}

          private static final java.util.Map<Integer, java.util.function.Function<Any, AnyValue>> KIND_HANDLERS;
          static {
            java.util.Map<Integer, java.util.function.Function<Any, AnyValue>> m = new java.util.HashMap<>();
            m.put(TCKind._tk_boolean,   a -> new AnyValue("boolean", a.extract_boolean()));
            m.put(TCKind._tk_short,     a -> new AnyValue("short",   a.extract_short()));
            m.put(TCKind._tk_ushort,    a -> new AnyValue("short",   a.extract_ushort()));
            m.put(TCKind._tk_long,      a -> new AnyValue("int",     a.extract_long()));
            m.put(TCKind._tk_ulong,     a -> new AnyValue("int",     a.extract_ulong()));
            m.put(TCKind._tk_longlong,  a -> new AnyValue("long",    a.extract_longlong()));
            m.put(TCKind._tk_ulonglong, a -> new AnyValue("long",    a.extract_ulonglong()));
            m.put(TCKind._tk_float,     a -> new AnyValue("float",   a.extract_float()));
            m.put(TCKind._tk_double,    a -> new AnyValue("double",  a.extract_double()));
            m.put(TCKind._tk_char,      a -> new AnyValue("char",    a.extract_char()));
            m.put(TCKind._tk_wchar,     a -> new AnyValue("char",    a.extract_wchar()));
            m.put(TCKind._tk_octet,     a -> new AnyValue("byte",    a.extract_octet()));
            m.put(TCKind._tk_string,    a -> new AnyValue("string",  a.extract_string()));
            m.put(TCKind._tk_any,       a -> new AnyValue("any",     AnyMapper.toAnyValue(a.extract_any())));
            KIND_HANDLERS = java.util.Collections.unmodifiableMap(m);
          }

          //@@STRUCT_HANDLERS@@

          public static Any toAny(AnyValue dto) {
            if (dto == null) return null;
            Any any = ORB.init(new String[0], null).create_any();
            if (dto.type == null) return any;
            switch (dto.type) {
              case "boolean": any.insert_boolean((Boolean) dto.value); break;
              case "short":   any.insert_short(((Number) dto.value).shortValue()); break;
              case "long":    any.insert_long(((Number) dto.value).intValue()); break;
              case "float":   any.insert_float(((Number) dto.value).floatValue()); break;
              case "double":  any.insert_double(((Number) dto.value).doubleValue()); break;
              case "string":  any.insert_string(String.valueOf(dto.value)); break;
              case "any":     any.insert_any(toAny((AnyValue) dto.value)); break;
              default:        any.insert_Object((Object) dto.value); break;
            }
            return any;
          }

          public static AnyValue toAnyValue(Any any) {
            if (any == null) return null;
            TCKind kind = any.type().kind();
            if (kind.equals(TCKind.tk_null) || kind.equals(TCKind.tk_void)) return null;
            java.util.function.Function<Any, AnyValue> h = KIND_HANDLERS.get(kind.value());
            if (h != null) return h.apply(any);
            if (kind.equals(TCKind.tk_struct)) return extractStruct(any);
            return new AnyValue("object", any.extract_Object());
          }

          private static AnyValue extractStruct(Any any) {
            try {
              String id = any.type().id();
              java.util.function.Function<Any, AnyValue> h = STRUCT_HANDLERS.get(id);
              return h != null ? h.apply(any) : new AnyValue("struct", null);
            } catch (org.omg.CORBA.TypeCodePackage.BadKind e) {
              return new AnyValue("struct", null);
            }
          }
        }
        """
            .formatted(pkg, basePkgName)
            .replace("  //@@STRUCT_HANDLERS@@\n", structHandlersBlock);

    Files.writeString(
        mapperDir.resolve("AnyMapper.java"),
        content,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }
}
