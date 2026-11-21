package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;

import cn.dev33.satoken.stp.StpUtil;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockMultipartFile;
import org.slf4j.LoggerFactory;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;
import org.ssssssss.script.runtime.function.MagicScriptLambdaFunction;

class ReportOriginalDiskMagicTest {
    private static final String TENANT = "validation-tenant";
    private static final String KEY = "report-local:1234567890abcdef1234567890abcdef";
    private static final byte[] ORIGINAL = "编号,名称\n1,验证\n".getBytes(StandardCharsets.UTF_8);

    @TempDir Path storageRoot;

    public static class DownloadResponse {
        final Map<String, String> headers = new HashMap<>();
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        public void setHeader(String name, String value) { headers.put(name, value); }
        public ByteArrayOutputStream getOutputStream() { return bytes; }
        public boolean end() { return true; }
    }

    @Test
    void writesOnceAndReadsTheOriginalFromADifferentScriptContext() throws Exception {
        var script = storageScript();
        var upload = new MockMultipartFile("file", "验证.csv", "text/csv", ORIGINAL);
        assertEquals(true, run(script, "write", TENANT, KEY, upload));
        var file = storageRoot.resolve("report-originals/validation-tenant/1234567890abcdef1234567890abcdef.data");
        assertArrayEquals(ORIGINAL, Files.readAllBytes(file));

        var duplicate = new MockMultipartFile("file", "其他.csv", "text/csv", new byte[]{1});
        assertThrows(Exception.class, () -> run(script, "write", TENANT, KEY, duplicate));
        try (var stream = (InputStream) run(storageScript(), "open", TENANT, KEY, null)) {
            assertArrayEquals(ORIGINAL, stream.readAllBytes());
        }
        assertThrows(Exception.class, () -> run(script, "open", "validation-other", KEY, null));
        assertEquals(true, run(script, "discard", TENANT, KEY, null));
        assertFalse(Files.exists(file));
    }

    @Test
    void rejectsTraversalAndCleansAnIncompleteWrite() throws Exception {
        var script = storageScript();
        var upload = new MockMultipartFile("file", "验证.csv", "text/csv", ORIGINAL) {
            @Override public long getSize() { return super.getSize() + 1; }
        };
        assertThrows(Exception.class, () -> run(script, "write", "../outside", KEY, upload));
        assertThrows(Exception.class, () -> run(script, "write", TENANT, "report-local:../../outside", upload));
        assertThrows(Exception.class, () -> run(script, "write", TENANT, KEY, upload));
        try (var files = Files.list(storageRoot.resolve("report-originals").resolve(TENANT))) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void uploadsThroughTheMagicEntryAndCleansTheFileWhenItsDatabaseRecordIsRejected() throws Exception {
        var fixture = new TableDetailFieldSaveMagicTest.Fixture(0);
        fixture.jdbc.execute("""
                create table file_upload_t (
                    tid varchar(32) primary key, file_path varchar(255), file_name varchar(255) check(file_name <> 'rejected.csv'),
                    file_size int, file_suffix varchar(20), tenant_id varchar(32), created_by varchar(32), updated_by varchar(32),
                    created_time timestamp, updated_time timestamp, is_del int
                )
                """);
        var storage = storageScript();
        var script = MagicScript.create(resourceScript("api/02.数据盘点/03.文件操作/01.业务附件操作.ms")
                .replaceAll("(?m)^import (?:'.*|tenantRuntime|log|response;?|systemConfig)\\r?\\n", ""), null);
        var inputs = new HashMap<String, Object>();
        inputs.put("action", "upload-report");
        inputs.put("db", fixture.db);
        inputs.put("tenantRuntime", fixture.scope);
        inputs.put("log", LoggerFactory.getLogger(ReportOriginalDiskMagicTest.class));
        inputs.put("configData", (MagicScriptLambdaFunction) (context, arguments) -> {
            throw new AssertionError("report upload reached object storage");
        });
        inputs.put("reportOriginal", (MagicScriptLambdaFunction) (context, arguments) ->
                run(storage, (String) arguments[0], (String) arguments[1], (String) arguments[2], (MockMultipartFile) arguments[3]));
        try (var auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsString).thenReturn("validation-user");
            inputs.put("file", new MockMultipartFile("file", "验证.csv", "text/csv", ORIGINAL));
            var result = assertInstanceOf(Map.class, script.execute(new MagicScriptContext(inputs)));
            var id = (String) result.get("tid");
            var directory = storageRoot.resolve("report-originals").resolve(fixture.scope.id());
            assertArrayEquals(ORIGINAL, Files.readAllBytes(directory.resolve(id + ".data")));
            assertEquals("report-local:" + id, fixture.jdbc.queryForObject("select file_path from file_upload_t where tid=?", String.class, id));

            var response = new DownloadResponse();
            inputs.put("fileId", id);
            inputs.put("response", response);
            var download = MagicScript.create(resourceScript("api/02.数据盘点/03.文件操作/02.业务附件下载.ms")
                    .replaceAll("(?m)^import (?:'.*|tenantRuntime|response;?|systemConfig)\\r?\\n", ""), null);
            assertEquals(true, download.execute(new MagicScriptContext(inputs)));
            assertEquals("attachment; filename*=UTF-8''%E9%AA%8C%E8%AF%81.csv", response.headers.get("Content-Disposition"));
            assertArrayEquals(ORIGINAL, response.bytes.toByteArray());

            inputs.put("file", new MockMultipartFile("file", "invalid.exe", "application/octet-stream", ORIGINAL));
            assertInstanceOf(ExitValue.class, script.execute(new MagicScriptContext(inputs)));
            inputs.put("file", new MockMultipartFile("file", "rejected.csv", "text/csv", ORIGINAL));
            assertInstanceOf(ExitValue.class, script.execute(new MagicScriptContext(inputs)));
            assertEquals(1, fixture.jdbc.queryForObject("select count(*) from file_upload_t", Integer.class));
            try (var files = Files.list(directory)) {
                assertEquals(1, files.count());
            }
        }
    }

    private MagicScript storageScript() throws Exception {
        var script = resourceScript("function/01.通用函数/64.数据上报原件磁盘存储.ms")
                .replace("import org.springframework.core.env.Environment as environment", "");
        return MagicScript.create(script, null);
    }

    private String resourceScript(String resource) throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (!Files.isDirectory(root.resolve("magic"))) root = root.getParent();
        var source = Files.readString(root.resolve("magic").resolve(resource));
        return source.substring(source.indexOf("================================") + 32);
    }

    private Object run(MagicScript script, String operation, String tenant, String key, MockMultipartFile file) {
        var variables = new HashMap<String, Object>();
        variables.put("environment", new MockEnvironment().withProperty("storage.root-dir", storageRoot.toString()));
        variables.put("operation", operation);
        variables.put("tenantId", tenant);
        variables.put("storageKey", key);
        variables.put("upload", file);
        return script.execute(new MagicScriptContext(variables));
    }
}
