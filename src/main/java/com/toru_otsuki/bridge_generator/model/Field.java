package com.toru_otsuki.bridge_generator.model;

/// `struct`のフィールド
///
/// @param name フィールド名
/// @param type フィールドの型
public record Field(String name, CorbaType type) {}
