package com.example.observability;

import io.micrometer.observation.ObservationPredicate;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration(proxyBeanMethods = false)
public class SpringBootAdminObservationConfig {

	@Bean
	ObservationPredicate ignoreSpringBootAdmin() {
		return (name, context) -> {
			if (context instanceof ServerRequestObservationContext server) {
				return !server.getCarrier().getRequestURI().contains("/actuator");
			}
			if (context instanceof ClientRequestObservationContext client && client.getCarrier() != null) {
				return !client.getCarrier().getURI().getPath().contains("/instances");
			}
			return !(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
					&& attributes.getRequest().getRequestURI().contains("/actuator"));
		};
	}

}
