package com.toru_otsuki.bridge_generator.model;

/// 操作の引数
///
/// @param name 引数名
/// @param type in引数ならその型、out/inout引数ならHolderの`value`の型
/// @param holderType out/inout引数ならHolder型の完全修飾名、in引数ならnull
/// @param direction IDLで宣言した引数の向き
public record Param(String name, CorbaType type, String holderType, Direction direction) {
  /// IDLの引数の向き
  public enum Direction {
    IN,
    OUT,
    INOUT
  }

  /// in引数を作る
  ///
  /// @param name 引数名
  /// @param type 型
  /// @return in引数
  public static Param in(String name, CorbaType type) {
    return new Param(name, type, null, Direction.IN);
  }

  /// リクエストで値を受け取る引数か（in/inout）
  ///
  /// @return in/inoutならtrue
  public boolean isInput() {
    return direction != Direction.OUT;
  }

  /// Holderで渡し、呼び出し後の値を結果で返す引数か（out/inout）
  ///
  /// @return out/inoutならtrue
  public boolean isOut() {
    return direction != Direction.IN;
  }
}
