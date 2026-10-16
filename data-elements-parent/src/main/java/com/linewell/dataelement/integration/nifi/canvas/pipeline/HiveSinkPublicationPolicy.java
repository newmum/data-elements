package com.linewell.dataelement.integration.nifi.canvas.pipeline;

import java.util.*;

/** Hive raw ingestion uses the same branch projection in the editor and at publication. */
public final class HiveSinkPublicationPolicy {
    private HiveSinkPublicationPolicy() {}
    /** Incomplete drafts remain editable; strict graph checks still run before publication. */
    public static Pipeline forCanvas(Pipeline pipeline) {
        try { return forPublication(pipeline); }
        catch (IllegalStateException incomplete) { return pipeline; }
    }
    public static boolean isHiveSink(Pipeline.Node node) {
        if(node==null)return false;
        if("sink.hive".equals(node.manifestKey()))return true;
        if(!"sink".equals(node.category())||node.config()==null)return false;
        return "HIVE".equalsIgnoreCase(Objects.toString(node.config().get("dbType"),""));
    }
    private static boolean mapping(Pipeline.Node node) {
        return node!=null&&Set.of("transform.field-mapping","transform.field-enrichment").contains(node.manifestKey());
    }
    private static boolean success(Pipeline.Edge edge) {
        return edge.outlet()==null||edge.outlet().isBlank()||Set.of("default","success").contains(edge.outlet());
    }
    public static Pipeline forPublication(Pipeline pipeline) {
        if(pipeline==null)return null;
        var dsl=forPublication(pipeline.dsl());if(dsl==pipeline.dsl())return pipeline;
        return new Pipeline(pipeline.id(),pipeline.name(),pipeline.description(),pipeline.createdAt(),pipeline.updatedAt(),dsl,
                pipeline.nifiProcessGroupId(),pipeline.status(),pipeline.lastDeployedHash(),pipeline.lastDeployedAt(),pipeline.lastStoppedAt(),pipeline.nodeMapping(),pipeline.lastBulletinId());
    }
    public static Pipeline.Dsl forPublication(Pipeline.Dsl dsl) {
        if(dsl==null||dsl.nodes()==null||dsl.edges()==null||dsl.nodes().stream().noneMatch(HiveSinkPublicationPolicy::isHiveSink))return dsl;
        Map<String,Pipeline.Node> nodes=new LinkedHashMap<>();dsl.nodes().forEach(node->nodes.put(node.id(),node));
        Map<String,List<Pipeline.Edge>> incoming=new HashMap<>();dsl.edges().forEach(edge->incoming.computeIfAbsent(edge.target(),ignored->new ArrayList<>()).add(edge));
        Set<String> candidates=new LinkedHashSet<>();List<Pipeline.Edge> edges=new ArrayList<>();
        Map<String,List<Pipeline.Edge>> memo=new HashMap<>();
        Set<List<String>> emitted=new HashSet<>();
        for(var edge:dsl.edges()) {
            if(!isHiveSink(nodes.get(edge.target()))||!mapping(nodes.get(edge.source()))||!success(edge)){edges.add(edge);continue;}
            var inputs=rawInputs(edge.source(),nodes,incoming,candidates,new HashSet<>(),memo);
            int index=0;for(var input:inputs) {
                // A diamond of mapping nodes must not duplicate the same raw records.
                if(emitted.add(List.of(input.source(),edge.target(),Objects.toString(input.outlet(),"default"))))
                    edges.add(new Pipeline.Edge(edge.id()+"-hive-raw-"+(index++),input.source(),edge.target(),input.outlet()));
            }
        }
        if(candidates.isEmpty())return dsl;
        // A shared mapping remains deployed when a JDBC/other branch still consumes it.
        Set<String> retained=new HashSet<>();Map<String,List<String>> parents=new HashMap<>();
        for(var edge:edges)if(candidates.contains(edge.source())) {
            if(!candidates.contains(edge.target()))retained.add(edge.source());
            else parents.computeIfAbsent(edge.target(),ignored->new ArrayList<>()).add(edge.source());
        }
        var queue=new ArrayDeque<>(retained);while(!queue.isEmpty())for(String parent:parents.getOrDefault(queue.removeFirst(),List.of()))if(retained.add(parent))queue.add(parent);
        Set<String> removed=new HashSet<>(candidates);removed.removeAll(retained);
        var publishedNodes=dsl.nodes().stream().filter(node->!removed.contains(node.id())).toList();
        var publishedEdges=edges.stream().filter(edge->!removed.contains(edge.source())&&!removed.contains(edge.target())).toList();
        return new Pipeline.Dsl(dsl.version(),publishedNodes,publishedEdges);
    }
    private static List<Pipeline.Edge> rawInputs(String id,Map<String,Pipeline.Node> nodes,Map<String,List<Pipeline.Edge>> incoming,Set<String> candidates,Set<String> visiting,Map<String,List<Pipeline.Edge>> memo) {
        if(memo.containsKey(id))return memo.get(id);
        if(!visiting.add(id))throw new IllegalStateException("Hive原始写入的字段映射链存在循环");
        candidates.add(id);Map<List<String>,Pipeline.Edge> result=new LinkedHashMap<>();
        for(var edge:incoming.getOrDefault(id,List.of())) {
            if(!success(edge))throw new IllegalStateException("Hive原始写入不能绕过字段映射的异常输入分支，请调整为独立错误处理链路");
            var inputs=mapping(nodes.get(edge.source()))?rawInputs(edge.source(),nodes,incoming,candidates,visiting,memo):List.of(edge);
            for(var input:inputs)result.putIfAbsent(List.of(input.source(),Objects.toString(input.outlet(),"default")),input);
        }
        visiting.remove(id);
        if(result.isEmpty())throw new IllegalStateException("Hive原始写入的字段映射缺少来源连接");
        var inputs=List.copyOf(result.values());memo.put(id,inputs);return inputs;
    }
    /** One graph traversal, no per-source database/NiFi reads. */
    public static Set<String> upstreamNodeIds(Pipeline pipeline) {
        if(pipeline==null||pipeline.dsl()==null||pipeline.dsl().nodes()==null)return Set.of();
        var dsl=pipeline.dsl();Map<String,List<String>> incoming=new HashMap<>();
        if(dsl.edges()!=null)dsl.edges().forEach(edge->incoming.computeIfAbsent(edge.target(),ignored->new ArrayList<>()).add(edge.source()));
        Set<String> ids=new HashSet<>();var queue=new ArrayDeque<String>();
        dsl.nodes().stream().filter(HiveSinkPublicationPolicy::isHiveSink).forEach(node->{ids.add(node.id());queue.add(node.id());});
        while(!queue.isEmpty())for(String parent:incoming.getOrDefault(queue.removeFirst(),List.of()))if(ids.add(parent))queue.add(parent);
        return ids;
    }
}
