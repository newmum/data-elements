package com.linewell.dataelement.integration.nifi.canvas.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.linewell.dataelement.platform.magic.TableSortMagicModule;
import java.math.BigDecimal;
import java.util.*;

/** Orders one complete NiFi group snapshot; no per-processor remote requests. */
final class ProcessorStatusPage {
    private ProcessorStatusPage() {}
    static Map<String,Object> page(JsonNode status,String field,String direction,int page,int size) {
        if(page<1||size<1||size>100)throw new IllegalArgumentException("处理器分页参数无效");
        List<Map<String,Object>> rows=new ArrayList<>();
        Deque<JsonNode> pending=new ArrayDeque<>();
        if(status!=null&&!status.isNull())pending.add(status.path("processGroupStatus").path("aggregateSnapshot").isMissingNode()
            ?status.path("aggregateSnapshot").isMissingNode()?status:status.path("aggregateSnapshot")
            :status.path("processGroupStatus").path("aggregateSnapshot"));
        int groups=0;
        while(!pending.isEmpty()) {
            if(++groups>1000)throw new IllegalArgumentException("流程状态范围过大，请拆分流程后查看");
            JsonNode group=pending.removeFirst();
            for(JsonNode wrapper:group.path("processorStatusSnapshots")) {
                if(rows.size()>=1000)throw new IllegalArgumentException("处理器数量过多，请拆分流程后查看");
                JsonNode row=wrapper.has("processorStatusSnapshot")?wrapper.path("processorStatusSnapshot"):wrapper;
                String id=row.path("id").asText("");if(id.isBlank())throw new IllegalStateException("处理器状态缺少标识");
                Map<String,Object> mapped=new LinkedHashMap<>();mapped.put("key",id);
                for(String key:List.of("name","type"))mapped.put(key,row.path(key).asText(""));
                mapped.put("state",row.path("runStatus").asText(""));
                mapped.put("flowFilesIn",display(row,"input"));mapped.put("flowFilesOut",display(row,"output"));
                mapped.put("bytesIn",display(row,"read"));mapped.put("bytesOut",display(row,"written"));mapped.put("tasks",display(row,"tasks"));
                mapped.put("flowFilesInCount",number(row,"flowFilesIn","input",false));
                mapped.put("flowFilesOutCount",number(row,"flowFilesOut","output",false));
                mapped.put("bytesInCount",number(row,"bytesRead","read",true));
                mapped.put("bytesOutCount",number(row,"bytesWritten","written",true));
                mapped.put("tasksCount",number(row,"taskCount","tasks",false));rows.add(mapped);
            }
            for(JsonNode wrapper:group.path("processGroupStatusSnapshots"))pending.add(wrapper.has("processGroupStatusSnapshot")?wrapper.path("processGroupStatusSnapshot"):wrapper);
        }
        Map<String,Object> options=new HashMap<>();if(field!=null)options.put("sortField",field);if(direction!=null)options.put("sortDir",direction);
        var ordered=new TableSortMagicModule().rows(rows,options,Map.of("name","name","flowFilesIn","flowFilesInCount","flowFilesOut","flowFilesOutCount","bytesIn","bytesInCount","bytesOut","bytesOutCount","tasks","tasksCount"),"name","key");
        long offset=Math.multiplyExact((long)page-1,size);int start=(int)Math.min(offset,ordered.size()),end=Math.min(start+size,ordered.size());
        return Map.of("list",ordered.subList(start,end),"total",rows.size(),"page",page,"size",size);
    }
    private static String display(JsonNode row,String key) {return row.hasNonNull(key)?row.path(key).asText():"—";}
    private static BigDecimal number(JsonNode row,String raw,String formatted,boolean bytes) {
        if(row.path(raw).isNumber())return row.path(raw).decimalValue();
        String text=row.path(formatted).asText("").trim().replace(",","");
        var match=java.util.regex.Pattern.compile("^([0-9]+(?:\\.[0-9]+)?)(?:\\s*(B|KB|MB|GB|TB))?",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text);
        if(!match.find())return null;BigDecimal value=new BigDecimal(match.group(1));
        if(bytes&&match.group(2)!=null){int power=List.of("B","KB","MB","GB","TB").indexOf(match.group(2).toUpperCase(Locale.ROOT));value=value.multiply(BigDecimal.valueOf(1024).pow(power));}
        return value;
    }
}
