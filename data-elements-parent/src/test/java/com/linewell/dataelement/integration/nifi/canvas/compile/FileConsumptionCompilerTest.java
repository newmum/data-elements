package com.linewell.dataelement.integration.nifi.canvas.compile;

import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.model.nifi.NifiEntity;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileConsumptionCompilerTest {
    record Processor(String id,String type,Map<String,String> properties) {}
    record Link(String from,String to,List<String> relationships) {}

    @ParameterizedTest @ValueSource(booleans={true,false})
    void readsOnlyCompletedFilesAndDeletesOnlyAfterJdbcSuccess(boolean sftp) {
        var client=mock(NifiClient.class);var processors=new ArrayList<Processor>();var links=new ArrayList<Link>();
        when(client.createProcessor(anyString(),anyString(),anyString(),anyDouble(),anyDouble(),anyMap(),any(),any())).thenAnswer(a->{
            String id="processor-"+processors.size();processors.add(new Processor(id,a.getArgument(1),new LinkedHashMap<>(a.getArgument(5))));return entity(id);
        });
        when(client.createConnection(anyString(),anyString(),anyString(),anyString(),anyString(),any())).thenAnswer(a->{links.add(new Link(a.getArgument(1),a.getArgument(3),a.getArgument(5)));return entity("connection-"+links.size());});
        when(client.createRetentionFunnel(anyString(),anyDouble(),anyDouble())).thenReturn(entity("retained"));
        var cfg=Map.<String,Object>of("fileConsumptionPolicy",FileConsumptionCompiler.POLICY,"protocol",sftp?"sftp":"ftp","remotePath","/root/source/table","hostname","files.invalid","port",sftp?22:21,"username","validation","password","test-only");
        var source=new Pipeline.Node("source",sftp?"source.sftp":"source.ftp","文件来源","source",0,0,cfg);
        var sink=new Pipeline.Node("sink","sink.jdbc","目标库","sink",1000,0,Map.of());
        var pipeline=pipeline(List.of(source,sink),List.of(new Pipeline.Edge("e","source","sink",null)));
        FileConsumptionCompiler.validate(pipeline);var compiler=new FileConsumptionCompiler(client);
        var compiledSource=new DslCompiler.NodeCompilation();var compiledSink=new DslCompiler.NodeCompilation();compiledSink.processorIds.put("put","jdbc-writer");
        compiler.fetch("pg",source,compiledSource,Map.of("Hostname","files.invalid","Port",sftp?"22":"21","Username","validation","Password","test-only"),"/root/source/table","60 sec",sftp,0,0);
        var initialFetch=processors.get(2);assertEquals("None",initialFetch.properties().get("Completion Strategy"));
        assertEquals("(?i).*\\.json\\.done$",processors.getFirst().properties().get("File Filter Regex"));
        assertTrue(links.stream().anyMatch(l->l.to().equals("retained")&&l.relationships().contains("not.found")));
        assertTrue(processors.stream().noneMatch(p->p.type().endsWith("DeleteSFTP")||"Delete File".equals(p.properties().get("Completion Strategy"))));
        compiler.acknowledge("pg",pipeline,Map.of("source",compiledSource,"sink",compiledSink));
        var data=processors.get(3);var marker=processors.get(4);
        assertTrue(links.stream().anyMatch(l->l.from().equals("jdbc-writer")&&l.to().equals(data.id())&&l.relationships().equals(List.of("success"))));
        assertTrue(links.stream().anyMatch(l->l.from().equals(data.id())&&l.to().equals(marker.id())&&l.relationships().contains(sftp?"not found":"not.found")));
        assertTrue(links.stream().noneMatch(l->l.from().equals("jdbc-writer")&&l.relationships().contains("failure")));
        verify(client).executeOnPrimaryNode(processors.getFirst().id());
    }

    @Test void rejectsMultipleTargetsBeforeCreatingAnyNativeProcessors() {
        var source=new Pipeline.Node("source","source.sftp","文件来源","source",0,0,Map.of("fileConsumptionPolicy",FileConsumptionCompiler.POLICY));
        var sink=new Pipeline.Node("sink","sink.jdbc","目标库","sink",0,0,Map.of());
        var second=new Pipeline.Node("second","sink.jdbc","第二目标","sink",0,0,Map.of());
        assertThrows(IllegalArgumentException.class,()->FileConsumptionCompiler.validate(pipeline(List.of(source,sink,second),List.of())));
        assertThrows(IllegalArgumentException.class,()->FileConsumptionCompiler.validate(pipeline(List.of(source,sink),List.of())));
    }
    private Pipeline pipeline(List<Pipeline.Node> nodes,List<Pipeline.Edge> edges){return new Pipeline("validation","文件消费验证",null,null,null,new Pipeline.Dsl(1,nodes,edges),null,null,null,null,null,null,null);}
    private NifiEntity entity(String id){return new NifiEntity(null,Map.of("id",id),null,null);}
}
