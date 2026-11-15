package com.haitong.magic;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.ssssssss.magicapi.core.config.MagicAPIProperties;
import org.ssssssss.magicapi.core.model.ApiInfo;
import org.ssssssss.magicapi.core.service.impl.ApiInfoMagicResourceStorage;

@Configuration(proxyBeanMethods = false)
class MagicLeafRouteConfiguration {
    @Bean
    ApiInfoMagicResourceStorage apiInfoMagicResourceStorage(MagicAPIProperties properties) {
        return new ApiInfoMagicResourceStorage(properties.getPrefix()) {
            @Override
            public String buildMappingKey(ApiInfo api, String groupPath) {
                String key = super.buildMappingKey(api, groupPath);
                // 文件与数据库资源均通过此处注册显式声明的叶组根路由。
                if ("/".equals(api.getPath()) && api.getProperties() != null
                        && Boolean.TRUE.equals(api.getAttribute("leafRoute")) && !key.endsWith(":/")) {
                    return key.substring(0, key.length() - 1);
                }
                return key;
            }
        };
    }
}
