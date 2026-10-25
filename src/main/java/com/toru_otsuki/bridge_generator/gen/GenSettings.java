package com.toru_otsuki.bridge_generator.gen;

import com.toru_otsuki.bridge_generator.model.Struct;
import java.nio.file.Path;
import java.util.List;

/// 生成するプロジェクトによらない、コード生成全体の設定
///
/// @param basePackage 生成プロジェクトのベースパッケージ
/// @param returnAsDto 戻り値・out引数をDTOに変換するかどうか
/// @param stringCharset 生成アプリの`corba.string-charset`の既定値（`none`なら読み替えない）
/// @param stubRoot idlj生成スタブのルートディレクトリ（生成プロジェクトへコピーする）
/// @param structs DTO/Mapperを生成する構造体
public record GenSettings(
    String basePackage,
    boolean returnAsDto,
    String stringCharset,
    Path stubRoot,
    List<Struct> structs) {

  TypeMapping types() {
    return new TypeMapping(basePackage);
  }
}
