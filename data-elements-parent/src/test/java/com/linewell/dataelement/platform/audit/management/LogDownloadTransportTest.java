package com.linewell.dataelement.platform.audit.management;

import com.linewell.dataelement.model.common.CommonResponse;
import com.linewell.dataelement.platform.magic.CanonicalMagicSources;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.function.MagicScriptLambdaFunction;
import static org.junit.jupiter.api.Assertions.*;

class LogDownloadTransportTest {
    @TempDir Path directory;

    @Test void binaryExportsExposeAStreamingResourceAndAttachmentHeaders() throws Exception {
        for (String extension : List.of("csv", "jsonl")) {
            String filename = "validation." + extension;
            Path file = Files.writeString(directory.resolve(filename), "content");
            var response = serve(Map.of("id", "validation", "resolve", (MagicScriptLambdaFunction) (ctx, args) -> {
                assertEquals("validation", args[0]);
                return file;
            }));
            assertEquals(extension.equals("csv") ? "text/csv;charset=UTF-8" : "application/x-ndjson;charset=UTF-8",
                    response.getHeaders().getContentType().toString());
            assertEquals("no-store", response.getHeaders().getCacheControl());
            assertEquals("attachment; filename=\"" + filename + "\"", response.getHeaders().getFirst("Content-Disposition"));
            var resource = assertInstanceOf(Resource.class, response.getBody());
            assertEquals(filename, resource.getFilename());
            assertEquals(7, resource.contentLength());
        }
    }

    @Test void missingOrUnauthorizedJobReturnsAnErrorWithoutAnAttachment() throws Exception {
        var missing = serve(Map.of("resolve", (MagicScriptLambdaFunction) (ctx, args) -> fail("读取了缺少标识的任务")));
        var denied = serve(Map.of("id", "validation-foreign", "resolve", (MagicScriptLambdaFunction) (ctx, args) -> {
            throw new SecurityException("导出任务不存在或无权下载");
        }));
        assertEquals(20001, assertInstanceOf(CommonResponse.class, missing.getBody()).getCode());
        assertEquals(403, assertInstanceOf(CommonResponse.class, denied.getBody()).getCode());
        assertNull(denied.getHeaders().getFirst("Content-Disposition"));
    }

    private static ResponseEntity<?> serve(Map<String, Object> variables) throws Exception {
        var script = MagicScript.create(CanonicalMagicSources.byPath(
                "function/01.通用函数/77.返回日志导出附件.ms", "log_export_attachment_v1"), null);
        return assertInstanceOf(ResponseEntity.class, script.execute(new MagicScriptContext(variables)));
    }
}
