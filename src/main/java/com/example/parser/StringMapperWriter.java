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
   * <p>CORBAサーバーがISO-8859-1として送ってくるSJISバイト列を正しい文字列に変換します。
   *
   * @param mapperDir Mapper ディレクトリ
   * @param basePkgName ベースパッケージ名
   */
  static void write(Path mapperDir, String basePkgName) throws IOException {
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
        }
        """
            .formatted(basePkgName);
    Files.writeString(
        mapperDir.resolve("StringMapper.java"),
        content,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }
}
