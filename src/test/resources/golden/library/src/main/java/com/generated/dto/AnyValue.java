package com.generated.dto;

/**
 * CORBAのAnyに入れる値（型名と値の組）。
 *
 * <p>typeは基本型なら"long"・"string"など、構造体ならIDL上の名前（例: "Coverage.Item"）。構造体のvalueはそのDTO。
 */
public record AnyValue(String type, Object value) {
  /**
   * 構造体のDTOから、型名を補ってAnyValueを作る。
   *
   * @throws IllegalArgumentException このライブラリが知らないクラスの場合
   */
  public static AnyValue of(Object dto) {
    String type = com.generated.mapper.AnyMapper.typeNameOf(dto.getClass());
    if (type == null) {
      throw new IllegalArgumentException(dto.getClass().getName() + " はAnyに入れられる構造体のDTOではありません");
    }
    return new AnyValue(type, dto);
  }

  /** 値が指定した型ならそれを返す。 */
  public <T> java.util.Optional<T> as(Class<T> valueType) {
    return valueType.isInstance(value) ? java.util.Optional.of(valueType.cast(value)) : java.util.Optional.empty();
  }
}
