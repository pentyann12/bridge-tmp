package com.generated.corba;

import org.omg.CORBA.ORB;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import Other.ClockService;
import Other.ClockServiceHelper;

@Component
public class ClockServiceCorbaClient {
  private final ClockService service;

  public ClockServiceCorbaClient(@Value("${corba.ClockService.ior}") String ior) {
    ORB orb = ORB.init(new String[0], null);
    org.omg.CORBA.Object obj = orb.string_to_object(ior);
    this.service = ClockServiceHelper.narrow(obj);
  }

  public long now() {
    return service.now();
  }

  public String zone(Coverage.Tag hint) {
    return service.zone(hint);
  }

}
