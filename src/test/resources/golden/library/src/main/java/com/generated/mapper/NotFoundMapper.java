package com.generated.mapper;

import com.corba.Errors.NotFound;
import com.generated.dto.NotFoundDto;

public final class NotFoundMapper {
  public static NotFoundDto toDto(NotFound src) {
    NotFoundDto dto = new NotFoundDto();
    dto.id = src.id;
    dto.reason = StringMapper.encode(src.reason);
    return dto;
  }

  public static NotFound fromDto(NotFoundDto src) {
    NotFound dst = new NotFound();
    dst.id = src.id;
    dst.reason = StringMapper.decode(src.reason);
    return dst;
  }
}
