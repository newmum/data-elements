package com.linewell.dataelement.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DataPushRegistryProperties.class)
public class DataPushRegistryConfiguration { }
