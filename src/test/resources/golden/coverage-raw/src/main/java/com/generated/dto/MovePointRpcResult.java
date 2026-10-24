package com.generated.dto;

import Coverage.Inner.Point;
import com.fasterxml.jackson.annotation.JsonProperty;

public class MovePointRpcResult {
  @JsonProperty("return")
  public Point returnValue;

  public Point previous;
}
