package com.toru_otsuki.bridge_generator.gen;

import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.TypeName;
import com.toru_otsuki.bridge_generator.model.CorbaType;

/// 型モデル（[CorbaType]）ごとに、DTO側の型・CORBA側の型・相互変換の式を決める
///
/// 構造体Mapper・Controller・RequestDto/RpcResultはすべてここを通して型と変換式を得る。変換の規則は次のとおり。
///
/// - プリミティブ: そのまま
/// - String: CORBAから受け取るときは`StringMapper.encode`、渡すときは`StringMapper.decode`（DTO変換の有無によらず常に）
/// - Any: `AnyMapper.toAnyValue`/`AnyMapper.toAny`
/// - 名前付きの型: `XxxMapper.toDto`/`XxxMapper.fromDto`
/// - 配列: 要素の変換が要るときだけ`Arrays.stream`で要素ごとに変換する（多次元配列は入れ子にする）
///
/// CORBA→DTOの変換で`asDto=false`（`--return-mode=raw`）のときは、Anyと名前付きの型を変換せずCORBA型のまま返す。
///
/// 生成する式中のクラスは[ClassName]で埋め込むので、JavaPoetの`JavaFile`内ではimportされた単純名に、`CodeBlock#toString()`では完全修飾名になる。
///
/// @param basePackage 生成プロジェクトのベースパッケージ
/// @param naming DTOの名前の付け方
record TypeMapping(String basePackage, Naming naming) {
  /// `java.lang.`を付けずに書くためのString型（JavaPoetの`JavaFile`内でも単純名で出力される）
  static final ClassName STRING = ClassName.get("", "String");

  private static final ClassName CORBA_ANY = ClassName.get("org.omg.CORBA", "Any");

  String dtoPackage() {
    return basePackage + ".dto";
  }

  String mapperPackage() {
    return basePackage + ".mapper";
  }

  /// 構造体・enumのDTOのクラスを返す
  ///
  /// @param type 構造体かenumの型
  /// @return DTOのクラス
  ClassName dtoClass(CorbaType.Named type) {
    return dtoClass(naming.dtoName(type.simpleName()));
  }

  ClassName dtoClass(String simpleName) {
    return ClassName.get(dtoPackage(), simpleName);
  }

  ClassName mapperClass(String simpleName) {
    return ClassName.get(mapperPackage(), simpleName);
  }

  /// DTO側の型を返す。RequestDto・構造体DTOのフィールドと、`--return-mode=dto`の戻り値に使う。
  ///
  /// @param type 型モデル
  /// @return DTO側の型
  TypeName dtoType(CorbaType type) {
    return switch (type) {
      case CorbaType.Primitive p -> primitive(p.name());
      case CorbaType.Str s -> STRING;
      case CorbaType.AnyType a -> dtoClass("AnyValue");
      case CorbaType.Named n -> dtoClass(n);
      case CorbaType.ArrayOf a -> ArrayTypeName.of(dtoType(a.element()));
    };
  }

  TypeName corbaType(CorbaType type) {
    return switch (type) {
      case CorbaType.Primitive p -> primitive(p.name());
      case CorbaType.Str s -> STRING;
      case CorbaType.AnyType a -> CORBA_ANY;
      case CorbaType.Named n -> ClassName.get(n.pkg(), n.simpleName());
      case CorbaType.ArrayOf a -> ArrayTypeName.of(corbaType(a.element()));
    };
  }

  /// 戻り値・out引数としてレスポンスに載せるときの型を返す
  ///
  /// @param type 型モデル
  /// @param asDto DTOに変換するかどうか
  /// @return DTOに変換するならDTO側の型、しないならCORBA側の型
  TypeName resultType(CorbaType type, boolean asDto) {
    return asDto ? dtoType(type) : corbaType(type);
  }

  /// CORBA側の値をレスポンス用の値に変換する式を返す
  ///
  /// @param type 値の型
  /// @param rhs 変換元の式（複数回評価されることがあるので、副作用のない変数参照を渡すこと）
  /// @param asDto Anyと名前付きの型をDTOに変換するかどうか
  /// @return 変換後の式
  CodeBlock toDto(CorbaType type, CodeBlock rhs, boolean asDto) {
    return convert(type, rhs, true, asDto, 0);
  }

  /// DTO側でnullのまま、CORBAに渡せない型か（inout引数のように、利用側に値を必ず求める型）
  ///
  /// 文字列・名前付きの型・配列はnullのままCORBAで送れない。プリミティブは省略すると既定値（0やfalse）になり、 Anyは空のAnyにできるので含めない。
  ///
  /// @param type 値の型
  /// @return 値が必要ならtrue
  static boolean needsValue(CorbaType type) {
    return type instanceof CorbaType.Str
        || type instanceof CorbaType.Named
        || type instanceof CorbaType.ArrayOf;
  }

  /// DTO側の値をCORBA側の値に変換する式を返す
  ///
  /// @param type 値の型
  /// @param rhs 変換元の式（複数回評価されることがあるので、副作用のない変数参照を渡すこと）
  /// @return 変換後の式
  CodeBlock fromDto(CorbaType type, CodeBlock rhs) {
    return convert(type, rhs, false, true, 0);
  }

  /// 変換が不要（元の式がそのまま値になる）かどうかを判定する
  ///
  /// @param type 値の型
  /// @param toDto CORBA→DTO方向ならtrue、DTO→CORBA方向ならfalse
  /// @param asDto Anyと名前付きの型をDTOに変換するかどうか（CORBA→DTO方向でのみ意味を持つ）
  /// @return 変換が不要であればtrue
  private boolean isIdentity(CorbaType type, boolean toDto, boolean asDto) {
    return switch (type) {
      case CorbaType.Primitive p -> true;
      case CorbaType.Str s -> false;
      case CorbaType.AnyType a -> toDto && !asDto;
      case CorbaType.Named n -> toDto && !asDto;
      case CorbaType.ArrayOf a -> isIdentity(a.element(), toDto, asDto);
    };
  }

  /// 値を変換する式を組み立てる。配列は要素ごとの変換をストリームで包み、それ以外はMapperの静的メソッド呼び出しにする。
  ///
  /// @param type 値の型
  /// @param rhs 変換元の式
  /// @param toDto CORBA→DTO方向ならtrue、DTO→CORBA方向ならfalse
  /// @param asDto Anyと名前付きの型をDTOに変換するかどうか
  /// @param depth 配列の入れ子の深さ（ラムダ式の変数名を重複させないために使う）
  /// @return 変換後の式
  private CodeBlock convert(
      CorbaType type, CodeBlock rhs, boolean toDto, boolean asDto, int depth) {
    if (isIdentity(type, toDto, asDto)) return rhs;
    if (type instanceof CorbaType.ArrayOf array) {
      CorbaType element = array.element();
      TypeName elementType = toDto ? resultType(element, asDto) : corbaType(element);
      return CodeBlock.of(
          "$L == null ? null : java.util.Arrays.stream($L).map($L).toArray($T[]::new)",
          rhs,
          rhs,
          elementFunction(element, toDto, asDto, depth),
          elementType);
    }
    MapperMethod method = mapperMethod(type, toDto);
    return CodeBlock.of("$T.$L($L)", method.owner(), method.name(), rhs);
  }

  /// 配列の要素に適用する関数を返す。要素が配列ならラムダ式で入れ子の変換を書き、それ以外はメソッド参照にする。
  ///
  /// @param element 要素の型
  /// @param toDto CORBA→DTO方向ならtrue、DTO→CORBA方向ならfalse
  /// @param asDto Anyと名前付きの型をDTOに変換するかどうか
  /// @param depth 配列の入れ子の深さ
  /// @return `map`に渡す関数の式
  private CodeBlock elementFunction(CorbaType element, boolean toDto, boolean asDto, int depth) {
    if (element instanceof CorbaType.ArrayOf) {
      CodeBlock var = CodeBlock.of("e$L", depth);
      return CodeBlock.of("$L -> $L", var, convert(element, var, toDto, asDto, depth + 1));
    }
    MapperMethod method = mapperMethod(element, toDto);
    return CodeBlock.of("$T::$L", method.owner(), method.name());
  }

  /// 値1つを変換する静的メソッド（例: `StringMapper.encode`）
  ///
  /// @param owner メソッドを持つMapperクラス
  /// @param name メソッド名
  private record MapperMethod(ClassName owner, String name) {}

  private MapperMethod mapperMethod(CorbaType type, boolean toDto) {
    return switch (type) {
      case CorbaType.Str s ->
          new MapperMethod(mapperClass("StringMapper"), toDto ? "encode" : "decode");
      case CorbaType.AnyType a ->
          new MapperMethod(mapperClass("AnyMapper"), toDto ? "toAnyValue" : "toAny");
      case CorbaType.Named n ->
          new MapperMethod(mapperClass(n.simpleName() + "Mapper"), toDto ? "toDto" : "fromDto");
      default -> throw new IllegalArgumentException("変換メソッドを持たない型です: " + type);
    };
  }

  private static TypeName primitive(String name) {
    return switch (name) {
      case "int" -> TypeName.INT;
      case "long" -> TypeName.LONG;
      case "short" -> TypeName.SHORT;
      case "byte" -> TypeName.BYTE;
      case "boolean" -> TypeName.BOOLEAN;
      case "float" -> TypeName.FLOAT;
      case "double" -> TypeName.DOUBLE;
      case "char" -> TypeName.CHAR;
      default -> throw new IllegalArgumentException("プリミティブ型ではありません: " + name);
    };
  }
}
