package com.linewell.dataelement.feature.assetcenter.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.identity.infrastructure.persistence.mapper.IdentityUserLookupMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AssetCenterAttachmentBatchTest {

    @Test
    void loadsCurrentMappingsForAllBindingsInOneQuery() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:asset_attachment_batch;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("drop all objects");
        jdbc.execute("create table da_catalog_resource_binding_t (tid varchar(64), tenant_id varchar(64), catalog_id varchar(64), resource_type varchar(64), resource_id varchar(64), binding_role varchar(64), status varchar(64), mapping_version int, display_order int, primary_flag int, resource_version_id varchar(64), delivery_channel varchar(64))");
        jdbc.execute("create table da_catalog_item_mapping_t (tenant_id varchar(64), binding_id varchar(64), mapping_version int, status varchar(64), catalog_item_id varchar(64), resource_field_id varchar(64), resource_field_path varchar(64), mapping_kind varchar(64), transform_ref varchar(64))");
        jdbc.execute("create table db_table_t (tid varchar(64), tenant_id varchar(64), is_del int, table_name varchar(64), table_name_cn varchar(64), org_id varchar(64), app_id varchar(64), created_by varchar(64))");
        jdbc.update("insert into da_catalog_resource_binding_t values ('b1','tenant-a','catalog','TABLE','t1','SOURCE','ACTIVE',2,1,1,null,null)");
        jdbc.update("insert into da_catalog_resource_binding_t values ('b2','tenant-a','catalog','TABLE','t2','SOURCE','ACTIVE',1,2,0,null,null)");
        jdbc.update("insert into da_catalog_item_mapping_t values ('tenant-a','b1',1,'ACTIVE','old',null,'old','DIRECT',null)");
        jdbc.update("insert into da_catalog_item_mapping_t values ('tenant-a','b1',2,'ACTIVE','current',null,'current','DIRECT',null)");
        jdbc.update("insert into da_catalog_item_mapping_t values ('tenant-a','b2',1,'ACTIVE','second',null,'second','DIRECT',null)");
        jdbc.update("insert into db_table_t values ('t1','tenant-a',0,'table_one','表一',null,null,'user-a')");
        jdbc.update("insert into db_table_t values ('t2','tenant-a',0,'table_two','表二',null,null,'user-a')");
        AssetCenterStore store = spy(new AssetCenterStore(jdbc, new ObjectMapper(), mock(IdentityUserLookupMapper.class)));
        doReturn("tenant-a").when(store).tenant();
        doReturn("user-a").when(store).user();
        doReturn(List.of()).when(store).organizations();
        doReturn(true).when(store).table("da_catalog_resource_binding_t");
        AssetCenterQueryService service = new AssetCenterQueryService(store);

        List<Map<String,Object>> result = service.attachments("catalog");

        assertThat(result).hasSize(2);
        assertThat(((List<?>) result.get(0).get("mappings"))).hasSize(1);
        assertThat(((Map<?,?>) ((List<?>) result.get(0).get("mappings")).getFirst()).get("catalogItemId"))
                .isEqualTo("current");
        assertThat(((Map<?,?>) result.get(1).get("resource")).get("name")).isEqualTo("表二");
    }
}
