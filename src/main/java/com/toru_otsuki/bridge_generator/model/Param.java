package com.toru_otsuki.bridge_generator.model;

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
