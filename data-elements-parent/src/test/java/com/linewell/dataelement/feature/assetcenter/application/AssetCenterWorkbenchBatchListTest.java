package com.linewell.dataelement.feature.assetcenter.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.approval.application.ApprovalFlowService;
import com.linewell.dataelement.feature.approval.application.ApprovalPrincipalService;
import com.linewell.dataelement.feature.identity.application.IdentityDirectoryService;
import com.linewell.dataelement.feature.identity.infrastructure.persistence.mapper.IdentityUserLookupMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AssetCenterWorkbenchBatchListTest {

    @Test
    void subscriptionPageLoadsScopesOnceAndDoesNotReloadEachForm() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:asset_workbench_batch;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("drop all objects");
        jdbc.execute("create table data_apply_form_t (tid varchar(64), tenant_id varchar(64), is_del int, type varchar(64), apply_user_id varchar(64), apply_name varchar(255), flow_status int, submission_version int, asset_payload_json varchar(1000), revision varchar(64), apply_org_id varchar(64), updated_time timestamp)");
        jdbc.execute("create table da_apply_scope_t (tid varchar(64), tenant_id varchar(64), apply_form_id varchar(64), submission_version int, scope_no int, catalog_id varchar(64), status varchar(64))");
        jdbc.update("insert into data_apply_form_t values ('form-1','tenant-a',0,'CATALOG_SUBSCRIPTION','user-a','申请一',0,1,'{\"resources\":[]}','1','org-a',CURRENT_TIMESTAMP)");
        jdbc.update("insert into data_apply_form_t values ('form-2','tenant-a',0,'CATALOG_SUBSCRIPTION','user-a','申请二',2,1,'{\"resources\":[]}','2','org-a',CURRENT_TIMESTAMP)");
        jdbc.update("insert into data_apply_form_t values ('foreign','tenant-b',0,'CATALOG_SUBSCRIPTION','user-a','秘密',0,1,'{}','1','org-a',CURRENT_TIMESTAMP)");
        jdbc.update("insert into da_apply_scope_t values ('scope-1','tenant-a','form-1',1,1,'catalog-1','DRAFT')");
        jdbc.update("insert into da_apply_scope_t values ('scope-2','tenant-a','form-2',1,1,'catalog-2','ACTIVE')");
        AssetCenterStore store = spy(new AssetCenterStore(jdbc, new ObjectMapper(), mock(IdentityUserLookupMapper.class)));
        doReturn("tenant-a").when(store).tenant();
        doReturn("user-a").when(store).user();
        doReturn(true).when(store).table("da_apply_scope_t");
        AssetCenterWorkbenchService service = new AssetCenterWorkbenchService(store,
                mock(AssetCenterQueryService.class), mock(ApprovalFlowService.class),
                mock(ApprovalPrincipalService.class), mock(IdentityDirectoryService.class));

        Map<String,Object> page = service.workbench(Map.of("tab", "SUBSCRIPTIONS"));

        assertThat(page.get("items")).isInstanceOf(List.class);
        List<?> items = (List<?>) page.get("items");
        assertThat(items).hasSize(2);
        Map<?,?> first = (Map<?,?>) items.get(0);
        assertThat(((List<?>) ((Map<?,?>) first.get("subscription")).get("scopes"))).hasSize(1);
        verify(store, times(2)).rows(anyString(), any(Object[].class));
        verify(store, never()).owned(anyString(), anyString(), anyBoolean());
    }
}
