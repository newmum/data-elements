package com.linewell.dataelement.platform.audit;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AuditNavigationTest {
    private Map<String,Object> node(String id,String parent,String name,String url,int permitted){return Map.of("id",id,"parent_id",parent,"name",name,"url",url,"status",0,"deleted",0,"permitted",permitted);}
    @Test void twentyAndHundredMenusUseOneReadAndSnapshotAuthorizedHierarchy(){
        var store=mock(AuditStore.class);var jdbc=mock(JdbcTemplate.class);when(store.jdbc("validation-tenant")).thenReturn(jdbc);
        var navigation=new AuditNavigation(store);var actor=new AuditIdentity("validation-tenant","TENANT_USER","validation-user","validation.account","验证人员","validation-org","验证部门","validation-app",null,null);
        try {
            for(int size:List.of(20,100)){
                var rows=new ArrayList<Map<String,Object>>(List.of(node("root","ROOT","数据接入","/jr",1),node("tasks","root","接入任务","/tasks",1),node("denied","root","无权功能","/denied",0)));
                for(int i=3;i<size;i++)rows.add(node("menu"+i,"root","其他功能","/other"+i,1));
                when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(rows);
                var request=new MockHttpServletRequest();request.addHeader("X-Audit-Route","/jr/tasks/list?password=discarded");RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
                var snapshot=navigation.snapshot(actor);assertEquals("数据接入 → 接入任务",snapshot.get("menu_path"));assertEquals("/jr/tasks/list",snapshot.get("page_route"));assertSame(snapshot,navigation.snapshot(actor));
                var denied=new MockHttpServletRequest();denied.addHeader("X-Audit-Route","/jr/denied");RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(denied));assertFalse(navigation.snapshot(actor).containsKey("menu_path"));
            }
            verify(jdbc,times(4)).queryForList(contains("where m.tenant_id=? and m.app_id=?"),any(Object[].class));verifyNoMoreInteractions(jdbc);
        }finally{RequestContextHolder.resetRequestAttributes();}
    }
    @Test void disabledAncestorsAndCyclesNeverProduceLabels(){
        var root=new HashMap<>(node("root","ROOT","停用菜单","/root",1));root.put("status",1);
        var nodes=AuditNavigation.index(List.of(root,node("leaf","root","子菜单","/leaf",1)));assertTrue(AuditNavigation.chain(nodes,"leaf").isEmpty());
        root.put("status",0);root.put("parent_id","leaf");assertTrue(AuditNavigation.chain(AuditNavigation.index(List.of(root,node("leaf","root","子菜单","/leaf",1))),"leaf").isEmpty());
    }
    @Test void canonicalNodeConfigurationAliasRetainsTheAuthorizedMenu(){
        var store=mock(AuditStore.class);var jdbc=mock(JdbcTemplate.class);when(store.jdbc("validation-tenant")).thenReturn(jdbc);
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(node("root","ROOT","后台管理","/register",1),node("node","root","节点配置","/nifi-node-config",1)));
        var request=new MockHttpServletRequest();request.addHeader("X-Audit-Route","/node-config");RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try{var value=new AuditNavigation(store).snapshot(new AuditIdentity("validation-tenant","TENANT_USER","user",null,null,null,null,"app",null,null));assertEquals("后台管理 → 节点配置",value.get("menu_path"));assertEquals("/node-config",value.get("page_route"));}finally{RequestContextHolder.resetRequestAttributes();}
    }
    @Test void platformLabelsRequirePermissionAndScopeFromTheSameAssignment(){
        var read=Map.of("codes",List.of("audit:read"),"scopes",List.of(Map.of("identity_domain","workforce","scope_kind","ALL")));
        var context=Map.of("pairs",List.of(read));
        assertEquals("审计日志 → 日志管理",AuditPlatformNavigation.snapshot("/console/workforce/audit/logs",context).get("menu_path"));
        assertTrue(AuditPlatformNavigation.snapshot("/console/workforce/system/access/roles",context).isEmpty());
        assertTrue(AuditPlatformNavigation.snapshot("/console/public/audit/logs",context).isEmpty());
        var split=Map.of("pairs",List.of(Map.of("codes",List.of("audit:read"),"scopes",List.of(Map.of("identity_domain","public","scope_kind","ALL"))),Map.of("codes",List.of("users:read"),"scopes",List.of(Map.of("identity_domain","workforce","scope_kind","ALL")))));
        assertTrue(AuditPlatformNavigation.snapshot("/console/workforce/audit/logs",split).isEmpty());
    }
}
