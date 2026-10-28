package com.toru_otsuki.bridge_generator.model;

import java.util.List;
import java.util.Optional;

/// サービスの操作
///
/// @param name 操作名
/// @param returnType 戻り値の型。voidなら空
/// @param params 引数の一覧
/// @param raises `raises`で宣言したユーザー例外（宣言順）
public record Operation(
    String name, Optional<CorbaType> returnType, List<Param> params, List<CorbaType.Named> raises) {
  /// リクエストで値を受け取る引数（in/inout）があるか
  ///
  /// @return あればtrue
  public boolean hasInParams() {
    return params.stream().anyMatch(Param::isInput);
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
