package com.linewell.dataelement;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableConfigurationProperties
@SpringBootApplication(exclude = {
        org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration.class,
        org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration.class
})
@MapperScan({
        "com.linewell.dataelement.*.base.mapper",
        "com.linewell.dataelement.platform.tenant.infrastructure.persistence.mapper",
        "com.linewell.dataelement.feature.identity.infrastructure.persistence.mapper",
        "com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.mapper",
        "com.linewell.dataelement.feature.approval.infrastructure.persistence.mapper",
        "com.linewell.dataelement.feature.reconciliation.infrastructure.persistence.mapper",
        "com.linewell.dataelement.feature.dataquality.infrastructure.persistence.mapper",
        "com.linewell.dataelement.feature.delivery.infrastructure.persistence.mapper",
        "org.dromara.warm.flow.orm.mapper"
})
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class DataElementApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataElementApplication.class, args);
    }

}
