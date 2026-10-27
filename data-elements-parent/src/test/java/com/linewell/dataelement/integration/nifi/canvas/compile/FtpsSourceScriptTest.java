package com.linewell.dataelement.integration.nifi.canvas.compile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPSClient;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.junit.jupiter.api.Test;

class FtpsSourceScriptTest {
    static final class Property {final String value;Property(String value){this.value=value;}public String getValue(){return value;}}
    public static final class Snapshot {
        final Map<String,String> values;Snapshot(Map<String,String> values){this.values=values;}
        public Map<String,String> toMap(){return values;}
    }
    public static final class State {
        Map<String,String> values=new LinkedHashMap<>();int writes;
        public Snapshot getState(Object scope){return new Snapshot(new LinkedHashMap<>(values));}
        public boolean replace(Object previous,Map<String,String> next,Object scope){values=new LinkedHashMap<>(next);writes++;return true;}
    }
    public static final class Context {
        final Map<String,String> options;final State state=new State();int yields;
        Context(Map<String,String> options){this.options=options;}
        public Property getProperty(String name){return new Property(options.get(name));}
        public State getStateManager(){return state;}
        public void yield(){yields++;}
    }
    public static final class Flow {
        Map<String,String> attributes=new LinkedHashMap<>();ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        public String getAttribute(String key){return attributes.get(key);}
    }
    public static final class Session {
        List<Flow> transferred=new ArrayList<>();Runnable committed;Flow incoming;
        public Flow create(){return new Flow();}
        public Flow get(){Flow result=incoming;incoming=null;return result;}
        public Flow putAllAttributes(Flow flow,Map<String,String> attributes){flow.attributes.putAll(attributes);return flow;}
        public Flow write(Flow flow,Object callback){InvokerHelper.invokeMethod(callback,"process",flow.bytes);return flow;}
        public void transfer(Flow flow,Object relationship){transferred.add(flow);}
        public void commitAsync(Runnable callback){committed=callback;}
    }
    public static final class Log {public void warn(String message){} }

    private Script script(Context context,Session session) throws Exception {
        var loader=new GroovyClassLoader(getClass().getClassLoader());
        loader.parseClass("package org.apache.nifi.components.state; enum Scope { CLUSTER, LOCAL }");
        loader.parseClass("package org.apache.nifi.processor.io; interface OutputStreamCallback { void process(java.io.OutputStream stream) }");
        String source;try(var input=getClass().getResourceAsStream("/nifi/ftps-source.groovy")) {
            source=new String(Objects.requireNonNull(input).readAllBytes(),StandardCharsets.UTF_8);
        }
        var script=(Script)loader.parseClass(source).getDeclaredConstructor().newInstance();
        script.setBinding(new Binding(Map.of("context",context,"session",session,"REL_SUCCESS","success","log",new Log())));
        return script;
    }
    private FTPFile file(String name,long modified) {
        var file=new FTPFile();file.setType(FTPFile.FILE_TYPE);file.setName(name);file.setSize(2);
        Calendar timestamp=Calendar.getInstance(TimeZone.getTimeZone("UTC"));timestamp.setTimeInMillis(modified);file.setTimestamp(timestamp);
        return file;
    }
    private Context context() {
        return new Context(new LinkedHashMap<>(Map.of("ftps.host","validation.invalid","ftps.path","/",
                "ftps.username","validation","ftps.port","21","ftps.implicit","false")));
    }

    @Test void sourceStateAdvancesOnlyAfterDurableCommitAndDoesNotDeleteFiles() throws Exception {
        Context context=context();Session session=new Session();
        FTPFile[] files={file("first.json",1700000000000L),file("second.json",1700000001000L)};
        try(var clients=mockConstruction(FTPSClient.class,(client,construction)->{
            when(client.getReplyCode()).thenReturn(226);when(client.login(anyString(),anyString())).thenReturn(true);
            when(client.listFiles("/")).thenReturn(files);
            when(client.retrieveFile(anyString(),any())).thenAnswer(call->{call.getArgument(1,java.io.OutputStream.class).write("{}".getBytes(StandardCharsets.UTF_8));return true;});
        })) {
            script(context,session).run();
            assertEquals(2,session.transferred.size());assertEquals(0,context.state.writes);
            session.committed.run();assertEquals(1,context.state.writes);
            session.transferred.clear();script(context,session).run();assertTrue(session.transferred.isEmpty());assertEquals(1,context.yields);
            for(FTPSClient client:clients.constructed()) {
                verify(client).execPBSZ(0);verify(client).execPROT("P");verify(client).setEndpointCheckingEnabled(true);
                verify(client,never()).deleteFile(anyString());
            }
        }
    }

    @Test void failedDownloadNeverAdvancesDiscoveryState() throws Exception {
        Context context=context();Session session=new Session();
        try(var clients=mockConstruction(FTPSClient.class,(client,construction)->{
            when(client.getReplyCode()).thenReturn(226);when(client.login(anyString(),anyString())).thenReturn(true);
            when(client.listFiles("/")).thenReturn(new FTPFile[]{file("first.json",1700000000000L)});
            when(client.retrieveFile(anyString(),any())).thenReturn(false);
        })) {
            Script script=script(context,session);Exception failure=assertThrows(Exception.class,script::run);
            Throwable cause=failure;while(cause.getCause()!=null)cause=cause.getCause();
            assertTrue(cause.getMessage().contains("FTPS 文件下载失败"),cause.toString());
            assertNull(session.committed);assertEquals(0,context.state.writes);
            verify(clients.constructed().getFirst(),never()).deleteFile(anyString());
        }
    }

    @Test void credentialsStayInSensitivePropertiesOutsideScriptBody() {
        var properties=FtpsSourceCompiler.properties(Map.of("protocol","ftps","password",UUID.randomUUID().toString()),"/lib/commons-net.jar",false);
        assertFalse(properties.get("Script Body").contains(properties.get("ftps.password")));
        assertEquals("rollback",properties.get("Failure Strategy"));
        assertEquals("/lib/commons-net.jar",properties.get("Additional Classpath"));
    }
}
