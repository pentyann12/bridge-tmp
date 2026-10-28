package com.generated.mapper;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * CORBAサーバーが自分の文字コードのバイト列をISO-8859-1の文字列として受け渡すのを、Javaの文字列に読み替える。
 */
public final class StringMapper {
  private static volatile Charset charset = toCharset("SJIS");

  private StringMapper() {}

  /**
   * CORBAサーバーが文字列に使う文字コードを設定する。"none"なら読み替えない。
   *
   * @throws java.nio.charset.UnsupportedCharsetException 文字コード名が不正な場合
   */
  public static void configure(String charsetName) {
    charset = toCharset(charsetName);
  }

  private static Charset toCharset(String name) {
    return name.isBlank() || name.equalsIgnoreCase("none") ? null : Charset.forName(name.strip());
  }

  public static String encode(String text) {
    Charset cs = charset;
    if (text == null || cs == null) return text;
    return new String(text.getBytes(StandardCharsets.ISO_8859_1), cs);
  }

  public static String decode(String text) {
    Charset cs = charset;
    if (text == null || cs == null) return text;
    return new String(text.getBytes(cs), StandardCharsets.ISO_8859_1);
  }
}
