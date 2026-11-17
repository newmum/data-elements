package com.linewell.dataelement.feature.logmanagement;

import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.ssssssss.magicapi.core.service.MagicAPIService;

/** 为日志文件传输和后台任务绑定可信租户，读取 Magic 授权规则。 */
@Service
public class LogAccessService {
    private final ObjectProvider<MagicAPIService> magic;

    public LogAccessService(ObjectProvider<MagicAPIService> magic) {
        this.magic = magic;
    }
    public LogScope current(String capability) {
        if(!StpUtil.isLogin()) throw new SecurityException("请重新登录");
        String tenant=TenantContext.requireTenantId();
        String app=Objects.toString(StpUtil.getTokenSession().get("appId"),"");
        return tenant(tenant,StpUtil.getLoginIdAsString(),app,capability);
    }
    public LogScope tenant(String tenant,String user,String app,String capability) {
        // 后台任务复核已冻结的主体。显式绑定任务租户，执行完恢复调用方上下文。
        Object decision = TenantContext.call(tenant, () -> magic.getObject().invoke("/common/loggrants",
                Map.of("actor", user, "appId", app, "capability", capability)));
        if (!(decision instanceof Map<?, ?> result) || !(result.get("modes") instanceof Map<?, ?> modes)
                || !(modes.get(capability) instanceof String mode)) {
            throw new SecurityException("没有此日志模块的访问权限");
        }
        if (!Set.of("ALL", "ORG", "OWNER").contains(mode) || !(result.get("orgIds") instanceof List<?> orgIds)
                || !orgIds.stream().allMatch(String.class::isInstance)) {
            throw new IllegalStateException("行业日志授权结果无效");
        }
        return new LogScope(tenant, user, app, null, null, mode,
                orgIds.stream().map(String.class::cast).toList(), capability);
    }
    public LogScope platform(Map<String,Object> context,String capability) {
        if(context==null || !platformPermission(capability).equals(context.get("requiredPermission"))) throw new SecurityException("平台审计权限未校验");
        return new LogScope(null,String.valueOf(context.get("userId")),"idaas",String.valueOf(context.get("requiredDomain")),context.get("requiredAppId")==null?null:String.valueOf(context.get("requiredAppId")),"ALL",List.of(),capability);
    }
    public String platformPermission(String capability) {
        return switch(capability) {case "exports"->"audit:export";case "policies"->"audit:configure";case "archives"->"audit:archive";default->"audit:read";};
    }
    public LogScope refresh(LogScope scope,String capability) {
        if(!scope.platform()) return tenant(scope.tenantId(),scope.actorId(),scope.sourceApp(),capability);
        var decision=magic.getObject().invoke("/idaas/log-scope-for-actor",Map.of("actorId",scope.actorId(),"permission",platformPermission(capability),"domain",scope.domain(),"appId",Objects.toString(scope.appId(),"")));
        if(!(decision instanceof Map<?,?> result)||!Boolean.TRUE.equals(result.get("allowed"))) throw new SecurityException("日志权限已变更，请重新提交");
        return new LogScope(null,scope.actorId(),scope.sourceApp(),scope.domain(),scope.appId(),"ALL",List.of(),capability);
    }
    public LogScope intersect(LogScope before,LogScope now) {
        if(before.platform()) return now;
        if(before.mode().equals("ALL")) return now;
        if(now.mode().equals("ALL")) return before;
        boolean owner=before.mode().contains("OWNER")||now.mode().contains("OWNER");
        boolean org=before.mode().contains("ORG")||now.mode().contains("ORG");
        List<String> orgs=before.mode().contains("ORG")?before.orgIds():now.orgIds();
        if(before.mode().contains("ORG")&&now.mode().contains("ORG")) orgs=before.orgIds().stream().filter(now.orgIds()::contains).toList();
        return new LogScope(now.tenantId(),now.actorId(),now.sourceApp(),null,null,owner&&org?"OWNER_AND_ORG":owner?"OWNER":"ORG",orgs,now.capability());
    }
}
