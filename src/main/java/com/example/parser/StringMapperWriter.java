package com.example.parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** CORBA Stringの文字コード変換Mapperを出力するユーティリティ */
final class StringMapperWriter {
  private StringMapperWriter() {}

  /**
   * StringMapperクラスを出力する
   *
   * CORBAサーバーがISO-8859-1として送ってくるSJISバイト列を正しい文字列に変換します。
   *
   * @param ctx コード生成コンテキスト
   */
  static void write(GenContext ctx) throws IOException {
    Path target = ctx.mapperDir().resolve("StringMapper.java");
    Files.createDirectories(target.getParent());

    String content =
        """
        package %s.mapper;

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
        """
            .formatted(ctx.basePackage());
    Files.writeString(
        target,
        content,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }
}
