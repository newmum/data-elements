package com.linewell.dataelement.platform.magic;

import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.compile.DslCompiler;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.MagicResourceLoader;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManualBackfillStartupMagicTest {
    @Test void normalFullStartupDoesNotIncludeTheManualBackfillInItsOneShotProcessorSet()throws Exception {
        var main=new Pipeline.Node("main","source.oracle","主来源","source",0,0,Map.of("syncMode","FULL"));
        var backfill=new Pipeline.Node("backfill","source.oracle","备用补数","source",0,0,Map.of("syncMode","FULL","backfillSource",true));
        var api=new Pipeline.Node("api","source.api","主动API来源","source",0,0,Map.of("scheduleMode","once"));
        var flow=new Pipeline("validation","validation",null,null,null,new Pipeline.Dsl(1,List.of(main,backfill,api),List.of()),null,null,null,null,null,null,null);
        var compiler=mock(DslCompiler.class);when(compiler.supportsSyncSettings("source.oracle")).thenReturn(true);
        synchronized(MagicResourceLoader.class){
            var field=MagicResourceLoader.class.getDeclaredField("classLoader");field.setAccessible(true);
            @SuppressWarnings("unchecked") var previous=(java.util.function.Function<String,Object>)field.get(null);
            MagicResourceLoader.setClassLoader(name->name.equals(DslCompiler.class.getName())?compiler:previous.apply(name));
            try{
                var script=MagicScript.create(CanonicalMagicSources.byPath("function/01.通用函数/57.海通一次性来源处理器.ms","nifi_one_shot_sources_v1"),null);
                var result=script.execute(new MagicScriptContext(Map.of("canvas",flow,"primary",Map.of("main","main-processor","backfill","backfill-processor","api","api-processor"))));
                assertEquals(Set.of("main-processor","api-processor"),result);
                verify(compiler,times(1)).supportsSyncSettings("source.oracle");
            }finally{MagicResourceLoader.setClassLoader(previous);}
        }
    }
}
