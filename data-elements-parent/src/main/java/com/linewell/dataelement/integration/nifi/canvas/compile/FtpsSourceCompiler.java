package com.linewell.dataelement.integration.nifi.canvas.compile;

import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.model.nifi.NifiEntity;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Explicit/implicit FTPS uses TLS for both command and data channels. */
final class FtpsSourceCompiler {
    static final String TYPE = "org.apache.nifi.processors.groovyx.ExecuteGroovyScript";
    private FtpsSourceCompiler() { }

    static boolean enabled(Map<String,Object> config) {
        return "ftps".equalsIgnoreCase(Objects.toString(config.getOrDefault("ftpProtocol",config.get("protocol")),""));
    }

    static NifiEntity create(NifiClient nifi,String pgId,String label,Map<String,Object> config,
                             String libraries,boolean acknowledge,double x,double y) {
        Map<String,String> properties=properties(config,libraries,acknowledge);
        var processor=nifi.createProcessor(pgId,TYPE,label,x,y,properties,
                acknowledge?"0 sec":text(config,"schedulingPeriod","60 sec"),"TIMER_DRIVEN");
        if (!acknowledge) nifi.executeOnPrimaryNode(processor.id());
        return processor;
    }

    static Map<String,String> properties(Map<String,Object> config,String libraries,boolean acknowledge) {
        var properties=new LinkedHashMap<String,String>();
        try(var input=FtpsSourceCompiler.class.getResourceAsStream("/nifi/ftps-source.groovy")) {
            if(input==null)throw new IllegalStateException("缺少 FTPS 抽取脚本");
            properties.put("Script Body",new String(input.readAllBytes(),StandardCharsets.UTF_8));
        } catch(java.io.IOException failure){throw new IllegalStateException("无法读取 FTPS 抽取脚本",failure);}
        properties.put("Failure Strategy","rollback");
        if(libraries!=null&&!libraries.isBlank())properties.put("Additional Classpath",libraries);
        properties.put("ftps.operation",acknowledge?"acknowledge":"fetch");
        properties.put("ftps.host",text(config,"hostname",text(config,"host","")));
        properties.put("ftps.port",text(config,"port",text(config,"ftpPort","990")));
        properties.put("ftps.username",text(config,"username",text(config,"ftpUsername","")));
        properties.put("ftps.password",text(config,"password",text(config,"ftpPassword","")));
        properties.put("ftps.path",text(config,"remotePath",text(config,"ftpPath","/")));
        properties.put("ftps.implicit",text(config,"ftpsImplicit","auto"));
        properties.put("ftps.recursive",text(config,"ftpRecursive","false"));
        properties.put("ftps.passive",text(config,"ftpPassiveMode","true"));
        properties.put("ftps.filter",text(config,"fileFilterRegex",text(config,"ftpFilePattern","(?i).*\\.(csv|json|xlsx|xls|txt)$")));
        properties.put("ftps.maxFiles","100");
        properties.put("ftps.completedOnly",Boolean.toString(FileConsumptionCompiler.enabled(config)));
        properties.put("ftps.trustStorePath",text(config,"nifiTrustStorePath",text(config,"trustStorePath","")));
        properties.put("ftps.trustStorePassword",text(config,"trustStorePassword",""));
        return properties;
    }

    private static String text(Map<String,Object> values,String key,String fallback) {
        Object value=values.get(key);return value==null||value.toString().isBlank()?fallback:value.toString();
    }
}
