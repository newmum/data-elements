package com.linewell.dataelement.magic.runtime.route;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.model.ApiInfo;
import org.ssssssss.magicapi.core.service.MagicResourceService;

/** Resolves the published flow namespace from the current Magic group. */
@Component
public class PublishedFlowRoutes {
    public static final String GROUP_ID = "e5ce028f1797312d57c628b66ffcf657";
    private static final String LEGACY_PARENT_ID = "5d290a9b3bca3b2dd9ffd715dc47ce56";
    private static final String TENANT_PREFIX = "/__tenant/";

    private final MagicResourceService resources;
    private final String legacyPublicPrefix;

    public PublishedFlowRoutes(@Lazy MagicResourceService resources,
                               @Value("${flowserve.runtime.public-prefix}") String legacyPublicPrefix) {
        this.resources = resources;
        this.legacyPublicPrefix = legacyPublicPrefix;
    }

    public Layout layout() {
        var group = resources.getGroup(GROUP_ID);
        if (group == null) {
            throw new IllegalStateException("缺少 Magic 服务编排发布分组");
        }
        String path = resources.getGroupPath(GROUP_ID);
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("Magic 发布分组路径为空");
        }
        String groupPath = ("/" + path).replaceAll("/+", "/").replaceAll("/$", "");
        boolean sharedGroup = !LEGACY_PARENT_ID.equals(group.getParentId());
        if (!sharedGroup) {
            return new Layout(groupPath, "", false);
        }
        Object configured = group.getAttribute("flowservePublishedSegment");
        String segment = configured == null
                ? legacyPublicPrefix.substring(legacyPublicPrefix.lastIndexOf('/') + 1)
                : configured.toString();
        if (!segment.matches("[a-z][a-z0-9]*")) {
            throw new IllegalStateException("Magic 发布分组的服务路径段无效");
        }
        return new Layout(groupPath, "/" + segment, true);
    }

    public Match match(ApiInfo api) {
        if (api == null || !GROUP_ID.equals(api.getGroupId())) {
            return null;
        }
        var layout = layout();
        if (!layout.contains(api.getPath())) {
            return null;
        }
        String path = api.getPath().substring(layout.relativePrefix().length());
        String tenantId = null;
        if (path.startsWith(TENANT_PREFIX)) {
            int end = path.indexOf('/', TENANT_PREFIX.length());
            String value = path.substring(TENANT_PREFIX.length(), end < 0 ? path.length() : end);
            tenantId = value.isBlank() ? null : value;
        }
        return new Match(api.getId(), layout.groupPath() + api.getPath(), tenantId);
    }

    public static String tenantPath(String tenantId, String path) {
        return TENANT_PREFIX + tenantId + path;
    }

    public record Layout(String groupPath, String relativePrefix, boolean sharedGroup) {
        public String apiPath(String path) {
            return relativePrefix + path;
        }

        public String publicPath(String path) {
            return groupPath + apiPath(path);
        }

        public boolean contains(String path) {
            return path != null && path.startsWith(relativePrefix + "/") && path.length() > relativePrefix.length() + 1;
        }
    }

    public record Match(String resourceId, String path, String tenantId) {
    }
}
