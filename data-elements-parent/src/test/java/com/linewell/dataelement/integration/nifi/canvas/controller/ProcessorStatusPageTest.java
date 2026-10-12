package com.linewell.dataelement.integration.nifi.canvas.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProcessorStatusPageTest {
    @Test void nestedSnapshotOrdersCompleteCollectionBeforeTwentyOrHundredRows() throws Exception {
        List<Map<String,Object>> rows=new ArrayList<>();
        for(int i=149;i>=0;i--)rows.add(Map.of("processorStatusSnapshot",Map.of("id","p"+i,"name",String.format("name%03d",i),"flowFilesIn",i,"input",i+" (1 KB)","bytesRead",150-i,"read","1 KB","taskCount",i)));
        var snapshot=new ObjectMapper().valueToTree(Map.of("processGroupStatus",Map.of("aggregateSnapshot",Map.of("processGroupStatusSnapshots",List.of(Map.of("processGroupStatusSnapshot",Map.of("processorStatusSnapshots",rows)))))));
        var first=ProcessorStatusPage.page(snapshot,"flowFilesIn","asc",1,20);
        var hundred=ProcessorStatusPage.page(snapshot,"flowFilesIn","asc",1,100);
        assertThat(first.get("total")).isEqualTo(150);
        var small=(List<Map<String,Object>>)first.get("list");var large=(List<Map<String,Object>>)hundred.get("list");
        assertThat(small).isEqualTo(large.subList(0,20));assertThat(small.getFirst().get("key")).isEqualTo("p0");
        assertThat(((List<Map<String,Object>>)ProcessorStatusPage.page(snapshot,"name","desc",2,20).get("list")).getFirst().get("key")).isEqualTo("p129");
        assertThat(((List<Map<String,Object>>)ProcessorStatusPage.page(snapshot,"bytesIn","desc",1,20).get("list")).getFirst().get("key")).isEqualTo("p0");
        assertThatThrownBy(()->ProcessorStatusPage.page(snapshot,"name;delete", "asc",1,20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->ProcessorStatusPage.page(snapshot,"name", "asc",1,101)).isInstanceOf(IllegalArgumentException.class);
    }
}
