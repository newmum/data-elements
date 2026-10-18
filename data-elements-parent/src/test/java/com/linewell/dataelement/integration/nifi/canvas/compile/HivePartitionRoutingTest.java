package com.linewell.dataelement.integration.nifi.canvas.compile;

import com.linewell.dataelement.metautil.ddl.TablePartitions;
import com.linewell.dataelement.metautil.model.dto.ColumnInfo;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import groovy.lang.*;
import java.io.*;
import java.util.*;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.io.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class HivePartitionRoutingTest {
    @Test void actualGroovySeparatesMonthsAndRetainsTargetColumnOrderWithoutWritingPartitionInPayload() throws Exception {
        var field=new ColumnInfo();field.setColumnName("created_at");field.setColumnType("TIMESTAMP");
        var plan=new TablePartitions().normalize(new LinkedHashMap<>(Map.of("enabled",true,"strategy","MONTH","field","created_at","startMonth","2026-01","months",2)),List.of(field),DatabaseType.HIVE);
        List<String> payloads=new ArrayList<>(),buckets=new ArrayList<>();
        execute(plan,"[{\"CREATED_AT\":\"2026-01-31 23:59:59\",\"ID\":1},{\"id\":2,\"created_at\":\"2026-02-01 00:00:00\"}]",payloads,buckets);
        assertEquals(List.of("202601","202602"),buckets);
        assertEquals("[{\"id\":1,\"created_at\":\"2026-01-31 23:59:59\"}]",payloads.getFirst());
        assertFalse(payloads.toString().contains("ods_partition"));
    }
    @Test void unknownListValueRejectsWholeBatchBeforeCreatingAnyOutput() throws Exception {
        var field=new ColumnInfo();field.setColumnName("region_code");field.setColumnType("STRING");
        var plan=new TablePartitions().normalize(new LinkedHashMap<>(Map.of("enabled",true,"strategy","LIST","field","region_code","partitions",List.of(Map.of("name","p_1","bucket","350100","values",List.of("350100"))))),List.of(field),DatabaseType.HIVE);
        List<String> payloads=new ArrayList<>(),buckets=new ArrayList<>();
        assertThrows(Exception.class,()->execute(plan,"[{\"id\":1,\"region_code\":\"350100\"},{\"id\":2,\"region_code\":\"unknown\"}]",payloads,buckets));
        assertTrue(payloads.isEmpty());assertTrue(buckets.isEmpty());
    }
    private void execute(Map<String,Object> plan,String input,List<String> payloads,List<String> buckets) throws Exception {
        var locations=new LinkedHashMap<String,String>();
        for(Object p:(List<?>)plan.get("partitions")){String bucket=String.valueOf(((Map<?,?>)p).get("bucket"));locations.put(bucket,"hdfs://cluster/custom/"+bucket);}
        plan.put("locations",locations);
        var session=mock(ProcessSession.class);var flow=mock(FlowFile.class);
        when(session.get()).thenReturn(flow);when(flow.getSize()).thenReturn((long)input.length());
        doAnswer(call->{((InputStreamCallback)call.getArgument(1)).process(new ByteArrayInputStream(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)));return null;}).when(session).read(eq(flow),any(InputStreamCallback.class));
        when(session.create(flow)).thenAnswer(call->mock(FlowFile.class));
        when(session.write(any(FlowFile.class),any(OutputStreamCallback.class))).thenAnswer(call->{var out=new ByteArrayOutputStream();((OutputStreamCallback)call.getArgument(1)).process(out);payloads.add(out.toString(java.nio.charset.StandardCharsets.UTF_8));return call.getArgument(0);});
        when(session.putAllAttributes(any(FlowFile.class),anyMap())).thenAnswer(call->{Map<String,String> attrs=call.getArgument(1);buckets.add(attrs.get("hive.partition.bucket"));assertEquals("hdfs://cluster/custom/"+attrs.get("hive.partition.bucket"),attrs.get("hive.partition.directory"));return call.getArgument(0);});
        var success=new Relationship.Builder().name("success").build();
        new GroovyShell(new Binding(Map.of("session",session,"REL_SUCCESS",success))).evaluate(HivePartitionRouting.script(plan,List.of("id",String.valueOf(plan.get("field")))));
        verify(session).remove(flow);
    }
}
