package com.linewell.dataelement.platform.web.config;

import com.linewell.dataelement.platform.tenant.web.TenantWebInterceptor;
import com.linewell.dataelement.platform.web.security.GlobalInterceptor;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.server.PathContainer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.UrlPathHelper;
import org.springframework.web.util.pattern.PathPatternParser;
import com.linewell.dataelement.model.Global;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    @Autowired
    private GlobalInterceptor globalInterceptor;

    @Autowired
    private TenantWebInterceptor tenantWebInterceptor;

    @Autowired
    private com.linewell.dataelement.platform.audit.AuditWebInterceptor auditWebInterceptor;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(Global.USER_FILES_BASE_URL + "**").addResourceLocations("file:" + Global.getDir() + Global.USER_FILES_BASE_URL);
    }

    @Bean
    FilterRegistrationBean<Filter> legacyResourceRoutes() {
        Map<String, String> legacyPostPaths = Map.ofEntries(
            Map.entry("/dst/asset/module/page", "/dst/asset/page"),
            Map.entry("/dst/asset/module/save", "/dst/asset/save"),
            Map.entry("/dst/asset/module/delete", "/dst/asset/delete"),
            Map.entry("/dst/asset/module/statistics", "/dst/asset/stats"),
            Map.entry("/dst/asset/inventory/run", "/dst/asset/scan"),
            Map.entry("/dst/asset/evaluation/calculate", "/dst/asset/score"),
            Map.entry("/sym/user/saveOrUpdate", "/sym/user/save"),
            Map.entry("/sym/user/online/page", "/sym/online/page"),
            Map.entry("/sym/user/online/kickout", "/sym/online/kickout"),
            Map.entry("/sym/user/saveRoles", "/sym/user/assign"),
            Map.entry("/sym/user/role-options", "/sym/user/options"),
            Map.entry("/sym/role/saveOrUpdate", "/sym/role/save"),
            Map.entry("/sym/org/saveOrUpdate", "/sym/org/save"),
            Map.entry("/sym/org/getOrgAppDbTree", "/sym/org/assets"),
            Map.entry("/sym/org/getOrgTree", "/sym/org/tree"),
            Map.entry("/idaas/application-groups/remove", "/idaas/appgroup/remove"),
            Map.entry("/idaas/application-groups/save", "/idaas/appgroup/save"),
            Map.entry("/idaas/applications/directory-pull", "/idaas/applications/import"),
            Map.entry("/idaas/applications/push-portal-config", "/idaas/applications/portal"),
            Map.entry("/idaas/applications/runtime-binding", "/idaas/applications/binding"),
            Map.entry("/idaas/auth-account/login", "/idaas/identity/login"),
            Map.entry("/idaas/auth-account/logout", "/idaas/identity/logout"),
            Map.entry("/idaas/auth-account/password", "/idaas/identity/password"),
            Map.entry("/idaas/auth-account/register", "/idaas/identity/register"),
            Map.entry("/idaas/auth-account/revoke-session", "/idaas/identity/revoke"),
            Map.entry("/idaas/auth-account/select-entity", "/idaas/identity/entity"),
            Map.entry("/idaas/auth-accounts/bind-entity", "/idaas/accounts/entity"),
            Map.entry("/idaas/auth-accounts/reset", "/idaas/accounts/reset"),
            Map.entry("/idaas/auth-accounts/save", "/idaas/accounts/save"),
            Map.entry("/idaas/field-definitions/remove", "/idaas/field/remove"),
            Map.entry("/idaas/field-definitions/save", "/idaas/field/save"),
            Map.entry("/idaas/mfa/verify-login", "/idaas/mfa/verify"),
            Map.entry("/idaas/operators/reset-password", "/idaas/operators/reset"),
            Map.entry("/idaas/password-recovery/request", "/idaas/recovery/request"),
            Map.entry("/idaas/permission-groups/remove", "/idaas/permgroup/remove"),
            Map.entry("/idaas/permission-groups/save", "/idaas/permgroup/save"),
            Map.entry("/idaas/platform-roles/assign", "/idaas/adminrole/assign"),
            Map.entry("/idaas/platform-roles/save", "/idaas/adminrole/save"),
            Map.entry("/idaas/protocol-config/configure", "/idaas/protocol/configure"),
            Map.entry("/idaas/protocol-config/disable", "/idaas/protocol/disable"),
            Map.entry("/idaas/protocol-config/federation", "/idaas/protocol/federation"),
            Map.entry("/idaas/provision/save-config", "/idaas/provision/config"),
            Map.entry("/idaas/provision/set-initial-password", "/idaas/provision/password"),
            Map.entry("/idaas/public-entities/remove", "/idaas/legal/remove"),
            Map.entry("/idaas/public-entities/save", "/idaas/legal/save"),
            Map.entry("/idaas/self-entities/register", "/idaas/self/register"),
            Map.entry("/idaas/users/batch-disable", "/idaas/users/disable"),
            Map.entry("/idaas/verifications/save-provider", "/idaas/verifications/configure"),
            Map.entry("/sym/menu/saveOrUpdate", "/sym/menu/save"),
            Map.entry("/sym/menu/toggleStatus", "/sym/menu/state"),
            Map.entry("/sym/auth/userMenu", "/sym/auth/tree"),
            Map.entry("/sym/auth/getRoleResource", "/sym/auth/detail"),
            Map.entry("/sym/auth/saveRoleResource", "/sym/auth/save"),
            Map.entry("/sym/tenant/user-page", "/sym/tenant/page"),
            Map.entry("/sym/tenant/assign-user", "/sym/tenant/assign"),
            Map.entry("/sym/tenant/unassign-user", "/sym/tenant/unassign"),
            Map.entry("/sym/approval/query/deleteSuggestion", "/sym/suggestion/delete"),
            Map.entry("/sym/approval/query/page", "/sym/approval/page"),
            Map.entry("/sym/approval/query/applyPage", "/sym/approval/applications"),
            Map.entry("/sym/approval/delegation/delete", "/sym/delegation/delete"),
            Map.entry("/sym/approval/callback/finshHandle", "/sym/approval/finish"),
            Map.entry("/sym/approval/operate/handle", "/sym/approval/handle"),
            Map.entry("/sym/approval/operate/previousNodeList", "/sym/approval/previous"),
            Map.entry("/sym/approval/config/saveOrUpdate", "/sym/flow/save"),
            Map.entry("/sym/approval/config/list", "/sym/flow/page"),
            Map.entry("/sym/approval/config/exec", "/sym/flow/exec"),
            Map.entry("/sym/approval/delegation/save", "/sym/delegation/save"),
            Map.entry("/dws/flowserve/gateway/delete", "/dws/gateway/delete"),
            Map.entry("/dws/flowserve/gateway/save", "/dws/gateway/save"),
            Map.entry("/dws/flowserve/gateway/orchestration/open", "/dws/gateway/open"),
            Map.entry("/dws/flowserve/gateway/api/apply", "/dws/gateway/apply"),
            Map.entry("/dws/flowserve/gateway/group/list", "/dws/gategroup/list"),
            Map.entry("/dws/flowserve/gateway/group/save", "/dws/gategroup/save"),
            Map.entry("/dws/flowserve/gateway/group/delete", "/dws/gategroup/delete"),
            Map.entry("/dws/flowserve/gateway/api/list", "/dws/flowserve/services"),
            Map.entry("/dws/flowserve/gateway/api/detail", "/dws/flowserve/service"),
            Map.entry("/dws/flowserve/gateway/api/debug", "/dws/flowserve/inspect"),
            Map.entry("/dws/service/lake/zones", "/dws/lake/zones"),
            Map.entry("/dws/service/lake/zones/init", "/dws/lake/init"),
            Map.entry("/dws/service/lake/ingest/check", "/dws/lakerule/ingest"),
            Map.entry("/dws/service/lake/egress/apply", "/dws/lakerule/egress"),
            Map.entry("/dws/service/gateway/routes", "/dws/gateway/routes"),
            Map.entry("/dws/service/gateway/policies", "/dws/gateway/policies"),
            Map.entry("/dws/service/gateway/regions", "/dws/gateway/regions"),
            Map.entry("/dws/service/gateway/apps", "/dws/gateway/apps"),
            Map.entry("/dws/service/gateway/instances", "/dws/gaterun/instances"),
            Map.entry("/dws/service/gateway/gray-releases", "/dws/gaterun/releases"),
            Map.entry("/dws/service/gateway/logs", "/dws/gaterun/logs"),
            Map.entry("/dws/service/gateway/metrics", "/dws/gaterun/metrics"),
            Map.entry("/dws/service/gateway/subscriptions", "/dws/servicemarket/subscriptions"),
            Map.entry("/dws/service/gateway/feedback", "/dws/servicemarket/feedback"),
            Map.entry("/dws/service/gateway/packages", "/dws/servicemarket/packages"),
            Map.entry("/dws/service/gateway/invoke", "/dws/gaterun/invoke"),
            Map.entry("/dws/service/sys/timers", "/dws/timer/manage"),
            Map.entry("/dwm/standard/element/page", "/dwm/standard/page"),
            Map.entry("/dwm/standard/element/queryById", "/dwm/standard/detail"),
            Map.entry("/dwm/standard/element/metaSaveOrUpdate", "/dwm/standard/save"),
            Map.entry("/dwm/standard/element/updatePublishStatus", "/dwm/standard/publish"),
            Map.entry("/dwm/standard/element/meatDeleteById", "/dwm/standard/delete"),
            Map.entry("/dwm/standard/standard-class-list", "/dwm/standard/tree"),
            Map.entry("/dwm/standard/code/list", "/dwm/code/list"),
            Map.entry("/dwm/standard/code/page", "/dwm/code/page"),
            Map.entry("/dwm/standard/code/queryById", "/dwm/code/detail"),
            Map.entry("/dwm/standard/code/saveOrUpdate", "/dwm/code/save"),
            Map.entry("/dwm/standard/code/deleteById", "/dwm/code/delete"),
            Map.entry("/dwm/standard/landing/list", "/dwm/binding/page"),
            Map.entry("/dwm/standard/landing/save", "/dwm/binding/save"),
            Map.entry("/dwm/metadata-governance/tables/columns/batch", "/dwm/column/batch"),
            Map.entry("/dwm/center/dwd-list", "/dwm/layer/dwd"),
            Map.entry("/dwm/center/dws-list", "/dwm/layer/dws"),
            Map.entry("/dwm/center/ods-list", "/dwm/layer/ods"),
            Map.entry("/dwm/center/layer-page", "/dwm/layer/page"),
            Map.entry("/dwm/center/dstTable", "/dwm/center/stats"),
            Map.entry("/dwm/center/datasource-options", "/dwm/layer/choices"),
            Map.entry("/dwm/center/node/save", "/dwm/layer/save"),
            Map.entry("/dwm/center/operations/record/save", "/dwm/ops/save"),
            Map.entry("/dwm/center/operations/record/delete", "/dwm/ops/delete"),
            Map.entry("/dwm/center/command", "/dwm/ops/command"),
            Map.entry("/dwm/quality/task/page", "/dwm/qtask/page"),
            Map.entry("/dwm/quality/task/detail", "/dwm/qtask/detail"),
            Map.entry("/dwm/quality/task/save", "/dwm/qtask/save"),
            Map.entry("/dwm/quality/task/precheck", "/dwm/qtask/check"),
            Map.entry("/dwm/quality/saveOrUpdate", "/dwm/quality/save"),
            Map.entry("/dwm/quality/deleteById", "/dwm/quality/delete"),
            Map.entry("/dwm/quality/task/enable", "/dwm/qtask/enable"),
            Map.entry("/dwm/quality/run/manual", "/dwm/qrun/start"),
            Map.entry("/dwm/quality/run/page", "/dwm/qrun/page"),
            Map.entry("/dwm/quality/run/detail", "/dwm/qrun/detail"),
            Map.entry("/dwm/quality/run/issues", "/dwm/qrun/issues"),
            Map.entry("/dwm/quality/run/cancel", "/dwm/qrun/cancel"),
            Map.entry("/dwm/quality/options/datasources", "/dwm/qoption/sources"),
            Map.entry("/dwm/quality/options/tables", "/dwm/qoption/tables"),
            Map.entry("/dwm/quality/options/columns", "/dwm/qoption/columns"),
            Map.entry("/dwm/quality/options/templates", "/dwm/qoption/templates"),
            Map.entry("/dwm/quality/task/delete", "/dwm/qtask/delete"),
            Map.entry("/dwm/quality/options/organizations", "/dwm/qoption/orgs"),
            Map.entry("/dwm/quality/workorders/create", "/dwm/qorder/create"),
            Map.entry("/dwm/quality/workorders/transition", "/dwm/qorder/transition"),
            Map.entry("/dwm/quality/workorders/page", "/dwm/qorder/page"),
            Map.entry("/dwm/quality/templates/page", "/dwm/qsql/page"),
            Map.entry("/dwm/quality/templates/save", "/dwm/qsql/save"),
            Map.entry("/dwm/quality/templates/delete", "/dwm/qsql/delete"),
            Map.entry("/dwm/metadata-governance/catalog", "/dwm/meta/catalog"),
            Map.entry("/dwm/metadata-governance/relations/discover", "/dwm/relation/discover"),
            Map.entry("/dwm/metadata-governance/relations/save", "/dwm/relation/save"),
            Map.entry("/dwm/metadata-governance/relations/delete", "/dwm/relation/delete"),
            Map.entry("/dwm/metadata-governance/relations/validate", "/dwm/relation/validate"),
            Map.entry("/dwm/metadata-governance/lineage/evidence", "/dwm/lineage/evidence"),
            Map.entry("/dwm/metadata-governance/lineage/coverage", "/dwm/lineage/coverage"),
            Map.entry("/dwm/metadata-governance/relations/foreign-key/preview", "/dwm/foreign/check"),
            Map.entry("/dwm/metadata-governance/relations/foreign-key/create", "/dwm/foreign/create"),
            Map.entry("/dwm/metadata-governance/bindings/list", "/dwm/align/page"),
            Map.entry("/dwm/metadata-governance/bindings/save", "/dwm/align/save"),
            Map.entry("/dwm/metadata-governance/summary", "/dwm/meta/summary")
        );
        Map<String, String> legacyGetPaths = Map.ofEntries(
            Map.entry("/idaas/applications/directory-compare", "/idaas/applications/compare"),
            Map.entry("/idaas/applications/local-inventory", "/idaas/applications/inventory"),
            Map.entry("/idaas/auth-account/me", "/idaas/identity/me"),
            Map.entry("/idaas/auth-account/sessions", "/idaas/identity/sessions"),
            Map.entry("/idaas/auth-accounts/list", "/idaas/accounts/list"),
            Map.entry("/idaas/auth/public-settings", "/idaas/auth/settings"),
            Map.entry("/idaas/password-recovery/requests", "/idaas/recovery/requests"),
            Map.entry("/idaas/platform-roles/assignments", "/idaas/adminrole/assignments"),
            Map.entry("/idaas/platform-roles/list", "/idaas/adminrole/list"),
            Map.entry("/idaas/platform-roles/resources", "/idaas/adminrole/resources"),
            Map.entry("/idaas/protocol-config/read", "/idaas/protocol/read"),
            Map.entry("/idaas/self-entities/mine", "/idaas/self/mine"),
            Map.entry("/sym/role/pageRole", "/sym/role/page"),
            Map.entry("/sym/org/pageOrg", "/sym/org/page"),
            Map.entry("/sym/approval/query/suggestionList", "/sym/suggestion/list"),
            Map.entry("/sym/approval/query/definitionList", "/sym/flow/options"),
            Map.entry("/sym/approval/query/summary", "/sym/approval/summary"),
            Map.entry("/sym/approval/delegation/list", "/sym/delegation/list"),
            Map.entry("/dws/flowserve/runtime-nodes", "/dws/flowserve/nodes"),
            Map.entry("/dws/service/lake/zones", "/dws/lake/zones"),
            Map.entry("/dws/service/lake/zones/overview", "/dws/lake/overview"),
            Map.entry("/dws/service/sys/timers", "/dws/timer/page"),
            Map.entry("/dws/service/sys/timers/logs", "/dws/timer/logs"),
            Map.entry("/dwm/metadata-governance/catalog-services/page", "/dwm/catalog/services"),
            Map.entry("/dwm/metadata-governance/catalog-resources/page", "/dwm/catalog/resources"),
            Map.entry("/dwm/metadata-governance/tables/page", "/dwm/catalog/tables"),
            Map.entry("/dwm/metadata-governance/tables/columns", "/dwm/column/list"),
            Map.entry("/dwm/metadata-governance/tables/columns/page", "/dwm/column/page"),
            Map.entry("/dwm/center/layer-list", "/dwm/center/overview"),
            Map.entry("/dwm/center/dst-info", "/dwm/center/inventory"),
            Map.entry("/dwm/center/operations/overview", "/dwm/ops/overview"),
            Map.entry("/dwm/center/operations/records", "/dwm/ops/page"),
            Map.entry("/dwm/center/state", "/dwm/ops/state"),
            Map.entry("/dwm/quality/getQualityDimension", "/dwm/quality/dimension")
        );
        String oldDownload = "/data/download";
        String oldFlows = "/dws/flowserve/flows";
        var flowItem = PathPatternParser.defaultInstance.parse(oldFlows + "/{id}");
        var flowAction = PathPatternParser.defaultInstance.parse(oldFlows + "/{id}/{action}");
        var flowRoutes = Map.of(
                "GET", "/dws/flowserve/detail", "HEAD", "/dws/flowserve/detail", "PUT", "/dws/flowserve/draft",
                "POST/debug", "/dws/flowserve/debug", "POST/publish", "/dws/flowserve/publish",
                "POST/offline", "/dws/flowserve/offline", "GET/versions", "/dws/flowserve/versions");
        String oldLake = "/dws/service/lake";
        var patterns = PathPatternParser.defaultInstance;
        var zoneRules = patterns.parse(oldLake + "/zones/{id}/rules");
        var lakeRoutes = Map.of(
                "GET", Map.of(
                        patterns.parse(oldLake + "/zones/{id}/objects"), "/dws/lakeobject/page",
                        zoneRules, "/dws/lakerule/rules"),
                "POST", Map.of(
                        patterns.parse(oldLake + "/zones/{id}/objects/batch"), "/dws/lakeobject/register",
                        patterns.parse(oldLake + "/objects/{id}/lifecycle"), "/dws/lakeobject/lifecycle"),
                "PUT", Map.of(
                        patterns.parse(oldLake + "/zones/{id}"), "/dws/lake/state",
                        zoneRules, "/dws/lakerule/rules"));
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>((request, response, chain) -> {
            HttpServletRequest http = (HttpServletRequest) request;
            String oldPath = http.getRequestURI().substring(http.getContextPath().length());
            String target = switch (http.getMethod()) {
                case "GET" -> oldPath.equals(oldDownload) ? "/dst/file/download" : legacyGetPaths.get(oldPath);
                case "HEAD" -> legacyGetPaths.get(oldPath);
                case "POST" -> legacyPostPaths.get(oldPath);
                default -> null;
            };
            String legacyId = null;
            if (target == null && oldPath.startsWith(oldFlows + "/")) {
                PathContainer path = PathContainer.parsePath(oldPath);
                var match = flowItem.matchAndExtract(path);
                if (match == null) match = flowAction.matchAndExtract(path);
                if (match != null) {
                    var variables = match.getUriVariables();
                    String action = variables.get("action");
                    target = flowRoutes.get(http.getMethod() + (action == null ? "" : "/" + action));
                    legacyId = variables.get("id");
                }
            }
            if (target == null && oldPath.startsWith(oldLake + "/")) {
                PathContainer path = PathContainer.parsePath(oldPath);
                for (var route : lakeRoutes.getOrDefault(http.getMethod(), Map.of()).entrySet()) {
                    var match = route.getKey().matchAndExtract(path);
                    if (match != null) {
                        target = route.getValue();
                        legacyId = match.getUriVariables().get("id");
                        break;
                    }
                }
            }
            if (target == null) {
                chain.doFilter(request, response);
                return;
            }
            String routedPath = target;
            String pathId = legacyId;
            // 在会话过滤器前统一路径，使旧链接和新入口沿用同一次鉴权及租户解析。
            chain.doFilter(new HttpServletRequestWrapper(http) {
                @Override
                public String getRequestURI() {
                    return getContextPath() + routedPath;
                }

                @Override
                public String getServletPath() {
                    return routedPath;
                }

                @Override
                public StringBuffer getRequestURL() {
                    StringBuffer original = super.getRequestURL();
                    return original.replace(original.length() - oldPath.length(), original.length(), routedPath);
                }

                // 旧 URL 中的 ID 优先于同名请求参数，保持签名、权限和写入对象的选择。
                @Override
                public String getParameter(String name) {
                    return pathId != null && "id".equals(name) ? pathId : super.getParameter(name);
                }

                @Override
                public String[] getParameterValues(String name) {
                    return pathId != null && "id".equals(name) ? new String[]{pathId} : super.getParameterValues(name);
                }

                @Override
                public Map<String, String[]> getParameterMap() {
                    if (pathId == null) return super.getParameterMap();
                    var parameters = new LinkedHashMap<>(super.getParameterMap());
                    parameters.put("id", new String[]{pathId});
                    return Collections.unmodifiableMap(parameters);
                }

                @Override
                public Enumeration<String> getParameterNames() {
                    return pathId == null ? super.getParameterNames() : Collections.enumeration(getParameterMap().keySet());
                }
            }, response);
        });
        registration.addUrlPatterns(oldDownload);
        registration.addUrlPatterns(oldFlows + "/*");
        registration.addUrlPatterns(oldLake + "/*");
        registration.addUrlPatterns(legacyPostPaths.keySet().toArray(String[]::new));
        registration.addUrlPatterns(legacyGetPaths.keySet().toArray(String[]::new));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 30);
        return registration;
    }

    // 兼容接口多个"/"，比如//system/xxx
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.setUrlPathHelper(new UrlPathHelper());
    }

    // 注册自定义拦截器
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auditWebInterceptor);
        // 注册PermissionInterceptor拦截器，对所有路径生效
        registry.addInterceptor(globalInterceptor)
            .excludePathPatterns(
                "/api/web/**",
                "/nifi-ui/**",
                "/nifi-api/**",
                "/portal/main/index",
                "/portal/sso/login",
                "/portal/sso/logout",
                "/portal/sso/options",
                "/portal/sso/settings",
                "/dws/push/internal/**"
            );
        registry.addInterceptor(tenantWebInterceptor)
            .excludePathPatterns(
                "/api/web/**",
                "/nifi-ui/**",
                "/nifi-api/**",
                "/portal/main/index",
                "/portal/sso/login",
                "/portal/sso/logout",
                "/dws/push/internal/**"
            );
            //.addPathPatterns("/**"); // 根据需要调整路径匹配规则
    }

}
