package com.generated.mapper;

import Coverage.Tag;
import com.generated.dto.TagDto;

public final class TagMapper {
  public static TagDto toDto(Tag src) {
    TagDto dto = new TagDto();
    dto.key = StringMapper.encode(src.key);
    dto.value = StringMapper.encode(src.value);
    return dto;
  }

  public static Tag fromDto(TagDto src) {
    Tag dst = new Tag();
    dst.key = StringMapper.decode(src.key);
    dst.value = StringMapper.decode(src.value);
    return dst;
  }
}
