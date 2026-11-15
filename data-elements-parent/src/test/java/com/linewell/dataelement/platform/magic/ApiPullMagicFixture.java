package com.linewell.dataelement.platform.magic;

import java.io.IOException;
import java.util.Collections;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.function.MagicScriptLambdaFunction;

/** Binds the registered function imports to their canonical source in isolated API tests. */
final class ApiPullMagicFixture {
    private ApiPullMagicFixture() {
    }

    static MagicScriptLambdaFunction definitionError() throws IOException {
        var script = MagicScript.create(CanonicalMagicSources.byPath(
                "function/01.通用函数/21.API拉取定义校验.ms", "cd24c84c195a4320865914afd5dd4896"), null);
        return (runtime, args) -> script.execute(
                new MagicScriptContext(Collections.singletonMap("definitions", args[0])));
    }

}
