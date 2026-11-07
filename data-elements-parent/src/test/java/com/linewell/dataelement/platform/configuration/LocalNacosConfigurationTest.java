package com.linewell.dataelement.platform.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LocalNacosConfigurationTest {
    @TempDir Path temporary;

    @Test
    void completeLocalConfigurationLoadsWithoutRemoteImportsEvenWithStaleHighPriorityJson() {
        assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
            Path repository = Path.of(System.getProperty("backend.javaSourceDirectory")).toAbsolutePath()
                    .getParent().getParent().getParent().getParent();
            Path local = temporary.resolve("configuration with spaces");
            Files.createDirectories(local);
            Path business = Files.copy(repository.resolve("db/nacos/data-element_ct.yml"), local.resolve("data-element_ct.yml"));
            Path mode = Files.copy(repository.resolve("db/nacos/application-no-nacos.yml"), local.resolve("application-no-nacos.yml"));
            SpringApplication application = new SpringApplication(LocalConfigurationProbe.class);
            application.setWebApplicationType(WebApplicationType.NONE);
            application.setRegisterShutdownHook(false);
            application.setLogStartupInfo(false);
            application.setDefaultProperties(Map.of("spring.cloud.nacos.server-addr", "127.0.0.1:1"));
            try (ConfigurableApplicationContext context = application.run(
                    "--spring.config.location=" + business.toUri() + "," + mode.toUri(),
                    "--spring.config.import=",
                    "--spring.application.json={\"spring\":{\"config\":{\"import\":\"nacos:unavailable.yml\"},\"cloud\":{\"nacos\":{\"config\":{\"enabled\":true}}}}}",
                    "--spring.cloud.nacos.config.enabled=false",
                    "--spring.cloud.nacos.config.import-check.enabled=false",
                    "--spring.cloud.nacos.discovery.enabled=false",
                    "--spring.cloud.discovery.enabled=false",
                    "--spring.cloud.service-registry.auto-registration.enabled=false")) {
                var environment = context.getEnvironment();
                assertEquals("data-element", environment.getProperty("spring.application.name"));
                assertEquals("", environment.getProperty("spring.config.import"));
                assertEquals("false", environment.getProperty("spring.cloud.nacos.config.enabled"));
                assertEquals("false", environment.getProperty("spring.cloud.nacos.discovery.enabled"));
                assertEquals("false", environment.getProperty("spring.cloud.service-registry.auto-registration.enabled"));
                assertEquals("database", environment.getProperty("magic-api.resource.type"));
                assertEquals("chengtian", environment.getProperty("data-element.tenant.database.default-data-source-key"));
                assertEquals("public", environment.getProperty("data-element.tenant.database.data-sources.chengtian.schema"));
                assertTrue(environment.getProperty("data-element.tenant.database.data-sources.chengtian.url").startsWith("jdbc:kingbase8:"));
                assertTrue(environment.getProperty("spring.datasource.url").contains("/baseline?"));
                assertNotNull(environment.getProperty("spring.redis.host"));
                assertNotNull(environment.getProperty("elasticsearch.host"));
            }
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class LocalConfigurationProbe { }
}
