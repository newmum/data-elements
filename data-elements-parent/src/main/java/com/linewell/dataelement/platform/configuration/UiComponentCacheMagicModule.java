package com.linewell.dataelement.platform.configuration;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

@Component
@MagicModule("uiComponentCache")
public class UiComponentCacheMagicModule {

    private final UiComponentCacheService service;

    public UiComponentCacheMagicModule(UiComponentCacheService service) {
        this.service = service;
    }

    @Comment("Read active low-code components through Redis cache-aside")
    public List<Map<String, Object>> list() {
        return service.list();
    }

    @Comment("Evict the active low-code component cache")
    public boolean evict() {
        service.evict();
        return true;
    }

    @Comment("Refresh the shared low-code component cache and return its version and item count")
    public Map<String, Object> refresh() {
        return service.refresh();
    }
}
