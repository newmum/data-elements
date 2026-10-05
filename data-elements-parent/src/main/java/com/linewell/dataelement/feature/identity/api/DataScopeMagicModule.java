package com.linewell.dataelement.feature.identity.api;

import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

/** Exposes the centrally configured data-source scope to Magic APIs. */
@Component
@MagicModule("dataScope")
public class DataScopeMagicModule {

    private final DataScopeAuthorizationService service;

    public DataScopeMagicModule(DataScopeAuthorizationService service) {
        this.service = service;
    }

    @Comment("Read the current user's effective configurable data-source scope")
    public Map<String, Object> dataSource() {
        return service.currentScope(DataScopeAuthorizationService.DATA_SOURCE_RESOURCE).asMap();
    }

    @Comment("Read the current user's scope for one concrete menu or low-code component")
    public Map<String, Object> resource(String resourceCode) {
        return service.currentScope(resourceCode).asMap();
    }

    @Comment("Return true only when the current user may read this data-source master row")
    public boolean canReadDataSource(Map<String, ?> dataSource) {
        return service.canReadDataSource(dataSource);
    }

    @Comment("Return true only when the current user may read this source through the named menu/component")
    public boolean canReadDataSourceFor(String resourceCode, Map<String, ?> dataSource) {
        return service.canReadDataSource(resourceCode, dataSource);
    }

    @Comment("Filter data-source master rows by the current configurable scope")
    public List<Map<String, Object>> visibleDataSources(Collection<? extends Map<String, ?>> dataSources) {
        return service.visibleDataSources(dataSources);
    }

    @Comment("Filter source master rows by the scope configured for one menu/component")
    public List<Map<String, Object>> visibleDataSourcesFor(
            String resourceCode,
            Collection<? extends Map<String, ?>> dataSources
    ) {
        return service.visibleDataSources(resourceCode, dataSources);
    }

    @Comment("Filter registration-step application options: ALL sees all; every other scope sees only the current user's department applications")
    public List<Map<String, Object>> visibleRegistrationApplications(
            String resourceCode,
            Collection<? extends Map<String, ?>> applications
    ) {
        return service.visibleRegistrationApplications(resourceCode, applications);
    }
}
