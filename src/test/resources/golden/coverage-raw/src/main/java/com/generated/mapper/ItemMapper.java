package com.generated.mapper;

import Coverage.Item;
import Coverage.Tag;
import com.generated.dto.ItemDto;
import com.generated.dto.TagDto;

public final class ItemMapper {
  public static ItemDto toDto(Item src) {
    ItemDto dto = new ItemDto();
    dto.id = src.id;
    dto.name = StringMapper.encode(src.name);
    dto.price = src.price;
    dto.active = src.active;
    dto.extra = AnyMapper.toAnyValue(src.extra);
    dto.counts = src.counts;
    dto.labels = src.labels == null ? null : java.util.Arrays.stream(src.labels).map(StringMapper::encode).toArray(String[]::new);
    dto.tags = src.tags == null ? null : java.util.Arrays.stream(src.tags).map(TagMapper::toDto).toArray(TagDto[]::new);
    dto.aliases = src.aliases == null ? null : java.util.Arrays.stream(src.aliases).map(StringMapper::encode).toArray(String[]::new);
    dto.location = PointMapper.toDto(src.location);
    dto.status = StatusMapper.toDto(src.status);
    return dto;
  }

  public static Item fromDto(ItemDto src) {
    Item dst = new Item();
    dst.id = src.id;
    dst.name = StringMapper.decode(src.name);
    dst.price = src.price;
    dst.active = src.active;
    dst.extra = AnyMapper.toAny(src.extra);
    dst.counts = src.counts;
    dst.labels = src.labels == null ? null : java.util.Arrays.stream(src.labels).map(StringMapper::decode).toArray(String[]::new);
    dst.tags = src.tags == null ? null : java.util.Arrays.stream(src.tags).map(TagMapper::fromDto).toArray(Tag[]::new);
    dst.aliases = src.aliases == null ? null : java.util.Arrays.stream(src.aliases).map(StringMapper::decode).toArray(String[]::new);
    dst.location = PointMapper.fromDto(src.location);
    dst.status = StatusMapper.fromDto(src.status);
    return dst;
  }
}
