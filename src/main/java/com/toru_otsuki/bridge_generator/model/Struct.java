package com.toru_otsuki.bridge_generator.model;

import java.util.List;

/// DTOとMapperを生成する型（IDLの`struct`と`exception`。どちらもJavaではフィールドだけを持つクラスになる）
///
/// @param pkg Javaパッケージ名
/// @param name Javaのクラス名
/// @param repoId リポジトリID（`#pragma prefix`などを反映した、IDLコンパイラが付けたもの）
/// @param idlName IDL上のスコープ付きの名前をドットでつないだもの（例: `Coverage.Tag`）
/// @param fields フィールドの一覧
public record Struct(String pkg, String name, String repoId, String idlName, List<Field> fields) {}
