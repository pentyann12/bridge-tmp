package com.toru_otsuki.bridge_generator.stub;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// スタブのJavaParser構文木から、コード生成に必要な情報だけを取り出したモデル
///
/// JavaParserはシンボル解決をせずにパースしているため、型の完全修飾名はスタブのimport文から逆引きし、見つからなければ同じパッケージとみなす。
/// Holder型（out/inout引数）の`value`の型は、CORBA標準のHolderは固定の対応で、IDL由来のHolderはスタブの`value`フィールドから求める。
public final class StubModel {
  private static final List<String> PRIMITIVES =
      List.of("int", "long", "short", "byte", "boolean", "float", "double", "char");

  /// CORBA標準Holderの完全修飾名 → `value`の型
  private static final Map<String, String> STANDARD_HOLDERS =
      Map.of(
          "org.omg.CORBA.IntHolder", "int",
          "org.omg.CORBA.LongHolder", "long",
          "org.omg.CORBA.ShortHolder", "short",
          "org.omg.CORBA.BooleanHolder", "boolean",
          "org.omg.CORBA.FloatHolder", "float",
          "org.omg.CORBA.DoubleHolder", "double",
          "org.omg.CORBA.StringHolder", "String",
          "org.omg.CORBA.ByteHolder", "byte",
          "org.omg.CORBA.CharHolder", "char",
          "org.omg.CORBA.AnyHolder", "org.omg.CORBA.Any");

  private StubModel() {}

  /// CORBAサービス（IDLの`interface`）
  ///
  /// @param name サービス名（`Operations`サフィックスを除いたもの）
  /// @param pkg サービスのJavaパッケージ名
  /// @param operations 操作の一覧
  public record Service(String name, String pkg, List<Operation> operations) {}

  /// サービスの操作
  ///
  /// @param name 操作名
  /// @param returnType 戻り値の型。voidなら空
  /// @param params 引数の一覧
  public record Operation(String name, Optional<CorbaType> returnType, List<Param> params) {
    public boolean hasInParams() {
      return params.stream().anyMatch(p -> !p.isOut());
    }

    public boolean hasOutParams() {
      return params.stream().anyMatch(Param::isOut);
    }

    /// 生成クラス名の接頭辞にする、先頭を大文字にした操作名を返す
    ///
    /// @return 先頭を大文字にした操作名
    public String capitalizedName() {
      return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
  }

  /// 操作の引数
  ///
  /// @param name 引数名
  /// @param type in引数ならその型、out/inout引数ならHolderの`value`の型
  /// @param holderType out/inout引数ならHolder型の完全修飾名、in引数ならnull
  public record Param(String name, CorbaType type, String holderType) {
    public boolean isOut() {
      return holderType != null;
    }
  }

  /// IDLの`struct`に対応するクラス
  ///
  /// @param pkg Javaパッケージ名
  /// @param name クラス名
  /// @param fields フィールドの一覧
  public record Struct(String pkg, String name, List<Field> fields) {}

  /// `struct`のフィールド
  ///
  /// @param name フィールド名
  /// @param type フィールドの型
  public record Field(String name, CorbaType type) {}

  /// サービスインターフェース（`XxxOperations`）からサービスのモデルを作る
  ///
  /// @param iface サービスインターフェースの宣言
  /// @param stubs Holderの`value`型を調べるためのスタブ索引
  /// @return サービスのモデル
  public static Service service(ClassOrInterfaceDeclaration iface, StubIndex stubs) {
    CompilationUnit cu = iface.findCompilationUnit().orElseThrow();
    String name = iface.getNameAsString();
    if (name.endsWith("Operations")) {
      name = name.substring(0, name.length() - "Operations".length());
    }
    List<Operation> operations =
        iface.getMethods().stream().map(m -> operation(m, cu, stubs)).toList();
    return new Service(name, StubIndex.packageOf(cu), operations);
  }

  /// 構造体クラスから構造体のモデルを作る
  ///
  /// idljは配列フィールドをC形式（`Tag tags[]`）で出力するので、フィールド宣言ではなく変数側の型を見る。
  ///
  /// @param clazz 構造体クラスの宣言
  /// @return 構造体のモデル
  public static Struct struct(ClassOrInterfaceDeclaration clazz) {
    CompilationUnit cu = clazz.findCompilationUnit().orElseThrow();
    List<Field> fields =
        clazz.getFields().stream()
            .flatMap(f -> f.getVariables().stream())
            .map(v -> new Field(v.getNameAsString(), resolve(v.getType().asString(), cu)))
            .toList();
    return new Struct(StubIndex.packageOf(cu), clazz.getNameAsString(), fields);
  }

  private static Operation operation(
      MethodDeclaration method, CompilationUnit cu, StubIndex stubs) {
    String returnType = method.getType().asString();
    return new Operation(
        method.getNameAsString(),
        "void".equals(returnType) ? Optional.empty() : Optional.of(resolve(returnType, cu)),
        method.getParameters().stream().map(p -> param(p, cu, stubs)).toList());
  }

  /// メソッドの引数から引数のモデルを作る。型の単純名が`Holder`で終わるものをout/inout引数とみなす。
  ///
  /// @param param 引数の宣言
  /// @param cu 引数が属するコンパイル単位
  /// @param stubs Holderの`value`型を調べるためのスタブ索引
  /// @return 引数のモデル
  private static Param param(Parameter param, CompilationUnit cu, StubIndex stubs) {
    String type = param.getType().asString();
    if (!simpleName(type).endsWith("Holder")) {
      return new Param(param.getNameAsString(), resolve(type, cu), null);
    }
    String holderType = qualify(type, cu);
    return new Param(param.getNameAsString(), holderValueType(holderType, cu, stubs), holderType);
  }

  /// Holder型の`value`フィールドの型を返す
  ///
  /// idljは`sequence`のtypedefに対して配列型の`value`（例: `Tag value[]`）を持つHolderを生成するため、名前からは型が決まらない。
  /// CORBA標準のHolderは固定の対応で、それ以外はスタブの`value`フィールドから求め、スタブが無ければ名前から`Holder`を除いた型とみなす。
  ///
  /// @param holderType Holder型の完全修飾名
  /// @param cu Holder型を参照しているコンパイル単位
  /// @param stubs Holderのスタブを探すための索引
  /// @return `value`フィールドの型
  private static CorbaType holderValueType(String holderType, CompilationUnit cu, StubIndex stubs) {
    String standard = STANDARD_HOLDERS.get(holderType);
    if (standard != null) return resolve(standard, cu);
    Optional<CorbaType> fromStub =
        stubs
            .find(holderType)
            .flatMap(
                holder ->
                    holder.getFields().stream()
                        .flatMap(f -> f.getVariables().stream())
                        .filter(v -> v.getNameAsString().equals("value"))
                        .findFirst()
                        .map(
                            v ->
                                resolve(
                                    v.getType().asString(),
                                    holder.findCompilationUnit().orElseThrow())));
    return fromStub.orElseGet(
        () -> resolve(holderType.substring(0, holderType.length() - "Holder".length()), cu));
  }

  /// スタブ上の型名を型モデルに変換する
  ///
  /// @param type スタブ上の型名（単純名・完全修飾名・配列）
  /// @param context 型名が書かれているコンパイル単位（import文の逆引きに使う）
  /// @return 型モデル
  public static CorbaType resolve(String type, CompilationUnit context) {
    if (type.endsWith("[]")) {
      return new CorbaType.ArrayOf(resolve(type.substring(0, type.length() - 2), context));
    }
    if (PRIMITIVES.contains(type)) return new CorbaType.Primitive(type);
    if (type.equals("String")) return CorbaType.STRING;
    if (type.equals("org.omg.CORBA.Any")) return CorbaType.ANY;
    String fqn = qualify(type, context);
    int dot = fqn.lastIndexOf('.');
    return dot < 0
        ? new CorbaType.Named("", fqn)
        : new CorbaType.Named(fqn.substring(0, dot), fqn.substring(dot + 1));
  }

  /// 単純名をimport文から逆引きして完全修飾名にする。見つからなければコンパイル単位と同じパッケージとみなす。
  ///
  /// グローバルスコープの型は`StubRelocator`で別パッケージへ移され、import文で参照されるため、この逆引きが必要になる。
  ///
  /// @param type 型名（完全修飾名ならそのまま返す）
  /// @param context 型名が書かれているコンパイル単位
  /// @return 完全修飾名。デフォルトパッケージの型なら単純名
  private static String qualify(String type, CompilationUnit context) {
    if (type.contains(".")) return type;
    String pkg = StubIndex.packageOf(context);
    return context.getImports().stream()
        .filter(imp -> !imp.isAsterisk())
        .map(ImportDeclaration::getNameAsString)
        .filter(name -> name.endsWith("." + type))
        .findFirst()
        .orElse(pkg.isEmpty() ? type : pkg + "." + type);
  }

  private static String simpleName(String type) {
    return type.substring(type.lastIndexOf('.') + 1);
  }
}
