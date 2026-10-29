package com.toru_otsuki.bridge_generator.gen;

import com.toru_otsuki.bridge_generator.model.CorbaType;
import com.toru_otsuki.bridge_generator.model.IdlEnum;
import com.toru_otsuki.bridge_generator.model.Struct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/// `org.omg.CORBA.Any`とJSONで受け渡す`AnyValue`との相互変換コードを生成するユーティリティ
final class AnyTypeWriter {
  /// 1つの`initTypeHandlersN`メソッドに詰める型の最大数。型が多いと1メソッドがJVMの64KB上限を超えるため分割する。
  private static final int TYPE_CHUNK = 50;

  /// Anyとの変換を型ごとに登録する、IDLで定義した型（構造体・exception・enum）
  ///
  /// @param pkg Javaパッケージ名
  /// @param name Javaのクラス名
  /// @param repoId リポジトリID
  /// @param idlName IDL上のスコープ付きの名前
  private record NamedType(String pkg, String name, String repoId, String idlName) {}

  private AnyTypeWriter() {}

  /// Anyの代わりに受け渡すAnyValueクラス（型名と値の組）を出力する
  ///
  /// RESTではJacksonで読み書きできる可変のクラスにする。ライブラリでは不変のrecordにし、DTOから型名を補って作る`of`と、 型を指定して値を取り出す`as`を足す。
  ///
  /// @param ctx コード生成コンテキスト
  /// @param target 生成するプロジェクトの種類
  /// @throws IOException ファイルの書き込みに失敗した場合
  static void writeAnyValueDto(GenContext ctx, Target target) throws IOException {
    String template =
        switch (target) {
          case REST ->
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
              """;
          case LIBRARY ->
              """
              package %1$s.dto;

              /**
               * CORBAのAnyに入れる値（型名と値の組）。
               *
               * <p>typeは基本型なら"long"・"string"など、構造体・enumならIDL上の名前（例: "Coverage.Item"）。構造体・enumのvalueはそのDTO。
               */
              public record AnyValue(String type, Object value) {
                /**
                 * 構造体・enumのDTOから、型名を補ってAnyValueを作る。
                 *
                 * @throws IllegalArgumentException このライブラリが知らないクラスの場合
                 */
                public static AnyValue of(Object dto) {
                  String type = %1$s.mapper.AnyMapper.typeNameOf(dto.getClass());
                  if (type == null) {
                    throw new IllegalArgumentException(dto.getClass().getName() + " はAnyに入れられる構造体・enumのDTOではありません");
                  }
                  return new AnyValue(type, dto);
                }

                /** 値が指定した型ならそれを返す。 */
                public <T> java.util.Optional<T> as(Class<T> valueType) {
                  return valueType.isInstance(value) ? java.util.Optional.of(valueType.cast(value)) : java.util.Optional.empty();
                }
              }
              """;
        };
    String content = template.formatted(ctx.basePackage());
    Files.writeString(
        ctx.dtoDir().resolve("AnyValue.java"),
        content,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  /// AnyとAnyValueを相互変換するAnyMapperクラスを出力する
  ///
  /// 基本型はTypeCodeの種類（Any → JSON）や`type`の文字列（JSON → Any）で決まる変換をする。structとenumは種類だけでは
  /// 中身の型が分からないので、型ごとに両方向の変換を登録する。
  ///
  /// - Any → JSON: リポジトリID（IDLコンパイラが付けた正確な値）で引き、Helperで取り出してMapperでDTOにする。
  ///   `type`にはIDL上のスコープ付きの名前を入れる。
  /// - JSON → Any: その`type`で引き、JSONの値をDTOに読み替えてからMapperとHelperでAnyに入れる。
  ///   そのため、CORBAから返ってきたAnyのJSONをそのまま送り返せる。
  ///
  /// sequence・union・objrefなどは、Anyに入れるためのTypeCodeをJSONから組み立てられないので、JSON → Anyでは
  /// IllegalArgumentExceptionにする。
  ///
  /// Anyはアプリが持つORB（`useOrb`で受け取る）で作る。シングルトンORB（`ORB.init()`）で作ると、structを入れるときに
  /// シングルトンORBが内部で通信用のORBを固定の名前で作り、JMXに登録する。そのため、同じTomcatに複数のアプリを置くと衝突する。
  /// アプリのORBが無いとき（Springの外で単体で使うときなど）だけシングルトンORBを使う。
  ///
  /// ライブラリでは、AnyValueの値は（JSONから読んだMapではなく）DTOそのものなので、Jacksonを使わずにDTOとして受け取る。
  /// また`AnyValue.of`のために、DTOのクラス → IDL上の名前の表も登録する。
  ///
  /// @param ctx コード生成コンテキスト
  /// @param structs DTO/Mapperを生成する構造体
  /// @param enums DTO/Mapperを生成するenum
  /// @param target 生成するプロジェクトの種類
  /// @throws IOException ファイルの書き込みに失敗した場合
  static void writeAnyMapper(
      GenContext ctx, List<Struct> structs, List<IdlEnum> enums, Target target) throws IOException {
    String pkg = ctx.basePackage() + ".mapper";
    boolean library = target == Target.LIBRARY;

    Stream<NamedType> namedTypes =
        Stream.concat(
            structs.stream().map(s -> new NamedType(s.pkg(), s.name(), s.repoId(), s.idlName())),
            enums.stream().map(e -> new NamedType(e.pkg(), e.name(), e.repoId(), e.idlName())));
    List<String> entries =
        namedTypes
            .map(
                type -> {
                  String className = type.name();
                  String helperFqn =
                      type.pkg().isEmpty()
                          ? className + "Helper"
                          : type.pkg() + "." + className + "Helper";
                  String mapperFqn = pkg + "." + className + "Mapper";
                  String dtoFqn =
                      ctx.types()
                          .dtoClass(new CorbaType.Named(type.pkg(), className))
                          .canonicalName();
                  String dtoValue =
                      library
                          ? "(%s) v".formatted(dtoFqn)
                          : "JSON.convertValue(v, %s.class)".formatted(dtoFqn);
                  String entry =
                      """
                          m.put("%1$s", any -> new AnyValue("%2$s", %3$s.toDto(%4$s.extract(any))));
                          r.put("%2$s", v -> { Any any = newAny(); %4$s.insert(any, %3$s.fromDto(%6$s)); return any; });
                      """;
                  if (library) {
                    entry +=
                        """
                            t.put(%5$s.class, "%2$s");
                        """;
                  }
                  return entry.formatted(
                      type.repoId(), type.idlName(), mapperFqn, helperFqn, dtoFqn, dtoValue);
                })
            .toList();

    // 登録処理をTYPE_CHUNK件ずつinitTypeHandlersNメソッドに分け、static初期化子から順に呼ぶ
    String typeTableParam = library ? ",\n      java.util.Map<Class<?>, String> t" : "";
    List<String> chunkMethods = new ArrayList<>();
    for (int i = 0; i < entries.size(); i += TYPE_CHUNK) {
      chunkMethods.add(
          """
            private static void initTypeHandlers%d(
                java.util.Map<String, java.util.function.Function<Any, AnyValue>> m,
                java.util.Map<String, java.util.function.Function<Object, Any>> r%s) {
          %s  }
          """
              .formatted(
                  i / TYPE_CHUNK,
                  typeTableParam,
                  String.join("", entries.subList(i, Math.min(i + TYPE_CHUNK, entries.size())))));
    }

    String initCalls =
        IntStream.range(0, chunkMethods.size())
            .mapToObj(i -> "    initTypeHandlers%d(m, r%s);\n".formatted(i, library ? ", t" : ""))
            .collect(Collectors.joining());
    String typeTable =
        library ? "  private static final java.util.Map<Class<?>, String> DTO_TYPES;\n" : "";
    String typeHandlersBlock =
        """
          private static final java.util.Map<String, java.util.function.Function<Any, AnyValue>> TYPE_HANDLERS;
          private static final java.util.Map<String, java.util.function.Function<Object, Any>> TYPE_INSERTERS;
        %s  static {
            java.util.Map<String, java.util.function.Function<Any, AnyValue>> m = new java.util.HashMap<>();
            java.util.Map<String, java.util.function.Function<Object, Any>> r = new java.util.HashMap<>();
        %s%s    TYPE_HANDLERS = java.util.Collections.unmodifiableMap(m);
            TYPE_INSERTERS = java.util.Collections.unmodifiableMap(r);
        %s  }

        %s"""
            .formatted(
                typeTable,
                library
                    ? "    java.util.Map<Class<?>, String> t = new java.util.HashMap<>();\n"
                    : "",
                initCalls,
                library ? "    DTO_TYPES = java.util.Collections.unmodifiableMap(t);\n" : "",
                String.join("\n", chunkMethods));

    // 型ごとの登録処理は件数で形が変わるので、テンプレート中の//@@TYPE_HANDLERS@@行を後から差し替える
    String content =
        """
        package %s;

        import org.omg.CORBA.Any;
        import org.omg.CORBA.ORB;
        import org.omg.CORBA.TCKind;
        import %s.dto.AnyValue;

        public final class AnyMapper {
          private AnyMapper() {}

          private static final java.util.Map<Integer, java.util.function.Function<Any, AnyValue>> KIND_HANDLERS;
          static {
            java.util.Map<Integer, java.util.function.Function<Any, AnyValue>> m = new java.util.HashMap<>();
            m.put(TCKind._tk_null, a -> null);
            m.put(TCKind._tk_void, a -> null);
            m.put(TCKind._tk_short, a -> new AnyValue("short", a.extract_short()));
            m.put(TCKind._tk_long, a -> new AnyValue("long", a.extract_long()));
            m.put(TCKind._tk_ushort, a -> new AnyValue("ushort", a.extract_ushort()));
            m.put(TCKind._tk_ulong, a -> new AnyValue("ulong", a.extract_ulong()));
            m.put(TCKind._tk_float, a -> new AnyValue("float", a.extract_float()));
            m.put(TCKind._tk_double, a -> new AnyValue("double", a.extract_double()));
            m.put(TCKind._tk_boolean, a -> new AnyValue("boolean", a.extract_boolean()));
            m.put(TCKind._tk_char, a -> new AnyValue("char", a.extract_char()));
            m.put(TCKind._tk_octet, a -> new AnyValue("octet", a.extract_octet()));
            m.put(TCKind._tk_any, a -> new AnyValue("any", AnyMapper.toAnyValue(a.extract_any())));
            m.put(TCKind._tk_TypeCode, a -> new AnyValue("TypeCode", a.extract_TypeCode()));
            m.put(TCKind._tk_Principal, a -> new AnyValue("Principal", a.extract_Object()));
            m.put(TCKind._tk_objref, a -> new AnyValue("objref", a.extract_Object()));
            m.put(TCKind._tk_struct, a -> extractStruct(a));
            m.put(TCKind._tk_union, a -> extractUnion(a));
            m.put(TCKind._tk_enum, a -> extractEnum(a));
            m.put(TCKind._tk_string, a -> extractString(a));
            m.put(TCKind._tk_sequence, a -> extractSequence(a));
            m.put(TCKind._tk_array, a -> extractArray(a));
            m.put(TCKind._tk_alias, a -> extractAlias(a));
            m.put(TCKind._tk_except, a -> extractException(a));
            m.put(TCKind._tk_longlong, a -> new AnyValue("longlong", a.extract_longlong()));
            m.put(TCKind._tk_ulonglong, a -> new AnyValue("ulonglong", a.extract_ulonglong()));
            m.put(TCKind._tk_longdouble, a -> new AnyValue("longdouble", a.extract_Object()));
            m.put(TCKind._tk_wchar, a -> extractWChar(a));
            m.put(TCKind._tk_wstring, a -> extractWString(a));
            m.put(TCKind._tk_fixed, a -> new AnyValue("fixed", a.extract_fixed()));
            m.put(TCKind._tk_value, a -> extractValue(a));
            m.put(TCKind._tk_value_box, a -> extractValueBox(a));
            m.put(TCKind._tk_native, a -> extractNative(a));
            m.put(TCKind._tk_abstract_interface, a -> extractAbstractInterface(a));
            KIND_HANDLERS = java.util.Collections.unmodifiableMap(m);
          }

          //@@TYPE_HANDLERS@@

          @TARGET_MEMBERS@

          private static volatile ORB appOrb;

          public static void useOrb(ORB orb) {
            if (appOrb == null) appOrb = orb;
          }

          public static void releaseOrb(ORB orb) {
            if (appOrb == orb) appOrb = null;
          }

          private static Any newAny() {
            ORB orb = appOrb;
            return (orb != null ? orb : ORB.init()).create_any();
          }

          public static Any toAny(AnyValue dto) {
            Any any = newAny();
            if (dto == null || @T@ == null || "null".equals(@T@)) return any;
            switch (@T@) {
              case "boolean": any.insert_boolean((Boolean) @V@); break;
              case "short": any.insert_short(((Number) @V@).shortValue()); break;
              case "ushort": any.insert_ushort(((Number) @V@).shortValue()); break;
              case "long": any.insert_long(((Number) @V@).intValue()); break;
              case "ulong": any.insert_ulong(((Number) @V@).intValue()); break;
              case "longlong": any.insert_longlong(((Number) @V@).longValue()); break;
              case "ulonglong": any.insert_ulonglong(((Number) @V@).longValue()); break;
              case "float": any.insert_float(((Number) @V@).floatValue()); break;
              case "double": any.insert_double(((Number) @V@).doubleValue()); break;
              case "char": any.insert_char(String.valueOf(@V@).charAt(0)); break;
              case "wchar": any.insert_wchar(String.valueOf(@V@).charAt(0)); break;
              case "octet": any.insert_octet(((Number) @V@).byteValue()); break;
              case "string": any.insert_string(StringMapper.decode(String.valueOf(@V@))); break;
              case "wstring": any.insert_wstring(String.valueOf(@V@)); break;
              case "any": any.insert_any(@ANY_VALUE@); break;
              default:
                java.util.function.Function<Object, Any> inserter = TYPE_INSERTERS.get(@T@);
                if (inserter == null) {
                  throw new IllegalArgumentException("AnyValue.type「" + @T@ + "」はCORBAのAnyに変換できません");
                }
                return inserter.apply(@V@);
            }
            return any;
          }

          public static AnyValue toAnyValue(Any any) {
            if (any == null) return null;
            TCKind kind = any.type().kind();
            if (kind == null || kind.value() == TCKind._tk_null || kind.value() == TCKind._tk_void) {
              return null;
            }
            java.util.function.Function<Any, AnyValue> h = KIND_HANDLERS.get(kind.value());
            if (h != null) return h.apply(any);
            return new AnyValue("object", any.extract_Object());
          }

          private static AnyValue extractStruct(Any any) {
            try {
              String id = any.type().id();
              java.util.function.Function<Any, AnyValue> h = TYPE_HANDLERS.get(id);
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

          private static AnyValue extractString(Any any) {
            try {
              return new AnyValue("string", StringMapper.encode(any.extract_string()));
            } catch (Exception e) {
              return new AnyValue("string", null);
            }
          }

          private static AnyValue extractWChar(Any any) {
            try {
              return new AnyValue("wchar", any.extract_wchar());
            } catch (Exception e) {
              return new AnyValue("wchar", null);
            }
          }

          private static AnyValue extractWString(Any any) {
            try {
              return new AnyValue("wstring", any.extract_wstring());
            } catch (Exception e) {
              return new AnyValue("wstring", null);
            }
          }

          private static AnyValue extractEnum(Any any) {
            try {
              java.util.function.Function<Any, AnyValue> h = TYPE_HANDLERS.get(any.type().id());
              return h != null ? h.apply(any) : new AnyValue("enum", any.create_input_stream().read_ulong());
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
            .replace("  //@@TYPE_HANDLERS@@\n", typeHandlersBlock)
            // RESTのAnyValueは公開フィールドを持つクラスで、入れ子のanyの値はJSONから読んだMapになっている。
            // ライブラリのAnyValueはrecordで、入れ子のanyの値はAnyValueそのもの
            .replace("@T@", library ? "dto.type()" : "dto.type")
            .replace("@V@", library ? "dto.value()" : "dto.value")
            .replace(
                "@ANY_VALUE@",
                library
                    ? "toAny((AnyValue) dto.value())"
                    : "toAny(JSON.convertValue(dto.value, AnyValue.class))")
            .replace(
                "@TARGET_MEMBERS@",
                library
                    ? """
                      public static String typeNameOf(Class<?> dtoClass) {
                          return DTO_TYPES.get(dtoClass);
                        }"""
                    : "private static final tools.jackson.databind.json.JsonMapper JSON"
                        + " = new tools.jackson.databind.json.JsonMapper();");

    Files.writeString(
        ctx.mapperDir().resolve("AnyMapper.java"),
        content,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }
}
