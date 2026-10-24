package com.haitong;

import com.haitong.config.RelayProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(RelayProperties.class)
public class HaitongApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(HaitongApplication.class);
        application.run(args);
    }
}
