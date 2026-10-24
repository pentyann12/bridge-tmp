package com.generated.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/")
public class ItemServiceController {
  private final com.generated.corba.ItemServiceCorbaClient client;

  public ItemServiceController(com.generated.corba.ItemServiceCorbaClient client) {
    this.client = client;
  }

  @PostMapping("/getItem")
  public com.generated.dto.GetItemRpcResponse getItem(@RequestBody com.generated.dto.GetItemRpcRequest rpc) {
    com.generated.dto.GetItemRequestDto req = rpc.params;
    int arg0 = req.id;
    com.corba.Coverage.Item result = client.getItem(arg0);
    com.generated.dto.GetItemRpcResult __result = new com.generated.dto.GetItemRpcResult();
    __result.returnValue = com.generated.mapper.ItemMapper.toDto(result);
    com.generated.dto.GetItemRpcResponse __response = new com.generated.dto.GetItemRpcResponse();
    __response.result = __result;
    return __response;
  }

  @PostMapping("/putItem")
  public com.generated.dto.PutItemRpcResponse putItem(@RequestBody com.generated.dto.PutItemRpcRequest rpc) {
    com.generated.dto.PutItemRequestDto req = rpc.params;
    com.corba.Coverage.Item arg0 = com.generated.mapper.ItemMapper.fromDto(req.item);
    client.putItem(arg0);
    com.generated.dto.PutItemRpcResult __result = new com.generated.dto.PutItemRpcResult();
    __result.returnValue = null;
    com.generated.dto.PutItemRpcResponse __response = new com.generated.dto.PutItemRpcResponse();
    __response.result = __result;
    return __response;
  }

  @PostMapping("/listTags")
  public com.generated.dto.ListTagsRpcResponse listTags(@RequestBody com.generated.dto.ListTagsRpcRequest rpc) {
    com.generated.dto.ListTagsRequestDto req = rpc.params;
    String arg0 = com.generated.mapper.StringMapper.decode(req.prefix);
    com.corba.Coverage.Tag[] result = client.listTags(arg0);
    com.generated.dto.ListTagsRpcResult __result = new com.generated.dto.ListTagsRpcResult();
    __result.returnValue = result == null ? null : java.util.Arrays.stream(result).map(com.generated.mapper.TagMapper::toDto).toArray(com.generated.dto.TagDto[]::new);
    com.generated.dto.ListTagsRpcResponse __response = new com.generated.dto.ListTagsRpcResponse();
    __response.result = __result;
    return __response;
  }

  @PostMapping("/echoNames")
  public com.generated.dto.EchoNamesRpcResponse echoNames(@RequestBody com.generated.dto.EchoNamesRpcRequest rpc) {
    com.generated.dto.EchoNamesRequestDto req = rpc.params;
    String[] arg0 = req.names == null ? null : java.util.Arrays.stream(req.names).map(com.generated.mapper.StringMapper::decode).toArray(String[]::new);
    String[] result = client.echoNames(arg0);
    com.generated.dto.EchoNamesRpcResult __result = new com.generated.dto.EchoNamesRpcResult();
    __result.returnValue = result == null ? null : java.util.Arrays.stream(result).map(com.generated.mapper.StringMapper::encode).toArray(String[]::new);
    com.generated.dto.EchoNamesRpcResponse __response = new com.generated.dto.EchoNamesRpcResponse();
    __response.result = __result;
    return __response;
  }

  @PostMapping("/greet")
  public com.generated.dto.GreetRpcResponse greet(@RequestBody com.generated.dto.GreetRpcRequest rpc) {
    com.generated.dto.GreetRequestDto req = rpc.params;
    String arg0 = com.generated.mapper.StringMapper.decode(req.name);
    com.generated.dto.GreetRpcResult __result = new com.generated.dto.GreetRpcResult();
    __result.returnValue = com.generated.mapper.StringMapper.encode(client.greet(arg0));
    com.generated.dto.GreetRpcResponse __response = new com.generated.dto.GreetRpcResponse();
    __response.result = __result;
    return __response;
  }

  @PostMapping("/echoAny")
  public com.generated.dto.EchoAnyRpcResponse echoAny(@RequestBody com.generated.dto.EchoAnyRpcRequest rpc) {
    com.generated.dto.EchoAnyRequestDto req = rpc.params;
    org.omg.CORBA.Any arg0 = com.generated.mapper.AnyMapper.toAny(req.value);
    com.generated.dto.EchoAnyRpcResult __result = new com.generated.dto.EchoAnyRpcResult();
    __result.returnValue = com.generated.mapper.AnyMapper.toAnyValue(client.echoAny(arg0));
    com.generated.dto.EchoAnyRpcResponse __response = new com.generated.dto.EchoAnyRpcResponse();
    __response.result = __result;
    return __response;
  }

  @PostMapping("/findItems")
  public com.generated.dto.FindItemsRpcResponse findItems(@RequestBody com.generated.dto.FindItemsRpcRequest rpc) {
    com.generated.dto.FindItemsRequestDto req = rpc.params;
    String arg0 = com.generated.mapper.StringMapper.decode(req.keyword);
    com.corba.Coverage.TagSeqHolder arg1 = new com.corba.Coverage.TagSeqHolder();
    org.omg.CORBA.StringHolder arg2 = new org.omg.CORBA.StringHolder();
    int callResult = client.findItems(arg0, arg1, arg2);
    com.generated.dto.FindItemsRpcResult __result = new com.generated.dto.FindItemsRpcResult();
    __result.returnValue = callResult;
    __result.tags = arg1.value == null ? null : java.util.Arrays.stream(arg1.value).map(com.generated.mapper.TagMapper::toDto).toArray(com.generated.dto.TagDto[]::new);
    __result.message = com.generated.mapper.StringMapper.encode(arg2.value);
    com.generated.dto.FindItemsRpcResponse __response = new com.generated.dto.FindItemsRpcResponse();
    __response.result = __result;
    return __response;
  }

  @PostMapping("/touch")
  public com.generated.dto.TouchRpcResponse touch() {
    org.omg.CORBA.IntHolder arg0 = new org.omg.CORBA.IntHolder();
    client.touch(arg0);
    com.generated.dto.TouchRpcResult __result = new com.generated.dto.TouchRpcResult();
    __result.counter = arg0.value;
    com.generated.dto.TouchRpcResponse __response = new com.generated.dto.TouchRpcResponse();
    __response.result = __result;
    return __response;
  }

  @PostMapping("/ping")
  public com.generated.dto.PingRpcResponse ping() {
    com.generated.dto.PingRpcResult __result = new com.generated.dto.PingRpcResult();
    __result.returnValue = client.ping();
    com.generated.dto.PingRpcResponse __response = new com.generated.dto.PingRpcResponse();
    __response.result = __result;
    return __response;
  }

  @PostMapping("/globalNames")
  public com.generated.dto.GlobalNamesRpcResponse globalNames(@RequestBody com.generated.dto.GlobalNamesRpcRequest rpc) {
    com.generated.dto.GlobalNamesRequestDto req = rpc.params;
    String[] arg0 = req.names == null ? null : java.util.Arrays.stream(req.names).map(com.generated.mapper.StringMapper::decode).toArray(String[]::new);
    String[] result = client.globalNames(arg0);
    com.generated.dto.GlobalNamesRpcResult __result = new com.generated.dto.GlobalNamesRpcResult();
    __result.returnValue = result == null ? null : java.util.Arrays.stream(result).map(com.generated.mapper.StringMapper::encode).toArray(String[]::new);
    com.generated.dto.GlobalNamesRpcResponse __response = new com.generated.dto.GlobalNamesRpcResponse();
    __response.result = __result;
    return __response;
  }

  @PostMapping("/movePoint")
  public com.generated.dto.MovePointRpcResponse movePoint(@RequestBody com.generated.dto.MovePointRpcRequest rpc) {
    com.generated.dto.MovePointRequestDto req = rpc.params;
    com.corba.Coverage.Inner.Point arg0 = com.generated.mapper.PointMapper.fromDto(req.p);
    com.corba.Coverage.Inner.PointHolder arg1 = new com.corba.Coverage.Inner.PointHolder();
    com.corba.Coverage.Inner.Point callResult = client.movePoint(arg0, arg1);
    com.generated.dto.MovePointRpcResult __result = new com.generated.dto.MovePointRpcResult();
    __result.returnValue = com.generated.mapper.PointMapper.toDto(callResult);
    __result.previous = com.generated.mapper.PointMapper.toDto(arg1.value);
    com.generated.dto.MovePointRpcResponse __response = new com.generated.dto.MovePointRpcResponse();
    __response.result = __result;
    return __response;
  }

  @PostMapping("/outAny")
  public com.generated.dto.OutAnyRpcResponse outAny() {
    org.omg.CORBA.AnyHolder arg0 = new org.omg.CORBA.AnyHolder();
    client.outAny(arg0);
    com.generated.dto.OutAnyRpcResult __result = new com.generated.dto.OutAnyRpcResult();
    __result.value = com.generated.mapper.AnyMapper.toAnyValue(arg0.value);
    com.generated.dto.OutAnyRpcResponse __response = new com.generated.dto.OutAnyRpcResponse();
    __response.result = __result;
    return __response;
  }


}
