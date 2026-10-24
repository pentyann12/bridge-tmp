package com.generated.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/")
public class ClockServiceController {
  private final com.generated.corba.ClockServiceCorbaClient client;

  public ClockServiceController(com.generated.corba.ClockServiceCorbaClient client) {
    this.client = client;
  }

  @PostMapping("/now")
  public com.generated.dto.NowRpcResponse now() {
    com.generated.dto.NowRpcResult __result = new com.generated.dto.NowRpcResult();
    __result.returnValue = client.now();
    com.generated.dto.NowRpcResponse __response = new com.generated.dto.NowRpcResponse();
    __response.result = __result;
    return __response;
  }

  @PostMapping("/zone")
  public com.generated.dto.ZoneRpcResponse zone(@RequestBody com.generated.dto.ZoneRpcRequest rpc) {
    com.generated.dto.ZoneRequestDto req = rpc.params;
    com.corba.Coverage.Tag arg0 = com.generated.mapper.TagMapper.fromDto(req.hint);
    com.generated.dto.ZoneRpcResult __result = new com.generated.dto.ZoneRpcResult();
    __result.returnValue = com.generated.mapper.StringMapper.encode(client.zone(arg0));
    com.generated.dto.ZoneRpcResponse __response = new com.generated.dto.ZoneRpcResponse();
    __response.result = __result;
    return __response;
  }


}
