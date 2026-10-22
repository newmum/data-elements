package com.haitong.config;

import org.apache.coyote.http11.Http11Nio2Protocol;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Uses Tomcat's asynchronous Windows-compatible connector rather than the NIO poller.
 * Some Windows/JDK builds cannot create the local pipe used by the NIO selector.
 */
@Configuration(proxyBeanMethods = false)
public class TomcatProtocolConfiguration {
    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatNio2Protocol() {
        return factory -> factory.setProtocol(Http11Nio2Protocol.class.getName());
    }
}
