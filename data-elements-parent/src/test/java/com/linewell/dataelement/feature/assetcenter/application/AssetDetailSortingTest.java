package com.linewell.dataelement.feature.assetcenter.application;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AssetDetailSortingTest {
    @Test void itemNamesAndLengthsAreOrderedBeforePagingWithinTheParentAndTenant() {
        var db=new DriverManagerDataSource("jdbc:h2:mem:asset_sort_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        var jdbc=new JdbcTemplate(db);
        jdbc.execute("CREATE TABLE db_table_column_t(tid VARCHAR(64),tenant_id VARCHAR(64),table_id VARCHAR(64),column_name VARCHAR(100),column_comment VARCHAR(100),data_type VARCHAR(30),length INT,nullable VARCHAR(5),primary_key VARCHAR(5),ordinal_position INT,is_del INT)");
        for(int i=0;i<120;i++) {
            jdbc.update("INSERT INTO db_table_column_t VALUES(?,'tenant-a','table-a',?,?,'VARCHAR',?,'1','0',?,0)","field-"+i,"code-"+i,String.format("名称%03d",119-i),i,i);
            jdbc.update("INSERT INTO db_table_column_t VALUES(?,'tenant-b','table-a','outsider','外租户','VARCHAR',10000,'1','0',0,0)","other-"+i);
            jdbc.update("INSERT INTO db_table_column_t VALUES(?,'tenant-a','other-table','outsider','其他表','VARCHAR',10000,'1','0',0,0)","other-parent-"+i);
        }
        var store=new AssetCenterStore(jdbc,new ObjectMapper(),null){@Override public String tenant(){return "tenant-a";}};
        var query=new AssetCenterQueryService(store);
        var small=query.dataItems("TABLE","table-a",1,20,"name","asc");var large=query.dataItems("TABLE","table-a",1,100,"name","asc");
        assertEquals(small,large.subList(0,20));assertEquals("名称000",small.getFirst().get("name"));
        var lengths=query.dataItems("TABLE","table-a",1,100,"length","desc");assertEquals(119,lengths.getFirst().get("length"));
        assertTrue(lengths.stream().allMatch(r->String.valueOf(r.get("id")).startsWith("field-")));
        var exact=Map.of("fieldsCodes","[\"code-1\",\"code-119\"]");
        assertEquals(2,query.countItems("TABLE","table-a",exact));
        var matched=query.dataItems("TABLE","table-a",1,100,"length","desc",exact);
        assertEquals(List.of("code-119","code-1"),matched.stream().map(row->row.get("code")).toList());
        var searched=Map.of("fieldsQuery","名称000");
        assertEquals(1,query.countItems("TABLE","table-a",searched));
        assertEquals("code-119",query.dataItems("TABLE","table-a",1,20,"name","asc",searched).getFirst().get("code"));
        assertEquals(0,query.countItems("TABLE","table-a",Map.of("fieldsCodes","[\"outsider\"]")));
        assertThrows(AssetCenterException.class,()->query.countItems("TABLE","table-a",Map.of("fieldsCodes","[123]")));
        assertThrows(AssetCenterException.class,()->query.countItems("TABLE","table-a",Map.of("fieldsCodes","["+String.join(",",Collections.nCopies(101,"\"code\""))+"]")));
        assertThrows(IllegalArgumentException.class,()->query.dataItems("TABLE","table-a",1,20,"name;DROP TABLE x","asc"));
        assertThrows(IllegalArgumentException.class,()->query.dataItems("TABLE","table-a",1,20,"name","sideways"));
    }
}
