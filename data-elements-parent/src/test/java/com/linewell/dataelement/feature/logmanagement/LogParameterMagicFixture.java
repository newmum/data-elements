package com.linewell.dataelement.feature.logmanagement;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.ssssssss.magicapi.core.service.MagicAPIService;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Runs the actual new local function before registration; deliberately does not require a manifest edit. */
final class LogParameterMagicFixture {
    static final String ID = "log_parameter_condition_v1";
    static final String RELATIVE = "function/01.通用函数/34.日志参数关键词条件.ms";
    private static final String RESOURCE = load();
    private static final int SPLIT = RESOURCE.indexOf("================================");

    private LogParameterMagicFixture() { }

    @SuppressWarnings("unchecked")
    static ObjectProvider<MagicAPIService> provider() {
        ObjectProvider<MagicAPIService> provider = mock(ObjectProvider.class);
        MagicAPIService functions = mock(MagicAPIService.class);
        when(provider.getObject()).thenReturn(functions);
        when(functions.invoke(eq(LogQueryService.PARAMETER_CONDITION_FUNCTION), anyMap()))
                .thenAnswer(call -> execute(call.getArgument(1)));
        return provider;
    }

    static Object execute(Map<String, Object> variables) {
        return MagicScript.create(RESOURCE.substring(SPLIT + 32).strip(), null)
                .execute(new MagicScriptContext(variables));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> metadata() throws IOException {
        return new ObjectMapper().readValue(RESOURCE.substring(0, SPLIT), Map.class);
    }

    private static String load() {
        try {
            String configured = System.getProperty("magic.sourceRoot");
            if (configured != null && !configured.isBlank()) {
                return Files.readString(Path.of(configured).resolve(RELATIVE), StandardCharsets.UTF_8);
            }
            for (Path base = Path.of("").toAbsolutePath(); base != null; base = base.getParent()) {
                Path source = base.resolve("magic").resolve(RELATIVE);
                if (Files.isRegularFile(source)) return Files.readString(source, StandardCharsets.UTF_8);
            }
            throw new IOException("Cannot find canonical new Magic source: " + RELATIVE);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }
}