package com.toru_otsuki.bridge_generator.gen;

import com.toru_otsuki.bridge_generator.model.Operation;
import javax.lang.model.SourceVersion;

/// 利用者が直接使うクラスの名前に付けるプレフィックスとサフィックス
///
/// 構造体・enumのDTOと、共有ライブラリの操作の結果のrecordが対象。Mapperや、RESTブリッジのリクエスト・レスポンスのクラスは利用者が触らないので対象外。
/// 空にもできる。DTOを空にするとidljのクラスと同じ名前になるが、パッケージが違うので生成コードは完全修飾名やimportで区別できる。
///
/// @param dtoPrefix DTOのプレフィックス
/// @param dtoSuffix DTOのサフィックス
/// @param resultPrefix 結果のrecordのプレフィックス
/// @param resultSuffix 結果のrecordのサフィックス
public record Naming(String dtoPrefix, String dtoSuffix, String resultPrefix, String resultSuffix) {
  /// 指定が無いときの名前（`ItemDto`・`FindItemsResult`）
  public static final Naming DEFAULT = new Naming("", "Dto", "", "Result");

  /// 名前の一部に使える文字列かどうかを判定する。空なら使える
  ///
  /// @param affix プレフィックスかサフィックス
  /// @param prefix プレフィックスならtrue（先頭に数字を置けない）
  /// @return Javaのクラス名の一部にできればtrue
  public static boolean isValidAffix(String affix, boolean prefix) {
    return SourceVersion.isIdentifier(prefix ? affix + "A" : "A" + affix);
  }

  /// 構造体・enumのDTOのクラス名を返す
  ///
  /// @param idlName idljが生成したクラスの単純名
  /// @return DTOの単純名
  String dtoName(String idlName) {
    return dtoPrefix + idlName + dtoSuffix;
  }

  /// out/inout引数がある操作の、結果のrecordの名前を返す（クライアントクラスの中に置く）
  ///
  /// @param op 操作
  /// @return recordの単純名
  String resultName(Operation op) {
    return resultPrefix + op.capitalizedName() + resultSuffix;
  }
}
