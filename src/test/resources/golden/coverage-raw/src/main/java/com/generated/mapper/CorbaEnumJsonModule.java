package com.generated.mapper;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

/** IDLのenum（idljが生成したクラス）を、JSONでは選択肢の名前の文字列で書く。 */
@org.springframework.stereotype.Component
public class CorbaEnumJsonModule extends SimpleModule {
  public CorbaEnumJsonModule() {
    super("CorbaEnumJsonModule");
    addSerializer(Coverage.Status.class, byName(StatusMapper::toDto));
  }

  private static <T> ValueSerializer<T> byName(java.util.function.Function<T, ? extends Enum<?>> toDto) {
    return new ValueSerializer<T>() {
      @Override
      public void serialize(T value, JsonGenerator gen, SerializationContext ctxt) {
        gen.writeString(toDto.apply(value).name());
      }
    };
  }
}
