package com.linewell.dataelement.integration.nifi.canvas.compile;

import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.model.nifi.NifiEntity;
import java.util.*;

/** Compiles an acknowledged file transfer using native NiFi processors and durable queues. */
final class FileConsumptionCompiler {
    static final String POLICY = "DELETE_AFTER_DATABASE_COMMIT";
    private static final String STANDARD = "org.apache.nifi.processors.standard.";
    private final NifiClient nifi;

    FileConsumptionCompiler(NifiClient nifi) { this.nifi = nifi; }

    static boolean enabled(Map<String,Object> config) {
        return config != null && POLICY.equals(config.get("fileConsumptionPolicy"));
    }

    static void validate(Pipeline pipeline) {
        var sources = pipeline.dsl().nodes().stream().filter(n -> enabled(n.config())).toList();
        if (sources.isEmpty()) return;
        var sinks = pipeline.dsl().nodes().stream().filter(n -> "sink".equals(n.category())).toList();
        if (sources.size() != 1 || sinks.size() != 1 || !"sink.jdbc".equals(sinks.getFirst().manifestKey())
                || pipeline.dsl().nodes().stream().filter(n -> "source".equals(n.category())).count() != 1
                || !Set.of("source.ftp","source.sftp").contains(sources.getFirst().manifestKey()))
            throw new IllegalArgumentException("成功落库后删除文件仅支持单文件来源、单 JDBC 目标；多目标须拆为接入链路");
        var outgoing = new HashSet<String>();
        for (var edge : pipeline.dsl().edges()) if (!outgoing.add(edge.source()))
            throw new IllegalArgumentException("成功落库后删除文件不允许分叉，避免部分目标未提交即删除来源");
        var reached = new HashSet<String>(List.of(sources.getFirst().id()));
        boolean changed;
        do { changed=false; for(var edge:pipeline.dsl().edges())
            if(reached.contains(edge.source())) changed|=reached.add(edge.target());
        } while(changed);
        if(!reached.contains(sinks.getFirst().id())) throw new IllegalArgumentException("文件来源必须连通到 JDBC 目标");
    }

    NifiEntity fetch(String pgId,Pipeline.Node source,DslCompiler.NodeCompilation compiled,
                     Map<String,String> connection,String remotePath,String period,boolean sftp,double x,double y) {
        var listing = new LinkedHashMap<>(connection);
        listing.put("Remote Path",remotePath);
        listing.put("Listing Strategy","timestamps");
        listing.put("File Filter Regex","(?i).*\\.json\\.done$");
        listing.put("Search Recursively","false");
        var list=nifi.createProcessor(pgId,STANDARD+(sftp?"ListSFTP":"ListFTP"),source.label()+"/已完成文件清单",x,y-400,listing,period,"TIMER_DRIVEN");
        nifi.executeOnPrimaryNode(list.id());
        compiled.processorIds.put("list-completed",list.id());
        var attributes=nifi.createProcessor(pgId,"org.apache.nifi.processors.attributes.UpdateAttribute",source.label()+"/文件消费标识",x,y-200,
                Map.of("ingestion.file.name","${filename:substringBeforeLast('.done')}",
                        "ingestion.file.path",remotePath,"filename","${filename:substringBeforeLast('.done')}"),"0 sec","TIMER_DRIVEN");
        compiled.processorIds.put("file-consumption-attributes",attributes.id());
        var properties=new LinkedHashMap<>(connection);
        properties.put("Remote File",remotePath+"/${ingestion.file.name}");
        properties.put("Completion Strategy","None");
        var fetch=nifi.createProcessor(pgId,STANDARD+(sftp?"FetchSFTP":"FetchFTP"),source.label()+"/fetch",x,y,properties,"0 sec","TIMER_DRIVEN");
        connect(pgId,list.id(),attributes.id(),"success");connect(pgId,attributes.id(),fetch.id(),"success");
        nifi.createConnection(pgId,fetch.id(),"PROCESSOR",fetch.id(),"PROCESSOR",List.of("comms.failure","permission.denied"));
        var retained=nifi.createRetentionFunnel(pgId,x+350,y);
        nifi.createConnection(pgId,fetch.id(),"PROCESSOR",retained.id(),"FUNNEL",List.of("not.found"));
        return fetch;
    }

    void acknowledge(String pgId,Pipeline pipeline,Map<String,DslCompiler.NodeCompilation> compiled) {
        var source=pipeline.dsl().nodes().stream().filter(n->enabled(n.config())).findFirst().orElse(null);
        if(source==null)return;
        var sink=pipeline.dsl().nodes().stream().filter(n->"sink".equals(n.category())).findFirst().orElseThrow();
        var config=source.config();boolean sftp="source.sftp".equals(source.manifestKey())||"sftp".equalsIgnoreCase(text(config,"protocol"));
        var connection=new LinkedHashMap<String,String>();
        for(String key:List.of("hostname","port","username","password"))
            connection.put(Map.of("hostname","Hostname","port","Port","username","Username","password","Password").get(key),text(config,key));
        String root=text(config,"remotePath");while(root.endsWith("/"))root=root.substring(0,root.length()-1);
        var owner=compiled.get(sink.id());String writer=owner.processorIds.get("put");
        if(writer==null)throw new IllegalArgumentException("JDBC 写入节点缺少提交成功出口，无法确认来源文件删除");
        var data=delete(pgId,source,connection,root,sftp,false,sink.x()+400,sink.y());
        var marker=delete(pgId,source,connection,root,sftp,true,sink.x()+800,sink.y());
        owner.processorIds.put("ack-delete-data",data.id());owner.processorIds.put("ack-delete-marker",marker.id());
        connect(pgId,writer,data.id(),"success");
        nifi.createConnection(pgId,data.id(),"PROCESSOR",marker.id(),"PROCESSOR",List.of("success",sftp?"not found":"not.found"));
    }

    private NifiEntity delete(String pgId,Pipeline.Node source,Map<String,String> connection,String root,
                              boolean sftp,boolean marker,double x,double y) {
        var props=new LinkedHashMap<>(connection);
        String name=marker?"${ingestion.file.name:append('.done')}":"${ingestion.file.name}";
        if(sftp){props.put("Removal Strategy","FILE");props.put("Directory Path",root);props.put("Filename",name);}
        else{props.put("Remote File",root+"/"+name);props.put("Completion Strategy","Delete File");}
        var processor=nifi.createProcessor(pgId,STANDARD+(sftp?"DeleteSFTP":"FetchFTP"),source.label()+(marker?"/删除完成标记":"/落库成功后删除数据文件"),x,y,props,"0 sec","TIMER_DRIVEN");
        if(!sftp)nifi.createConnection(pgId,processor.id(),"PROCESSOR",processor.id(),"PROCESSOR",List.of("comms.failure","permission.denied"));
        // Native SFTP failure is retained/retried by finalizeGeneratedProcessGroup.
        return processor;
    }

    private void connect(String pgId,String from,String to,String relationship){nifi.createConnection(pgId,from,"PROCESSOR",to,"PROCESSOR",List.of(relationship));}
    private static String text(Map<String,Object> map,String key){return Objects.toString(map.get(key),"");}
}
