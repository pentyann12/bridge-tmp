package com.generated.corba;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import Coverage.ItemService;
import Coverage.ItemServiceHelper;

@Component
public class ItemServiceCorbaClient {
  private final ItemService service;

  public ItemServiceCorbaClient(CorbaOrb corbaOrb, @Value("${corba.ItemService.ior}") String ior) {
    org.omg.CORBA.Object obj = corbaOrb.orb().string_to_object(ior);
    this.service = ItemServiceHelper.narrow(obj);
  }

  public Coverage.Item getItem(int id) {
    return service.getItem(id);
  }

  public void putItem(Coverage.Item item) {
    service.putItem(item);
  }

  public Coverage.Tag[] listTags(String prefix) {
    return service.listTags(prefix);
  }

  public String[] echoNames(String[] names) {
    return service.echoNames(names);
  }

  public String greet(String name) {
    return service.greet(name);
  }

  public org.omg.CORBA.Any echoAny(org.omg.CORBA.Any value) {
    return service.echoAny(value);
  }

  public int findItems(String keyword, Coverage.TagSeqHolder tags, org.omg.CORBA.StringHolder message) {
    return service.findItems(keyword, tags, message);
  }

  public void touch(org.omg.CORBA.IntHolder counter) {
    service.touch(counter);
  }

  public void relabel(org.omg.CORBA.StringHolder label, Coverage.Inner.PointHolder p) {
    service.relabel(label, p);
  }

  public boolean ping() {
    return service.ping();
  }

  public String[] globalNames(String[] names) {
    return service.globalNames(names);
  }

  public Coverage.Inner.Point movePoint(Coverage.Inner.Point p, Coverage.Inner.PointHolder previous) {
    return service.movePoint(p, previous);
  }

  public void outAny(org.omg.CORBA.AnyHolder value) {
    service.outAny(value);
  }

  public Coverage.Status setStatus(int id, Coverage.Status status, Coverage.StatusSeqHolder history) {
    return service.setStatus(id, status, history);
  }

  public void cycleStatus(Coverage.StatusHolder status) {
    service.cycleStatus(status);
  }

}
