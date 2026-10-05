package com.linewell.dataelement.feature.identity.api;

import com.linewell.dataelement.feature.identity.application.ControlIdentityService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

/** Exposes control-plane identities to tenant-scoped Magic pages without cross-database SQL. */
@Component
@MagicModule("controlIdentity")
public class ControlIdentityMagicModule {

    private final ControlIdentityService service;

    public ControlIdentityMagicModule(ControlIdentityService service) {
        this.service = service;
    }

    @Comment("Page active control-plane accounts that belong to a tenant")
    public Map<String, Object> page(
            String tenantId,
            long pageNum,
            long pageSize,
            Object ids,
            String userName,
            String realName
    ) {
        return service.page(tenantId, pageNum, pageSize, ids(ids), userName, realName);
    }

    @Comment("List active control-plane accounts that belong to a tenant")
    public List<Map<String, Object>> list(String tenantId, String userName) {
        return service.list(tenantId, userName);
    }

    @Comment("Check whether an account belongs to a tenant")
    public boolean hasMembership(String tenantId, String userId) {
        return service.hasMembership(tenantId, userId);
    }

    @Comment("Read one active control-plane account within a tenant")
    public Map<String, Object> find(String tenantId, String userId) {
        return service.find(tenantId, userId);
    }

    @Comment("Retire one tenant membership from the control-plane identity source")
    public Map<String, Object> removeMembership(String tenantId, String userId, String operator) {
        return service.removeMembership(tenantId, userId, operator);
    }

    @Comment("Resolve display names from the control-plane account source")
    public Map<String, String> names(Object userIds) {
        return service.names(ids(userIds));
    }

    private List<String> ids(Object value) {
        if (value == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        if (value instanceof Iterable<?> values) {
            values.forEach(item -> add(result, item));
        } else {
            for (String item : String.valueOf(value).split(",")) {
                add(result, item);
            }
        }
        return result;
    }

    private void add(List<String> values, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            values.add(String.valueOf(value).trim());
        }
    }
}
