package com.toru_otsuki.bridge_generator.gen;

import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.TypeSpec;
import com.toru_otsuki.bridge_generator.model.Field;
import com.toru_otsuki.bridge_generator.model.Struct;
import java.io.IOException;
import javax.lang.model.element.Modifier;

/// 構造体ごとのDTOクラス（`XxxDto`）を生成する
final class DtoGenerator {
  private DtoGenerator() {}

  static void generate(Struct struct, GenContext ctx) throws IOException {
    TypeMapping types = ctx.types();
    TypeSpec.Builder dto =
        TypeSpec.classBuilder(struct.name() + "Dto").addModifiers(Modifier.PUBLIC);
    for (Field field : struct.fields()) {
      dto.addField(
          FieldSpec.builder(types.dtoType(field.type()), field.name(), Modifier.PUBLIC).build());
    }
    JavaFile.builder(types.dtoPackage(), dto.build()).build().writeTo(ctx.javaDir());
  }
}
