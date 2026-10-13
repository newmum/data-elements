package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.core.interceptor.DefaultResultProvider;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.*;
import org.ssssssss.magicapi.modules.db.dialect.*;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.magicapi.modules.db.provider.*;
import org.ssssssss.script.*;

class RegisteredGovernanceColumnBatchMagicTest {
    @Test
    void twentyAndHundredFieldsUseOneScopedReadAndRejectForeignColumnIds() throws Exception {
        for (int count : List.of(20,100)) {
            var source = new DriverManagerDataSource("jdbc:h2:mem:governance_columns_" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            var jdbc = new JdbcTemplate(source);
            jdbc.execute("create table db_table_column_t(tid varchar(64),tenant_id varchar(64),table_id varchar(64),column_name varchar(64),is_del int)");
            var items = new ArrayList<Map<String,Object>>();
            for(int i=0;i<count;i++) {
                jdbc.update("insert into db_table_column_t values(?,'police','source',?,0)","column-"+i,"field_"+i);
                items.add(i%2==0 ? Map.of("tid","column-"+i) : Map.of("columnName","field_"+i));
            }
            jdbc.update("insert into db_table_column_t values('foreign-tenant','other','source','secret',0)");
            jdbc.update("insert into db_table_column_t values('foreign-table','police','other','other_field',0)");
            jdbc.update("insert into db_table_column_t values('deleted','police','source','deleted_field',1)");
            items.addAll(List.of(Map.of("tid","foreign-tenant"),Map.of("tid","foreign-table"),Map.of("tid","deleted")));
            var sources=new MagicDynamicDataSource();sources.setDefault(source);var db=new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource());db.setResultProvider(new DefaultResultProvider("governance-test"));
            var dialects=new DialectAdapter();dialects.add(new MySQLDialect(){@Override public boolean match(String url){return url.contains(":h2:");}});db.setDialectAdapter(dialects);
            var columns=new ColumnMapperAdapter();columns.add(new DefaultColumnMapperProvider());columns.add(new CamelColumnMapperProvider());columns.setDefault("camel");db.setColumnMapperProvider(columns);db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());db.setRowMapColumnMapper(value->value);db.setNamedTableInterceptors(List.of());
            int[] reads={0};db.setSqlInterceptors(List.of(new SQLInterceptor(){@Override public void preHandle(BoundSql sql,RequestEntity request){if(sql.getSql().stripLeading().toLowerCase().startsWith("select"))reads[0]++;}}));
            String canonical=CanonicalMagicSources.byId("datapd_db_table_govern_01");
            String helpers=canonical.substring(canonical.indexOf("var textOf ="),canonical.indexOf("// 解析请求体并为可选参数设置默认值。"));
            var variables=new LinkedHashMap<String,Object>();variables.put("db",db);variables.put("items",items);
            var result=MagicScript.create("import java.util.Map\nvar tenantId='police'\n"+helpers+"\nreturn registeredColumnItems('source',items)",null)
                    .execute(new MagicScriptContext(variables));
            var resolved=assertInstanceOf(List.class,result);assertEquals(count,resolved.size());assertEquals(1,reads[0]);
            for(Object value:resolved){var row=assertInstanceOf(Map.class,assertInstanceOf(Map.class,value).get("column"));assertEquals("police",row.get("tenantId"));assertEquals("source",row.get("tableId"));}
            System.out.println("Governance field matching: fields="+count+", SQL reads="+reads[0]+", foreign/deleted IDs excluded");
        }
    }
}
