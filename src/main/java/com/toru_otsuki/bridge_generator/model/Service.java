package com.toru_otsuki.bridge_generator.model;

import java.util.List;
import java.util.stream.Stream;

/// CORBAサービス（IDLの`interface`）
///
/// @param name Javaのインターフェース名
/// @param pkg Javaパッケージ名
/// @param operations このインターフェースで宣言した操作の一覧（IDLでの宣言順）
/// @param inheritedOperations 親のインターフェースから継承した操作の一覧（祖先も含む）
public record Service(
    String name, String pkg, List<Operation> operations, List<Operation> inheritedOperations) {
  /// 継承した操作と宣言した操作を合わせた一覧を返す（継承した操作が先）
  ///
  /// RESTブリッジは単一プロジェクトのとき親と子で`/操作名`が重なるので[#operations]だけを使い、 共有ライブラリはJavaのメソッドなので重ならず、こちらを使う。
  ///
  /// @return すべての操作
  public List<Operation> allOperations() {
    return Stream.concat(inheritedOperations.stream(), operations.stream()).toList();
  }
}
