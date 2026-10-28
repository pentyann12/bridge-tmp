package com.toru_otsuki.bridge_generator.gen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/// CORBAで受け渡す文字列の文字コードを変換するStringMapperを出力するユーティリティ
final class StringMapperWriter {
  private StringMapperWriter() {}

  /// 共有ライブラリ用のStringMapperクラスを出力する
  ///
  /// 変換の中身はREST用（[#write]）と同じ。ライブラリはSpringに依存させないので、文字コードは`configure`で設定する
  /// （Springの自動設定が起動時に`corba.string-charset`を渡す）。設定されなければ生成時の指定を使う。
  ///
  /// @param ctx コード生成コンテキスト
  /// @throws IOException ファイルの書き込みに失敗した場合
  static void writeForLibrary(GenContext ctx) throws IOException {
    Path target = ctx.mapperDir().resolve("StringMapper.java");
    Files.createDirectories(target.getParent());

    String content =
        """
        package %1$s.mapper;

        import java.nio.charset.Charset;
        import java.nio.charset.StandardCharsets;

        /**
         * CORBAサーバーが自分の文字コードのバイト列をISO-8859-1の文字列として受け渡すのを、Javaの文字列に読み替える。
         */
        public final class StringMapper {
          private static volatile Charset charset = toCharset("%2$s");

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
        """
            .formatted(ctx.basePackage(), ctx.settings().stringCharset());
    Files.writeString(
        target, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
  }

  /// StringMapperクラスを出力する
  ///
  /// CORBAサーバーは自分の文字コード（SJISなど）のバイト列をISO-8859-1の文字列として受け渡すので、`encode`で受け取った文字列を
  /// その文字コードで読み直し、`decode`でその逆を行う。
  ///
  /// 文字コードは生成し直さずに変えられるよう、application.ymlの`corba.string-charset`から読む。Mapperは静的メソッドで呼ばれるので、
  /// StringMapper自身をBeanにして、起動時に設定値を静的フィールドへ移す。設定が無いときやSpringの外では生成時の指定を使う。
  ///
  /// @param ctx コード生成コンテキスト
  /// @throws IOException ファイルの書き込みに失敗した場合
  static void write(GenContext ctx) throws IOException {
    Path target = ctx.mapperDir().resolve("StringMapper.java");
    Files.createDirectories(target.getParent());

    String content =
        """
        package %1$s.mapper;

        import java.nio.charset.Charset;
        import java.nio.charset.StandardCharsets;
        import org.springframework.beans.factory.annotation.Value;
        import org.springframework.stereotype.Component;

        @Component
        public final class StringMapper {
          private static volatile Charset charset = toCharset("%2$s");

          public StringMapper(@Value("${corba.string-charset:%2$s}") String charsetName) {
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
        """
            .formatted(ctx.basePackage(), ctx.settings().stringCharset());
    Files.writeString(
        target, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
  }
}
