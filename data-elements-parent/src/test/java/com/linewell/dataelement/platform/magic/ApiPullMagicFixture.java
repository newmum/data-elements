package com.linewell.dataelement.platform.magic;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.function.MagicScriptLambdaFunction;

/** Binds the registered function imports to their canonical source in isolated API tests. */
final class ApiPullMagicFixture {
    private ApiPullMagicFixture() {
    }

    static MagicScriptLambdaFunction definitionError() throws IOException {
        var script = MagicScript.create(CanonicalMagicSources.byPath(
                "function/01.通用函数/33.API拉取定义校验.ms", "cd24c84c195a4320865914afd5dd4896"), null);
        return (runtime, args) -> script.execute(
                new MagicScriptContext(Collections.singletonMap("definitions", args[0])));
    }

    static MagicScriptLambdaFunction nifiSource() throws IOException {
        var script = MagicScript.create(CanonicalMagicSources.byPath(
                "function/01.通用函数/34.NiFi拉取来源配置.ms", "dfd3f3a9d4fa46c8a6f9565bf6948b8a"), null);
        return (runtime, args) -> {
            var parameters = new LinkedHashMap<String, Object>();
            parameters.put("source", args[0]);
            parameters.put("datasourceId", args[1]);
            parameters.put("tableName", args[2]);
            return script.execute(new MagicScriptContext(parameters));
        };
    }
}
