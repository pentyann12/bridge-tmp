package com.toru_otsuki.bridge_generator;

/// 入力やツールの問題で生成を続けられないことを表す例外。メッセージはそのまま利用者に表示する。
public class GenerationException extends Exception {
  public GenerationException(String message) {
    super(message);
  }
}
