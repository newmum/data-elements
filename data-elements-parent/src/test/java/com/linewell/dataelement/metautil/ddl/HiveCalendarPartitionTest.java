package com.linewell.dataelement.metautil.ddl;

import com.linewell.dataelement.metautil.model.dto.*;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import java.time.YearMonth;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HiveCalendarPartitionTest {
    private final TablePartitions service=new TablePartitions();

    @Test void inheritedAnnualAndMonthlyBucketsUseTheCoveredPeriodAndRetainEveryBoundary() {
        verify(List.of("2024-01-01","2025-01-01","2026-01-01","MAXVALUE"),
                List.of("before_2024","2024","2025","future"));
        verify(List.of("2024-01-01","2024-02-01","2024-03-01","MAXVALUE"),
                List.of("before_202401","202401","202402","future"));
    }

    @Test void irregularBoundsAreNotMislabelledAsASingleCalendarMonthOrSplit() {
        verify(List.of("2024-01-01","2024-02-01","2024-04-01","2024-04-12 10:30:00","MAXVALUE"),
                List.of("before_202401","202401","from_202402_to_202404","from_202404_to_20240412103000","future"));
    }

    @Test void sourceIntervalExpansionGetsTheSameCalendarLabelsAsTheHistoricalPartitions() {
        var request=source(List.of("2024-01-01","2024-02-01"),"NUMTOYMINTERVAL(1,'MONTH')");
        var normalized=service.normalize(request,List.of(field()),DatabaseType.HIVE);
        var parts=parts(normalized);
        assertTrue(parts.size()>2);
        assertEquals("before_202401",parts.getFirst().get("bucket"));
        for(int i=1;i<parts.size()-1;i++) {
            String lower=parts.get(i-1).get("upper").toString().substring(0,7);
            assertEquals(lower.replace("-",""),parts.get(i).get("bucket"));
        }
        assertEquals("future",parts.getLast().get("bucket"));
        assertEquals(buckets(normalized),buckets(service.normalize(normalized,List.of(field()),DatabaseType.HIVE)));
    }

    @Test void twentyAndHundredInheritedBoundsNeedNoAdditionalMetadataOrPerPartitionQueries() {
        for(int count:List.of(20,100)) {
            List<String> bounds=new ArrayList<>();
            for(int i=0;i<count;i++)bounds.add(YearMonth.of(2018,1).plusMonths(i).atDay(1).toString());
            var plan=service.normalize(source(bounds,""),List.of(field()),DatabaseType.HIVE);
            assertEquals(count,parts(plan).size());
            assertEquals("201801",parts(plan).get(1).get("bucket"));
            assertEquals("before_201801",parts(plan).getFirst().get("bucket"));
        }
    }

    @Test void existingExplicitBucketsAndNonHivePartitionNamesAreUnchanged() {
        var request=source(List.of("2024-01-01","2025-01-01"),"");
        parts(request).get(1).put("bucket","business_2024");
        assertEquals("business_2024",parts(service.normalize(request,List.of(field()),DatabaseType.HIVE)).get(1).get("bucket"));
        var oracle=service.normalize(request,List.of(field()),DatabaseType.OCEANBASE_ORACLE);
        assertEquals("p_0001",parts(oracle).getFirst().get("name"));
        assertEquals("p_0001",parts(request).getFirst().get("bucket"),"Never mutate the source probe plan");
    }

    private void verify(List<String> bounds,List<String> expected) {
        var source=source(bounds,"");
        var plan=service.normalize(source,List.of(field()),DatabaseType.HIVE);
        assertEquals(expected,buckets(plan));
        assertEquals(parts(source).stream().map(p->p.get("upper")).toList(),parts(plan).stream().map(p->p.get("upper")).toList());
        var config=new DataSourceConfig();config.setDatabaseType(DatabaseType.HIVE);
        String ddl=service.apply("CREATE TABLE ods_events(occurred_at TIMESTAMP)",plan,config);
        for(String bucket:expected)assertTrue(ddl.contains("`ods_partition`='"+bucket+"'"),ddl);
        assertFalse(ddl.matches("(?s).*`ods_partition`='p_\\d+'.*"));
        assertEquals(expected,buckets(service.normalize(plan,List.of(field()),DatabaseType.HIVE)));
    }
    @SuppressWarnings("unchecked")
    private Map<String,Object> source(List<String> bounds,String interval) {
        List<Map<String,Object>> rows=new ArrayList<>();
        for(String bound:bounds)rows.add(Map.of("PARTITION_NAME","original_"+rows.size(),"HIGH_VALUE",
                bound.equals("MAXVALUE")?bound:"TIMESTAMP '"+(bound.length()==10?bound+" 00:00:00":bound)+"'"));
        return (Map<String,Object>)PartitionMetadataReader.analyze("RANGE","NONE","occurred_at","TIMESTAMP",rows,interval).get("plan");
    }
    @SuppressWarnings("unchecked")
    private List<Map<String,Object>> parts(Map<String,Object> plan) {return (List<Map<String,Object>>)plan.get("partitions");}
    private List<String> buckets(Map<String,Object> plan) {return parts(plan).stream().map(p->p.get("bucket").toString()).toList();}
    private ColumnInfo field() {var field=new ColumnInfo();field.setColumnName("occurred_at");field.setColumnType("TIMESTAMP");return field;}
}
