package com.toru_otsuki.bridge_generator.model;

import java.util.List;

/// CORBAサービス（IDLの`interface`）
///
/// @param name Javaのインターフェース名
/// @param pkg Javaパッケージ名
/// @param operations 操作の一覧（IDLでの宣言順）
public record Service(String name, String pkg, List<Operation> operations) {}
