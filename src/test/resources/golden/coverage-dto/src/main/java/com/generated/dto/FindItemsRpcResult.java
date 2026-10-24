package com.generated.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FindItemsRpcResult {
  @JsonProperty("return")
  public int returnValue;

  public TagDto[] tags;

  public String message;
}
