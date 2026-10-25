package com.generated.mapper;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class StringMapper {
  private static volatile Charset charset = toCharset("SJIS");

  public StringMapper(@Value("${corba.string-charset:SJIS}") String charsetName) {
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
