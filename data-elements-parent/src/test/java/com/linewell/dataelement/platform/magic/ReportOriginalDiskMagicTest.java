package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockMultipartFile;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;

class ReportOriginalDiskMagicTest {
    private static final String TENANT = "validation-tenant";
    private static final String KEY = "report-local:1234567890abcdef1234567890abcdef";
    private static final byte[] ORIGINAL = "编号,名称\n1,验证\n".getBytes(StandardCharsets.UTF_8);

    @TempDir Path storageRoot;

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

    private MagicScript storageScript() throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (!Files.isDirectory(root.resolve("magic"))) root = root.getParent();
        var source = Files.readString(root.resolve("magic/function/01.通用函数/85.数据上报原件磁盘存储.ms"));
        var script = source.substring(source.indexOf("================================") + 32)
                .replace("import org.springframework.core.env.Environment as environment", "");
        return MagicScript.create(script, null);
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
