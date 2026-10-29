package com.toru_otsuki.bridge_generator.model;

import java.util.List;

/// IDLの`enum`
///
/// CORBAでは選択肢の名前ではなく、宣言順の番号（0から）で送られる。
///
/// @param pkg Javaパッケージ名
/// @param name Javaのクラス名
/// @param repoId リポジトリID（`#pragma prefix`などを反映した、IDLコンパイラが付けたもの）
/// @param idlName IDL上のスコープ付きの名前をドットでつないだもの（例: `Coverage.Status`）
/// @param members 選択肢の名前（IDLでの宣言順）
public record IdlEnum(
    String pkg, String name, String repoId, String idlName, List<String> members) {

  /// この型を参照するときの型モデルを返す。変換の形は構造体と同じ（`XxxDto`と`XxxMapper`）なので、名前付きの型として扱う
  ///
  /// @return 名前付きの型
  public CorbaType.Named type() {
    return new CorbaType.Named(pkg, name);
  }
}
