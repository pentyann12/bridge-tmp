package com.generated.corba;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.corba.Other.ClockService;
import com.corba.Other.ClockServiceHelper;

@Component
public class ClockServiceCorbaClient {
  private final ClockService service;

  public ClockServiceCorbaClient(CorbaOrb corbaOrb, @Value("${corba.ClockService.ior}") String ior) {
    org.omg.CORBA.Object obj = corbaOrb.orb().string_to_object(ior);
    this.service = ClockServiceHelper.narrow(obj);
  }

  public long now() {
    return service.now();
  }

  public String zone(com.corba.Coverage.Tag hint) {
    return service.zone(hint);
  }

}
