package com.generated.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SetStatusRpcResult {
  @JsonProperty("return")
  public StatusDto returnValue;

  public StatusDto[] history;
}
