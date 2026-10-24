package com.toru_otsuki.bridge_generator.gen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/// CORBAで受け渡す文字列の文字コードを変換するStringMapperを出力するユーティリティ
final class StringMapperWriter {
  private StringMapperWriter() {}

  /// StringMapperクラスを出力する
  ///
  /// CORBAサーバーはSJISのバイト列をISO-8859-1の文字列として受け渡すので、`encode`で受け取った文字列をSJISとして読み直し、`decode`でその逆を行う。
  ///
  /// @param ctx コード生成コンテキスト
  /// @throws IOException ファイルの書き込みに失敗した場合
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
        target, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
  }
}
