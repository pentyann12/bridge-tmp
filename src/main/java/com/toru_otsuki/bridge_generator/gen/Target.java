package com.toru_otsuki.bridge_generator.gen;

/// 生成するプロジェクトの種類
public enum Target {
  /// CORBAサービスをHTTP/JSONで公開するSpring Bootアプリ
  REST,
  /// CORBAサービスを型付きのJavaメソッドで呼ぶための共有ライブラリ（jar）
  LIBRARY
}
