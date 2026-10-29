package com.generated.spring;

import com.generated.client.*;
import com.generated.mapper.StringMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Spring Bootの自動設定。CorbaConnectionと、corba.サービス名.ior が設定されたサービスのクライアントをBeanにする。
 *
 * <p>corba.string-charset で、CORBAサーバーが文字列に使う文字コードを指定できる（既定は SJIS、noneなら読み替えない）。
 * corba.orb.* はORBの設定としてそのまま渡す（例: corba.orb.[org.omg.CORBA.ORBInitialHost]）。
 */
@AutoConfiguration
public class CorbaClientAutoConfiguration {
  public CorbaClientAutoConfiguration(Environment env) {
    StringMapper.configure(env.getProperty("corba.string-charset", "SJIS"));
  }

  @Bean(destroyMethod = "close")
  @ConditionalOnMissingBean
  public CorbaConnection corbaConnection(Environment env) {
    java.util.Properties orbProperties = new java.util.Properties();
    Binder.get(env).bind("corba.orb", Bindable.mapOf(String.class, String.class)).ifBound(orbProperties::putAll);
    return new CorbaConnection(orbProperties);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty("corba.ItemService.ior")
  public ItemServiceClient itemServiceClient(CorbaConnection connection, Environment env) {
    return new ItemServiceClient(connection, env.getRequiredProperty("corba.ItemService.ior"));
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty("corba.Audited.ior")
  public AuditedClient auditedClient(CorbaConnection connection, Environment env) {
    return new AuditedClient(connection, env.getRequiredProperty("corba.Audited.ior"));
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty("corba.Guarded.ior")
  public GuardedClient guardedClient(CorbaConnection connection, Environment env) {
    return new GuardedClient(connection, env.getRequiredProperty("corba.Guarded.ior"));
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty("corba.Reviewed.ior")
  public ReviewedClient reviewedClient(CorbaConnection connection, Environment env) {
    return new ReviewedClient(connection, env.getRequiredProperty("corba.Reviewed.ior"));
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty("corba.Supervised.ior")
  public SupervisedClient supervisedClient(CorbaConnection connection, Environment env) {
    return new SupervisedClient(connection, env.getRequiredProperty("corba.Supervised.ior"));
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty("corba.ClockService.ior")
  public ClockServiceClient clockServiceClient(CorbaConnection connection, Environment env) {
    return new ClockServiceClient(connection, env.getRequiredProperty("corba.ClockService.ior"));
  }
}
