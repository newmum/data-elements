package com.linewell.dataelement.integration.nifi.canvas.pipeline;

import java.util.*;

/** Only downstream Hive hops in a multi-datasource materialization chain bypass governance. */
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

    public static boolean isHdfsWriteMode(String configuredMode) {
        String mode = Objects.toString(configuredMode, "").trim().toUpperCase(Locale.ROOT);
        return Set.of("", "LINEWELL_HDFS", "LINEWELL", "HDFS_BATCH", "BATCH").contains(mode);
    }

    public static boolean supportsHdfsMerge(Pipeline.Node node) {
        if (node == null || !"sink.hive".equals(node.manifestKey()) || node.config() == null) return false;
        Map<String, Object> config = node.config();
        return isHdfsWriteMode(Objects.toString(config.get("hiveWriteMode"), ""))
                && !(config.get("partitionPlan") instanceof Map<?, ?> plan
                        && Boolean.parseBoolean(Objects.toString(plan.get("enabled"), "false")));
    }

    public static boolean usesHdfsMerge(Pipeline.Node node) {
        return supportsHdfsMerge(node) && !Boolean.FALSE.equals(node.config().get("hiveHdfsMerge"));
    }
    /** Context is stored on the sink so ordinary DSL save/load cannot discard it. */
    public static boolean bypassesGovernance(Pipeline.Node node) {
        if(!isHiveSink(node)||node.config()==null||!(node.config().get("accessChain") instanceof Map<?,?> chain))return false;
        int count=integer(chain.get("targetCount")),datasources=integer(chain.get("targetDatasourceCount")),index=integer(chain.get("targetIndex"));
        String root=Objects.toString(chain.get("rootSourceTableId"),"").trim();
        String source=Objects.toString(chain.get("sourceTableId"),"").trim();
        String target=Objects.toString(chain.get("targetTableId"),"").trim();
        if(target.isBlank()||!target.equals(Objects.toString(node.config().get("targetTableId"),"").trim()))return false;
        if(Boolean.TRUE.equals(chain.get("downstreamHive")))return !source.isBlank()&&!source.equals(target);
        return count>1&&count<=100&&datasources>0&&datasources<=count&&index>0&&index<count
                &&!root.isBlank()&&!source.isBlank()&&!root.equals(source)&&!target.isBlank()
                &&target.equals(Objects.toString(node.config().get("targetTableId"),"").trim());
    }
    /** Copy a verified task/table relationship into its Hive sink; never alter unrelated branches. */
    public static Pipeline.Dsl downstreamTask(Pipeline.Dsl dsl,String source,String target) {
        if(dsl==null||dsl.nodes()==null||source==null||source.isBlank()||target==null||target.isBlank()||source.equals(target))return dsl;
        if(dsl.nodes().stream().noneMatch(n->"source".equals(n.category())&&n.config()!=null&&source.equals(Objects.toString(n.config().get("sourceTableId"),""))))return dsl;
        boolean[] changed={false};
        var nodes=dsl.nodes().stream().map(n->{
            if(!isHiveSink(n)||n.config()==null||!target.equals(Objects.toString(n.config().get("targetTableId"),""))||bypassesGovernance(n))return n;
            var config=new LinkedHashMap<String,Object>(n.config());
            config.put("accessChain",Map.of("downstreamHive",true,"sourceTableId",source,"targetTableId",target));changed[0]=true;
            return new Pipeline.Node(n.id(),n.manifestKey(),n.label(),n.category(),n.x(),n.y(),config);
        }).toList();
        return changed[0]?new Pipeline.Dsl(dsl.version(),nodes,dsl.edges()):dsl;
    }
    public static Pipeline downstreamTask(Pipeline pipeline,String source,String target) {
        if(pipeline==null)return null;
        var dsl=downstreamTask(pipeline.dsl(),source,target);
        if(dsl==pipeline.dsl())return pipeline;
        return new Pipeline(pipeline.id(),pipeline.name(),pipeline.description(),pipeline.createdAt(),pipeline.updatedAt(),dsl,
                pipeline.nifiProcessGroupId(),pipeline.status(),pipeline.lastDeployedHash(),pipeline.lastDeployedAt(),pipeline.lastStoppedAt(),pipeline.nodeMapping(),pipeline.lastBulletinId());
    }
    /** Older generated DSL stored its serial order only at the top level. */
    public static Pipeline.Dsl legacyContext(Pipeline.Dsl dsl,Map<String,Object> context) {
        if(context==null||dsl.nodes()==null||!(context.get("targetTableIds") instanceof List<?> ids)||ids.size()<2||ids.size()>100)return dsl;
        var targets=ids.stream().map(id->Objects.toString(id,"").trim()).toList();
        String root=Objects.toString(context.get("rootSourceTableId"),"").trim();
        if(root.isBlank()||targets.contains("")||new HashSet<>(targets).size()!=targets.size()||targets.contains(root))return dsl;
        var sources=dsl.nodes().stream().filter(n->"source".equals(n.category())&&n.config()!=null)
                .map(n->Objects.toString(n.config().get("sourceTableId"),"")).distinct().toList();
        if(sources.size()!=1||sources.getFirst().isBlank()||root.equals(sources.getFirst()))return dsl;
        int previous=targets.indexOf(sources.getFirst());
        if(previous<0||previous+1>=targets.size())return dsl;
        return downstreamTask(dsl,sources.getFirst(),targets.get(previous+1));
    }
    private static int integer(Object value) {
        if(!(value instanceof Number number))return -1;
        double decimal=number.doubleValue();int integer=number.intValue();
        return Double.isFinite(decimal)&&decimal==integer?integer:-1;
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
        if(dsl==null||dsl.nodes()==null||dsl.edges()==null||dsl.nodes().stream().noneMatch(HiveSinkPublicationPolicy::bypassesGovernance))return dsl;
        Map<String,Pipeline.Node> nodes=new LinkedHashMap<>();dsl.nodes().forEach(node->nodes.put(node.id(),node));
        Map<String,List<Pipeline.Edge>> incoming=new HashMap<>();dsl.edges().forEach(edge->incoming.computeIfAbsent(edge.target(),ignored->new ArrayList<>()).add(edge));
        Set<String> candidates=new LinkedHashSet<>();List<Pipeline.Edge> edges=new ArrayList<>();
        Map<String,List<Pipeline.Edge>> memo=new HashMap<>();
        Set<List<String>> emitted=new HashSet<>();
        for(var edge:dsl.edges()) {
            if(!bypassesGovernance(nodes.get(edge.target()))||!mapping(nodes.get(edge.source()))||!success(edge)){edges.add(edge);continue;}
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
