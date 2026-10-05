package com.linewell.dataelement.platform.integration.pingao;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class PingaoApplicationClientWiringTest {

    @Test
    void usesTheApplicationConstructorForSpringInjection() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(PingaoApplicationProperties.class);
            context.registerBean(ObjectMapper.class);
            context.registerBean(PingaoApplicationClient.class);
            context.refresh();

            assertNotNull(context.getBean(PingaoApplicationClient.class));
        }
    }
}
