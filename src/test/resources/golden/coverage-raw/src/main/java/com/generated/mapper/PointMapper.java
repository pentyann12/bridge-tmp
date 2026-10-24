package com.generated.mapper;

import Coverage.Inner.Point;
import com.generated.dto.PointDto;

public final class PointMapper {
  public static PointDto toDto(Point src) {
    PointDto dto = new PointDto();
    dto.x = src.x;
    dto.y = src.y;
    return dto;
  }

  public static Point fromDto(PointDto src) {
    Point dst = new Point();
    dst.x = src.x;
    dst.y = src.y;
    return dst;
  }
}
