package com.linewell.dataelement.platform.tenant.protocol;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Keep the existing application security chain when the optional OIDC issuer is disabled. */
@Configuration(proxyBeanMethods = false)
public class ExistingApplicationSecurityConfiguration {
    @Bean
    @Order(100)
    public SecurityFilterChain existingApplicationSecurity(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(r -> r.anyRequest().permitAll())
                .csrf(c -> c.disable())
                .headers(h -> h.frameOptions(f -> f.disable()))
                .build();
    }
}
