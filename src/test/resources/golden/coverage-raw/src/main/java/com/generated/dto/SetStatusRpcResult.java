package com.generated.dto;

import Coverage.Status;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SetStatusRpcResult {
  @JsonProperty("return")
  public Status returnValue;

  public Status[] history;
}
