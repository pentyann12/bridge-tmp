package com.generated.mapper;

import com.corba.Errors.AccessDeniedException;
import com.generated.dto.AccessDeniedExceptionDto;

public final class AccessDeniedExceptionMapper {
  public static AccessDeniedExceptionDto toDto(AccessDeniedException src) {
    AccessDeniedExceptionDto dto = new AccessDeniedExceptionDto();
    dto.who = StringMapper.encode(src.who);
    return dto;
  }

  public static AccessDeniedException fromDto(AccessDeniedExceptionDto src) {
    AccessDeniedException dst = new AccessDeniedException();
    dst.who = StringMapper.decode(src.who);
    return dst;
  }
}
