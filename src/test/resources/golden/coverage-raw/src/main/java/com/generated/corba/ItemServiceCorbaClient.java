package com.generated.corba;

import org.omg.CORBA.ORB;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import Coverage.ItemService;
import Coverage.ItemServiceHelper;

@Component
public class ItemServiceCorbaClient {
  private final ItemService service;

  public ItemServiceCorbaClient(@Value("${corba.ItemService.ior}") String ior) {
    ORB orb = ORB.init(new String[0], null);
    org.omg.CORBA.Object obj = orb.string_to_object(ior);
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

}
