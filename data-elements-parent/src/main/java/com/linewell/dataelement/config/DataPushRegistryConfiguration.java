package com.linewell.dataelement.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.transfer.haitong.HaitongStandaloneRegistryClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DataPushRegistryProperties.class)
@PropertySource("classpath:haitong-relay.properties")
public class DataPushRegistryConfiguration {
    @Bean(name = {"haitongStandaloneRegistryClient", "haitongPushCredentialClient"})
    HaitongStandaloneRegistryClient haitongStandaloneRegistryClient(DataPushRegistryProperties properties, ObjectMapper mapper) {
        return new HaitongStandaloneRegistryClient(properties, mapper);
    }
}
