package com.generated.corba;

import java.util.Properties;
import org.omg.CORBA.ORB;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class CorbaOrb implements DisposableBean {
  private static final String GMBAL_PROPERTY = "org.glassfish.gmbal.no.multipleUpperBoundsException";
  private static final String REGISTER_MBEANS_PROPERTY = "com.sun.corba.ee.ORBRegisterMBeans";

  private final ORB orb;

  public CorbaOrb(Environment env) {
    if (System.getProperty(GMBAL_PROPERTY) == null) {
      System.setProperty(GMBAL_PROPERTY, "true");
    }
    Properties properties = new Properties();
    properties.setProperty(REGISTER_MBEANS_PROPERTY, "false");
    Binder.get(env).bind("corba.orb", Bindable.mapOf(String.class, String.class)).ifBound(properties::putAll);
    this.orb = ORB.init(new String[0], properties);
    com.generated.mapper.AnyMapper.useOrb(orb);
  }

  public ORB orb() {
    return orb;
  }

  @Override
  public void destroy() {
    com.generated.mapper.AnyMapper.releaseOrb(orb);
    orb.shutdown(false);
    orb.destroy();
  }
}
