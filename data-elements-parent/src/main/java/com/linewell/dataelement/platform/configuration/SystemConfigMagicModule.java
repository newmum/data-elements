package com.linewell.dataelement.platform.configuration;

import java.util.Map;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

@Component
@MagicModule("systemConfig")
public class SystemConfigMagicModule {

    private final SystemConfigService service;
    private final LegacyIntegrationSecretsProperties legacySecrets;

    public SystemConfigMagicModule(SystemConfigService service,
                                   LegacyIntegrationSecretsProperties legacySecrets) {
        this.service = service;
        this.legacySecrets = legacySecrets;
    }

    @Comment("Read public portal settings through Redis cache-aside")
    public Map<String, Object> publicSettings() {
        return service.publicSettings();
    }

    @Comment("Read server-side non-sensitive runtime settings")
    public Map<String, Object> runtimeSettings() {
        return service.runtimeSettings();
    }

    @Comment("Read a non-sensitive system configuration group")
    public Map<String, Object> group(String group) {
        return service.group(group, false);
    }

    @Comment("Read one non-sensitive system configuration value")
    public Object value(String group, String code) {
        return service.value(group, code);
    }

    @Comment("读取 Nacos 中的旧版 SSO 客户端凭据；未配置时明确失败")
    public String legacySsoClientSecret() {
        return required(legacySecrets.getSsoClientSecret(),
                "请配置 data-element.legacy-integration.sso-client-secret");
    }

    @Comment("读取 Nacos 中的旧版对象存储凭据；未配置时明确失败")
    public String legacyMinioPassword() {
        return required(legacySecrets.getMinioPassword(),
                "请配置 data-element.legacy-integration.minio-password");
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
        return value;
    }

    @Comment("Evict the current tenant and global cache for a configuration group")
    public boolean evict(String group) {
        service.evict(group);
        return true;
    }
}
