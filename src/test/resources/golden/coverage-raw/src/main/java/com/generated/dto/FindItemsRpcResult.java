package com.generated.dto;

import Coverage.Tag;
import com.fasterxml.jackson.annotation.JsonProperty;

public class FindItemsRpcResult {
  @JsonProperty("return")
  public int returnValue;

  public Tag[] tags;

  public String message;
}
