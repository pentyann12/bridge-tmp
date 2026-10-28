package com.generated.mapper;

import org.omg.CORBA.Any;
import org.omg.CORBA.ORB;
import org.omg.CORBA.TCKind;
import com.generated.dto.AnyValue;

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

  private static final java.util.Map<String, java.util.function.Function<Any, AnyValue>> STRUCT_HANDLERS;
  private static final java.util.Map<String, java.util.function.Function<Object, Any>> STRUCT_INSERTERS;
  static {
    java.util.Map<String, java.util.function.Function<Any, AnyValue>> m = new java.util.HashMap<>();
    java.util.Map<String, java.util.function.Function<Object, Any>> r = new java.util.HashMap<>();
    initStructHandlers0(m, r);
    STRUCT_HANDLERS = java.util.Collections.unmodifiableMap(m);
    STRUCT_INSERTERS = java.util.Collections.unmodifiableMap(r);
  }

  private static void initStructHandlers0(
      java.util.Map<String, java.util.function.Function<Any, AnyValue>> m,
      java.util.Map<String, java.util.function.Function<Object, Any>> r) {
    m.put("IDL:toru-otsuki.example/Coverage/Inner/Point:1.0", any -> new AnyValue("Coverage.Inner.Point", com.generated.mapper.PointMapper.toDto(com.corba.Coverage.Inner.PointHelper.extract(any))));
    r.put("Coverage.Inner.Point", v -> { Any any = newAny(); com.corba.Coverage.Inner.PointHelper.insert(any, com.generated.mapper.PointMapper.fromDto(JSON.convertValue(v, com.generated.dto.PointDto.class))); return any; });
    m.put("IDL:toru-otsuki.example/Coverage/Item:1.0", any -> new AnyValue("Coverage.Item", com.generated.mapper.ItemMapper.toDto(com.corba.Coverage.ItemHelper.extract(any))));
    r.put("Coverage.Item", v -> { Any any = newAny(); com.corba.Coverage.ItemHelper.insert(any, com.generated.mapper.ItemMapper.fromDto(JSON.convertValue(v, com.generated.dto.ItemDto.class))); return any; });
    m.put("IDL:toru-otsuki.example/Coverage/Tag:1.0", any -> new AnyValue("Coverage.Tag", com.generated.mapper.TagMapper.toDto(com.corba.Coverage.TagHelper.extract(any))));
    r.put("Coverage.Tag", v -> { Any any = newAny(); com.corba.Coverage.TagHelper.insert(any, com.generated.mapper.TagMapper.fromDto(JSON.convertValue(v, com.generated.dto.TagDto.class))); return any; });
  }

  private static final tools.jackson.databind.json.JsonMapper JSON = new tools.jackson.databind.json.JsonMapper();

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
    if (dto == null || dto.type == null || "null".equals(dto.type)) return any;
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
      case "char": any.insert_char(String.valueOf(dto.value).charAt(0)); break;
      case "wchar": any.insert_wchar(String.valueOf(dto.value).charAt(0)); break;
      case "octet": any.insert_octet(((Number) dto.value).byteValue()); break;
      case "string": any.insert_string(StringMapper.decode(String.valueOf(dto.value))); break;
      case "wstring": any.insert_wstring(String.valueOf(dto.value)); break;
      case "any": any.insert_any(toAny(JSON.convertValue(dto.value, AnyValue.class))); break;
      default:
        java.util.function.Function<Object, Any> inserter = STRUCT_INSERTERS.get(dto.type);
        if (inserter == null) {
          throw new IllegalArgumentException("AnyValue.type「" + dto.type + "」はCORBAのAnyに変換できません");
        }
        return inserter.apply(dto.value);
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
