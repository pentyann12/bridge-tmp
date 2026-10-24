package com.generated.mapper;

import java.nio.charset.StandardCharsets;

public final class StringMapper {
  private StringMapper() {}

  public static String encode(String text) {
    if (text == null) return null;
    try {
      return new String(text.getBytes(StandardCharsets.ISO_8859_1), "SJIS");
    } catch (java.io.UnsupportedEncodingException e) {
      return text;
    }
  }

  public static String decode(String text) {
    if (text == null) return null;
    try {
      return new String(text.getBytes("SJIS"), StandardCharsets.ISO_8859_1);
    } catch (java.io.UnsupportedEncodingException e) {
      return text;
    }
  }
}
