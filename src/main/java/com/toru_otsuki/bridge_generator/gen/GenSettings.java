package com.toru_otsuki.bridge_generator.gen;

import com.toru_otsuki.bridge_generator.stub.StubModel;
import java.nio.file.Path;
import java.util.List;

/// 生成するプロジェクトによらない、コード生成全体の設定
///
/// @param basePackage 生成プロジェクトのベースパッケージ
/// @param returnAsDto 戻り値・out引数をDTOに変換するかどうか
/// @param idlPackagePrefix `idlj -pkgPrefix`に渡したパッケージプレフィックス
/// @param repoIdPrefix IDLの`#pragma prefix`で設定されたリポジトリIDプレフィックス
/// @param stubRoot idlj生成スタブのルートディレクトリ（生成プロジェクトへコピーする）
/// @param structs DTO/Mapperを生成する構造体
public record GenSettings(
    String basePackage,
    boolean returnAsDto,
    String idlPackagePrefix,
    String repoIdPrefix,
    Path stubRoot,
    List<StubModel.Struct> structs) {

  TypeMapping types() {
    return new TypeMapping(basePackage);
  }
}
