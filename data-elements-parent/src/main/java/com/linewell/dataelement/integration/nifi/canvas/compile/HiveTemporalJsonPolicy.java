package com.linewell.dataelement.integration.nifi.canvas.compile;

import java.util.*;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.HiveSinkPublicationPolicy;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;

/** Public JSON service names and Hive connection recognition. Codec settings belong to NiFi. */
final class HiveTemporalJsonPolicy {
    static final String READER_NAME = "PUBLIC-READER-JSON";
    static final String DEFAULT_WRITER_NAME = "PUBLIC-WRITER-JSON";
    static final String WRITER_NAME = "PUBLIC-WRITER-JSON-TIME_FORMAT";

    private HiveTemporalJsonPolicy() {}

    static boolean isHive(String manifestKey, Map<String, ?> config) {
        if ("source.hive".equals(manifestKey)) return true;
        if (config == null) return false;
        Object type = config.get("dbType");
        if (type != null && "HIVE".equals(type.toString().trim().toUpperCase(Locale.ROOT))) return true;
        Object url = config.get("jdbcUrl");
        if (url == null) return false;
        String normalized = url.toString().trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("jdbc:hive2:") || normalized.startsWith("jdbc:hive:");
    }

    /** Classify connected Hive branches once; unrelated database branches keep the ordinary writer. */
    static Set<String> hiveWriterNodes(Pipeline published) {
        Set<String> result = new HashSet<>(HiveSinkPublicationPolicy.upstreamNodeIds(published));
        if(published==null||published.dsl()==null||published.dsl().nodes()==null)return Set.copyOf(result);
        Map<String,Pipeline.Node> nodes=new HashMap<>();Map<String,List<String>> outgoing=new HashMap<>(),incoming=new HashMap<>();
        published.dsl().nodes().forEach(node->nodes.put(node.id(),node));
        if(published.dsl().edges()!=null)published.dsl().edges().forEach(edge->{
            outgoing.computeIfAbsent(edge.source(),ignored->new ArrayList<>()).add(edge.target());
            incoming.computeIfAbsent(edge.target(),ignored->new ArrayList<>()).add(edge.source());
        });
        Set<String> ordinaryTargets=new HashSet<>();var ordinaryQueue=new ArrayDeque<String>();
        published.dsl().nodes().stream().filter(node->"sink".equals(node.category())&&!HiveSinkPublicationPolicy.isHiveSink(node))
                .forEach(node->{ordinaryTargets.add(node.id());ordinaryQueue.add(node.id());});
        while(!ordinaryQueue.isEmpty())for(String parent:incoming.getOrDefault(ordinaryQueue.removeFirst(),List.of()))
            if(ordinaryTargets.add(parent))ordinaryQueue.add(parent);
        var queue=new ArrayDeque<String>();Set<String> visited=new HashSet<>();
        published.dsl().nodes().stream().filter(node->node.manifestKey().startsWith("source.")&&isHive(node.manifestKey(),node.config()))
                .forEach(node->{queue.add(node.id());visited.add(node.id());});
        while(!queue.isEmpty()) {
            String id=queue.removeFirst();var node=nodes.get(id);
            if(node!=null&&(!"sink".equals(node.category())||HiveSinkPublicationPolicy.isHiveSink(node))
                    &&("source".equals(node.category())||!ordinaryTargets.contains(id)))result.add(id);
            for(String target:outgoing.getOrDefault(id,List.of()))if(visited.add(target))queue.add(target);
        }
        return Set.copyOf(result);
    }
}
