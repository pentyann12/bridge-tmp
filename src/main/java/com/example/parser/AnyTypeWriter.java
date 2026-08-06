package com.example.parser;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** CORBA.Any <-> AnyValueの相互変換コード生成ユーティリティ */
final class AnyTypeWriter {
  // 1つのinitStructHandlersNに詰める最大エントリ数
  private static final int STRUCT_CHUNK = 50;
  
  /** constructor. */
  private AnyTypeWriter() {}

  /**
   * AnyValueクラスを出力する
   *
   * @param ctx コード生成コンテキスト
   */
  static void writeAnyValueDto(GenContext ctx) throws IOException {
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
            .formatted(ctx.basePackage());
    Files.writeString(
        ctx.dtoDir().resolve("AnyValue.java"),
        content,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  /**
   * Javaパッケージ名から`idlj -pkgPrefix`で付与されたプレフィックスを除き、本来のIDL名前空間を返す
   *
   * @param classPkg Javaパッケージ名
   * @param ctx コード生成コンテキスト
   * @return プレフィックスを除いたIDL名前空間
   */
  private static String toIdlPkg(String classPkg, GenContext ctx) {
    String prefix = ctx.idlPackagePrefix();
    if (prefix.isEmpty()) return classPkg;
    if (classPkg.equals(prefix)) return "";
    if (classPkg.startsWith(prefix + ".")) return classPkg.substring(prefix.length() + 1);
    return classPkg;
  }

  /**
   * CORBA.Any <-> AnyValue相互変換Mapperを出力する
   *
   * @param ctx コード生成コンテキスト
   * @param structClasses IDLスタブから収集した構造体クラス一覧
   */
  static void writeAnyMapper(GenContext ctx, List<ClassOrInterfaceDeclaration> structClasses)
      throws IOException {
    String pkg = ctx.basePackage() + ".mapper";

    // 構造体ごとのMap.putエントリ文字列を生成
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
                  String idlPkg = toIdlPkg(classPkg, ctx);
                  // #pragma prefixとIDL名前空間を組み合わせてTypeCode IDを構築
                  String repoPath =
                      (ctx.repoIdPrefix().isEmpty() ? "" : ctx.repoIdPrefix() + "/")
                          + (idlPkg.isEmpty() ? "" : idlPkg.replace('.', '/') + "/")
                          + className;
                  String repoId = "IDL:" + repoPath + ":1.0";
                  // AnyValue.type: IDL名前空間付き型名
                  String typeName = idlPkg.isEmpty() ? className : idlPkg + "." + className;
                  String helperFqn =
                      classPkg.isEmpty()
                          ? className + "Helper"
                          : classPkg + "." + className + "Helper";
                  String mapperFqn = ctx.basePackage() + ".mapper." + className + "Mapper";
                  return "    m.put(\"%s\", any -> new AnyValue(\"%s\", %s.toDto(%s.extract(any))));\n"
                      .formatted(repoId, typeName, mapperFqn, helperFqn);
                })
            .collect(Collectors.toList());

    // STRUCT_CHUNK件ずつinitStructHandlersN()メソッドに分割
    List<String> chunkMethods = new ArrayList<>();
    for (int i = 0; i < entries.size(); i += STRUCT_CHUNK) {
      int idx = i / STRUCT_CHUNK;
      String body = String.join("", entries.subList(i, Math.min(i + STRUCT_CHUNK, entries.size())));
      chunkMethods.add(
          "  private static void initStructHandlers"
              + idx
              + "(java.util.Map<String, java.util.function.Function<Any, AnyValue>> m) {\n"
              + body
              + "  }\n");
    }

    // STRUCT_HANDLERSフィールド宣言 + static init + initStructHandlersNメソッド群
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
            m.put(TCKind._tk_boolean, a -> new AnyValue("boolean", a.extract_boolean()));
            m.put(TCKind._tk_short, a -> new AnyValue("short", a.extract_short()));
            m.put(TCKind._tk_ushort, a -> new AnyValue("ushort", a.extract_ushort()));
            m.put(TCKind._tk_long, a -> new AnyValue("long", a.extract_long()));
            m.put(TCKind._tk_ulong, a -> new AnyValue("ulong", a.extract_ulong()));
            m.put(TCKind._tk_longlong, a -> new AnyValue("longlong", a.extract_longlong()));
            m.put(TCKind._tk_ulonglong, a -> new AnyValue("ulonglong", a.extract_ulonglong()));
            m.put(TCKind._tk_float, a -> new AnyValue("float", a.extract_float()));
            m.put(TCKind._tk_double, a -> new AnyValue("double", a.extract_double()));
            m.put(TCKind._tk_longdouble, a -> new AnyValue("longdouble", a.extract_longdouble()));
            m.put(TCKind._tk_char, a -> new AnyValue("char", a.extract_char()));
            m.put(TCKind._tk_wchar, a -> new AnyValue("wchar", a.extract_wchar()));
            m.put(TCKind._tk_octet, a -> new AnyValue("octet", a.extract_octet()));
            m.put(TCKind._tk_string, a -> new AnyValue("string", a.extract_string()));
            m.put(TCKind._tk_wstring, a -> new AnyValue("wstring", a.extract_wstring()));
            m.put(TCKind._tk_any, a -> new AnyValue("any", AnyMapper.toAnyValue(a.extract_any())));
            m.put(TCKind._tk_TypeCode, a -> new AnyValue("TypeCode", a.extract_TypeCode()));
            m.put(TCKind._tk_Principal, a -> new AnyValue("Principal", a.extract_Principal()));
            m.put(TCKind._tk_objref, a -> new AnyValue("objref", a.extract_Object()));
            m.put(TCKind._tk_fixed, a -> new AnyValue("fixed", a.extract_fixed()));
            KIND_HANDLERS = java.util.Collections.unmodifiableMap(m);
          }

          //@@STRUCT_HANDLERS@@

          public static Any toAny(AnyValue dto) {
            if (dto == null) return null;
            Any any = ORB.init(new String[0], null).create_any();
            if (dto.type == null) return any;
            switch (dto.type) {
              case "boolean": any.insert_boolean((Boolean) dto.value); break;
              case "short": any.insert_short(((Number) dto.value).shortValue()); break;
              case "ushort": any.insert_ushort(((Number) dto.value).shortValue()); break;
              case "long": any.insert_long(((Number) dto.value).intValue()); break;
              case "ulong": any.insert_ulong(((Number) dto.value).intValue()); break;
              case "longlong": any.insert_longlong(((Number) dto.value).longValue()); break;
              case "ulonglong": any.insert_ulonglong(((Number) dto.value).longValue()); break;
              case "float": any.insert_float(((Number) dto.value).floatValue()); break;
              case "double": any.insert_double(((Number) dto.value).doubleValue()); break;
              case "longdouble": any.insert_longdouble(new java.math.BigDecimal(dto.value.toString())); break;
              case "char": any.insert_char(String.valueOf(dto.value).charAt(0)); break;
              case "wchar": any.insert_wchar(String.valueOf(dto.value).charAt(0)); break;
              case "octet": any.insert_octet(((Number) dto.value).byteValue()); break;
              case "string": any.insert_string(String.valueOf(dto.value)); break;
              case "wstring": any.insert_wstring(String.valueOf(dto.value)); break;
              case "TypeCode": any.insert_TypeCode((org.omg.CORBA.TypeCode) dto.value); break;
              case "Principal": any.insert_Principal((org.omg.CORBA.Principal) dto.value); break;
              case "any": any.insert_any(toAny((AnyValue) dto.value)); break;
              case "objref": case "union": case "enum": case "sequence": case "array": 
              case "alias": case "except": case "fixed": case "value": case "value_box": 
              case "native": case "abstract_interface": 
                any.insert_Object((Object) dto.value); break;
              default: any.insert_Object((Object) dto.value); break;
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
            if (kind.equals(TCKind.tk_union)) return extractUnion(any);
            if (kind.equals(TCKind.tk_enum)) return extractEnum(any);
            if (kind.equals(TCKind.tk_sequence)) return extractSequence(any);
            if (kind.equals(TCKind.tk_array)) return extractArray(any);
            if (kind.equals(TCKind.tk_alias)) return extractAlias(any);
            if (kind.equals(TCKind.tk_except)) return extractException(any);
            if (kind.equals(TCKind.tk_value)) return extractValue(any);
            if (kind.equals(TCKind.tk_value_box)) return extractValueBox(any);
            if (kind.equals(TCKind.tk_native)) return extractNative(any);
            if (kind.equals(TCKind.tk_abstract_interface)) return extractAbstractInterface(any);
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

          private static AnyValue extractUnion(Any any) {
            try {
              return new AnyValue("union", any.extract_Object());
            } catch (Exception e) {
              return new AnyValue("union", null);
            }
          }

          private static AnyValue extractEnum(Any any) {
            try {
              return new AnyValue("enum", any.extract_long());
            } catch (Exception e) {
              return new AnyValue("enum", null);
            }
          }

          private static AnyValue extractSequence(Any any) {
            try {
              return new AnyValue("sequence", any.extract_Object());
            } catch (Exception e) {
              return new AnyValue("sequence", null);
            }
          }

          private static AnyValue extractArray(Any any) {
            try {
              return new AnyValue("array", any.extract_Object());
            } catch (Exception e) {
              return new AnyValue("array", null);
            }
          }

          private static AnyValue extractAlias(Any any) {
            try {
              return new AnyValue("alias", any.extract_Object());
            } catch (Exception e) {
              return new AnyValue("alias", null);
            }
          }

          private static AnyValue extractException(Any any) {
            try {
              return new AnyValue("except", any.extract_Object());
            } catch (Exception e) {
              return new AnyValue("except", null);
            }
          }

          private static AnyValue extractValue(Any any) {
            try {
              return new AnyValue("value", any.extract_Object());
            } catch (Exception e) {
              return new AnyValue("value", null);
            }
          }

          private static AnyValue extractValueBox(Any any) {
            try {
              return new AnyValue("value_box", any.extract_Object());
            } catch (Exception e) {
              return new AnyValue("value_box", null);
            }
          }

          private static AnyValue extractNative(Any any) {
            try {
              return new AnyValue("native", any.extract_Object());
            } catch (Exception e) {
              return new AnyValue("native", null);
            }
          }

          private static AnyValue extractAbstractInterface(Any any) {
            try {
              return new AnyValue("abstract_interface", any.extract_Object());
            } catch (Exception e) {
              return new AnyValue("abstract_interface", null);
            }
          }
        }
        """
            .formatted(pkg, ctx.basePackage())
            .replace("  //@@STRUCT_HANDLERS@@\n", structHandlersBlock);

    Files.writeString(
        ctx.mapperDir().resolve("AnyMapper.java"),
        content,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }
}
