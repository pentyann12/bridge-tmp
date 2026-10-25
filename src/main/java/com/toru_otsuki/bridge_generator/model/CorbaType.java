package com.toru_otsuki.bridge_generator.model;

/// idljが生成したJava型を、コード生成上の扱いの違いで分類した型モデル
///
/// DTO型・CORBA型・相互変換式の決め方は、genパッケージの`TypeMapping`がこの分類ごとに一度だけ定義する。
public sealed interface CorbaType {

  /// Javaのプリミティブ型（IDLの`long`は`int`、`long long`は`long`になる）
  ///
  /// @param name Javaのプリミティブ型名
  record Primitive(String name) implements CorbaType {}

  /// IDLの`string`/`wstring`（Javaの`String`）。CORBAとの受け渡しで文字コード変換が要る。
  record Str() implements CorbaType {}

  /// IDLの`any`（`org.omg.CORBA.Any`）。DTO側は`AnyValue`で表す。
  record AnyType() implements CorbaType {}

  /// IDLで定義された名前付きの型（主に`struct`）。DTO側は`単純名Dto`、変換は`単純名Mapper`で行う。
  ///
  /// @param pkg Javaパッケージ名（デフォルトパッケージなら空文字列）
  /// @param simpleName クラスの単純名
  record Named(String pkg, String simpleName) implements CorbaType {}

  /// 配列（IDLの`sequence`や配列）。要素ごとに変換する。
  ///
  /// @param element 要素の型
  record ArrayOf(CorbaType element) implements CorbaType {}

  CorbaType STRING = new Str();

  CorbaType ANY = new AnyType();
}
