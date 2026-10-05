package com.linewell.dataelement.dataservice.push;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DataPushRegistryProperties.class)
public class DataPushRegistryConfiguration { }
