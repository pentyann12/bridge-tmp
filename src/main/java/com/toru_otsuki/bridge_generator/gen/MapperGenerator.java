package com.toru_otsuki.bridge_generator.gen;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import com.toru_otsuki.bridge_generator.model.Field;
import com.toru_otsuki.bridge_generator.model.Struct;
import java.io.IOException;
import javax.lang.model.element.Modifier;

/// 構造体ごとのCORBA⇔DTO変換クラス（`XxxMapper`）を生成する
final class MapperGenerator {
  private MapperGenerator() {}

  /// `toDto(Xxx)`と`fromDto(XxxDto)`を持つMapperクラスを出力する。フィールドごとの変換式は[TypeMapping]が決める。
  ///
  /// 構造体の中身は`--return-mode`によらず常にDTOに変換する。
  ///
  /// @param struct 構造体のモデル
  /// @param ctx コード生成コンテキスト
  /// @throws IOException ファイルの書き込みに失敗した場合
  static void generate(Struct struct, GenContext ctx) throws IOException {
    TypeMapping types = ctx.types();
    ClassName dtoClass = types.dtoClass(struct.name() + "Dto");
    ClassName corbaClass = ClassName.get(struct.pkg(), struct.name());

    MethodSpec.Builder toDto =
        MethodSpec.methodBuilder("toDto")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(dtoClass)
            .addParameter(corbaClass, "src")
            .addStatement("$T dto = new $T()", dtoClass, dtoClass);
    MethodSpec.Builder fromDto =
        MethodSpec.methodBuilder("fromDto")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(corbaClass)
            .addParameter(dtoClass, "src")
            .addStatement("$T dst = new $T()", corbaClass, corbaClass);

    for (Field field : struct.fields()) {
      CodeBlock src = CodeBlock.of("src.$N", field.name());
      toDto.addStatement("dto.$N = $L", field.name(), types.toDto(field.type(), src, true));
      fromDto.addStatement("dst.$N = $L", field.name(), types.fromDto(field.type(), src));
    }

    TypeSpec mapper =
        TypeSpec.classBuilder(struct.name() + "Mapper")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addMethod(toDto.addStatement("return dto").build())
            .addMethod(fromDto.addStatement("return dst").build())
            .build();
    JavaFile.builder(types.mapperPackage(), mapper).build().writeTo(ctx.javaDir());
  }
}
