package com.toru_otsuki.bridge_generator.gen;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import com.toru_otsuki.bridge_generator.model.IdlEnum;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Modifier;

/// IDLの`enum`ごとに、DTO（Javaのenum `XxxDto`）とCORBA⇔DTO変換クラス（`XxxMapper`）を生成する
///
/// DTOをJavaのenumにするので、JSONでは選択肢の名前の文字列（`"ACTIVE"`）になる。CORBAで送る番号はJSONに出さない。
/// 変換は番号どうしで行う（DTOの`ordinal()`とidljのクラスの`value()`/`from_int`）。DTOの選択肢はIDLの宣言順に並べる。
final class EnumGenerator {
  private EnumGenerator() {}

  /// DTOとMapperを出力する
  ///
  /// @param idlEnum enumのモデル
  /// @param ctx コード生成コンテキスト
  /// @throws IOException ファイルの書き込みに失敗した場合
  static void generate(IdlEnum idlEnum, GenContext ctx) throws IOException {
    TypeMapping types = ctx.types();
    ClassName dtoClass = types.dtoClass(idlEnum.type());
    ClassName corbaClass = ClassName.get(idlEnum.pkg(), idlEnum.name());

    TypeSpec.Builder dto = TypeSpec.enumBuilder(dtoClass).addModifiers(Modifier.PUBLIC);
    for (String member : idlEnum.members()) {
      dto.addEnumConstant(constantName(member));
    }
    JavaFile.builder(types.dtoPackage(), dto.build()).build().writeTo(ctx.javaDir());

    TypeSpec mapper =
        TypeSpec.classBuilder(idlEnum.name() + "Mapper")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addMethod(
                MethodSpec.methodBuilder("toDto")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .returns(dtoClass)
                    .addParameter(corbaClass, "src")
                    .addStatement("return src == null ? null : $T.values()[src.value()]", dtoClass)
                    .build())
            .addMethod(
                MethodSpec.methodBuilder("fromDto")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .returns(corbaClass)
                    .addParameter(dtoClass, "src")
                    .addStatement(
                        "return src == null ? null : $T.from_int(src.ordinal())", corbaClass)
                    .build())
            .build();
    JavaFile.builder(types.mapperPackage(), mapper).build().writeTo(ctx.javaDir());
  }

  /// RESTブリッジの`--return-mode=raw`用に、idljのenumのクラスをJSONに書くJacksonのモジュール（`CorbaEnumJsonModule`）を出力する
  ///
  /// rawでは戻り値や構造体のフィールドのenumがidljのクラスのまま返る。そのクラスには公開フィールドもgetterもなく、
  /// Jacksonがそのままでは書けないので、DTOと同じく選択肢の名前の文字列にする。Spring BootはJacksonのモジュールのBeanを
  /// 自動で登録する。enumが無いか、DTOに変換するとき（`--return-mode=dto`）は出力しない。
  ///
  /// @param enums enumのモデル
  /// @param ctx コード生成コンテキスト
  /// @throws IOException ファイルの書き込みに失敗した場合
  static void writeRawJsonModule(List<IdlEnum> enums, GenContext ctx) throws IOException {
    if (enums.isEmpty() || ctx.returnAsDto()) return;
    String registrations =
        enums.stream()
            .map(
                e ->
                    "    addSerializer(%s.class, byName(%sMapper::toDto));\n"
                        .formatted(
                            e.pkg().isEmpty() ? e.name() : e.pkg() + "." + e.name(), e.name()))
            .collect(Collectors.joining());
    Files.writeString(
        ctx.mapperDir().resolve("CorbaEnumJsonModule.java"),
        """
        package %s.mapper;

        import tools.jackson.core.JsonGenerator;
        import tools.jackson.databind.SerializationContext;
        import tools.jackson.databind.ValueSerializer;
        import tools.jackson.databind.module.SimpleModule;

        /** IDLのenum（idljが生成したクラス）を、JSONでは選択肢の名前の文字列で書く。 */
        @org.springframework.stereotype.Component
        public class CorbaEnumJsonModule extends SimpleModule {
          public CorbaEnumJsonModule() {
            super("CorbaEnumJsonModule");
        %s  }

          private static <T> ValueSerializer<T> byName(java.util.function.Function<T, ? extends Enum<?>> toDto) {
            return new ValueSerializer<T>() {
              @Override
              public void serialize(T value, JsonGenerator gen, SerializationContext ctxt) {
                gen.writeString(toDto.apply(value).name());
              }
            };
          }
        }
        """
            .formatted(ctx.basePackage(), registrations));
  }

  /// IDLでは使えてもJavaの予約語になる名前（`class`など）は、idljと同じく先頭に`_`を付ける。JSONでもその名前になる。
  private static String constantName(String member) {
    return SourceVersion.isKeyword(member) ? "_" + member : member;
  }
}
