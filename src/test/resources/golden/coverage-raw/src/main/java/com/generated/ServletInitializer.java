package com.generated;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

public class ServletInitializer extends SpringBootServletInitializer {
  @Override
  protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
    System.setProperty(
      "org.glassfish.gmbal.no.multipleUpperBoundsException",
      "true");
    return application.sources(Application.class);
  }
}
