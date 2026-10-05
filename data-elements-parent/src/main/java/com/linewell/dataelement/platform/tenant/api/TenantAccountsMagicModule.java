package com.linewell.dataelement.platform.tenant.api;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.dev33.satoken.session.SaSession;
import com.linewell.dataelement.feature.identity.application.TenantIdentityQueryService;
import com.linewell.dataelement.platform.tenant.application.TenantAccessService;
import com.linewell.dataelement.platform.tenant.application.TenantSessions;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;

/** Session/datasource primitives and framework reads only; mutations/credential validation are Magic scripts. */
@Component
@MagicModule("tenantAccounts")
public class TenantAccountsMagicModule {
    private final TenantIdentityQueryService queries;
    private final TenantAccessService access;
    public TenantAccountsMagicModule(TenantIdentityQueryService queries, TenantAccessService access) {
        this.queries=queries; this.access=access;
    }
    public boolean isLocal(String tenantId) { return queries.isLocal(tenantId); }
    public SaSession session() { return TenantSessions.session(); }
    public Map<String,Object> page(String tenantId,long page,long size,Object ids,String account,String name) {
        return queries.page(tenantId,page,size,ids == null ? null : ids(ids),account,name);
    }
    public List<Map<String,Object>> list(String tenantId,String name) { return queries.list(tenantId,name); }
    public Map<String,Object> find(String tenantId,String id) { return queries.find(tenantId,id); }
    public boolean hasMembership(String tenantId,String id) { return queries.hasMembership(tenantId,id); }
    public Map<String,String> names(Object ids) { return queries.names(ids(ids)); }
    public List<String> tokens() { return TenantSessions.tokens(StpUtil.getStpLogic()); }
    public String loginLocal(String tenantId,String id,String appId,String account) {
        access.requireActiveTenant(tenantId);
        if (!queries.isLocal(tenantId)) throw new TenantAccessException("TENANT-FORBIDDEN","当前租户未启用本地账号");
        StpUtil.login(id,new SaLoginParameter().setIsShare(false).setDeviceType("tenant-local:"+tenantId));
        StpUtil.getTokenSession().set("tenantId",tenantId).set("authRealm","tenant-local-v1")
                .set("appId",appId).set("name",account);
        TenantContext.bind(tenantId);
        return StpUtil.getTokenValue();
    }
    public void revoke(String userId) {
        String tenantId=TenantContext.requireTenantId();
        var logic=StpUtil.getStpLogic();
        for (String token:TenantSessions.tokens(logic)) {
            if (!logic.isValidToken(token) || !userId.equals(String.valueOf(logic.getLoginIdByTokenNotThinkFreeze(token)))) continue;
            SaSession session=logic.getTokenSessionByToken(token,false);
            if (session!=null && tenantId.equals(session.get("tenantId")) && "tenant-local-v1".equals(session.get("authRealm"))) {
                logic.kickoutByTokenValue(token);
            }
        }
    }
    private List<String> ids(Object input) {
        var result=new ArrayList<String>();
        if (input instanceof Iterable<?> items) { for (Object item:items) add(result,item); }
        else if (input!=null) { for (String item:String.valueOf(input).split(",")) add(result,item); }
        return result;
    }
    private void add(List<String> result,Object item) {
        if (item!=null && !String.valueOf(item).isBlank()) result.add(String.valueOf(item).trim());
    }
}
