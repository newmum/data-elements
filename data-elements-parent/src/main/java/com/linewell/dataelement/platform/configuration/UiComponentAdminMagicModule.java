package com.linewell.dataelement.platform.configuration;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

@Component
@MagicModule("uiComponentAdmin")
public class UiComponentAdminMagicModule {

    private final UiComponentAdminService service;

    public UiComponentAdminMagicModule(UiComponentAdminService service) {
        this.service = service;
    }

    @Comment("Read the shared low-code component tree from the control database")
    public Map<String, Object> tree() {
        return service.tree();
    }

    @Comment("Return an empty string when a component name is available")
    public String duplicateMessage(Map<String, Object> body) {
        return service.duplicateMessage(body);
    }

    @Comment("Create or update shared low-code component metadata")
    public String save(Map<String, Object> body) {
        return service.save(body);
    }

    @Comment("Save source and compiled output with one history snapshot")
    public Map<String, Object> saveCode(Map<String, Object> body) {
        return service.saveCode(body);
    }

    @Comment("Soft-delete one component subtree")
    public int delete(String tid) {
        return service.delete(tid);
    }

    @Comment("Read shared component source")
    public String sourceCode(String tid) {
        return service.sourceCode(tid);
    }

    @Comment("Read shared component source history")
    public Map<String, Object> history(String componentId) {
        return service.history(componentId);
    }

    @Comment("Read one shared component history snapshot")
    public String historyDetail(String tid) {
        return service.historyDetail(tid);
    }

    @Comment("Read the latest shared component history snapshot")
    public Map<String, Object> lastCode(String componentId) {
        return service.lastCode(componentId);
    }

    @Comment("Enrich tenant repository tabs with shared component names")
    public List<Map<String, Object>> enrichRepositoryTabs(List<Map<String, Object>> rows) {
        return service.enrichRepositoryTabs(rows);
    }
}
