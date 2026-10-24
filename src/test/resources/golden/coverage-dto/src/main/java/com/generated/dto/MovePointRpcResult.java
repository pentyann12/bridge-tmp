package com.generated.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MovePointRpcResult {
  @JsonProperty("return")
  public PointDto returnValue;

  public PointDto previous;
}
