package com.generated.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.Object;
import org.omg.CORBA.Any;

public class OutAnyRpcResult {
  @JsonProperty("return")
  public Object returnValue;

  public Any value;
}
