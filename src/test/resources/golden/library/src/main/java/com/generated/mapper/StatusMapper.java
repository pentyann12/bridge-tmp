package com.generated.mapper;

import com.corba.Coverage.Status;
import com.generated.dto.StatusDto;

public final class StatusMapper {
  public static StatusDto toDto(Status src) {
    return src == null ? null : StatusDto.values()[src.value()];
  }

  public static Status fromDto(StatusDto src) {
    return src == null ? null : Status.from_int(src.ordinal());
  }
}
