package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;

class ReportTextFormatsMagicTest {
    @Test void storageClientHasAnOverallTimeoutShorterThanThePageRequest() throws Exception {
        String source=ReportRegistrationMagicTest.source();
        int start=source.indexOf("var storageHttpClient ="),end=source.indexOf("\n    var minioClient =",start);
        assertTrue(start>=0&&end>start);
        String script="import okhttp3.OkHttpClient\nimport java.time.Duration\n"+source.substring(start,end)
                +"\nreturn {call:storageHttpClient.callTimeoutMillis(),connect:storageHttpClient.connectTimeoutMillis(),read:storageHttpClient.readTimeoutMillis()}";
        var result=assertInstanceOf(Map.class,MagicScript.create(script,null).execute(new MagicScriptContext()));
        assertEquals(20000,((Number)result.get("call")).intValue());
        assertEquals(5000,((Number)result.get("connect")).intValue());
        assertEquals(15000,((Number)result.get("read")).intValue());
    }
    public static class TrackedStream extends java.io.ByteArrayInputStream {
        boolean closed;
        TrackedStream(int size) { super(new byte[size]); }
        @Override public void close() { closed=true; }
    }
    @Test void actualStorageReadIsBoundedAndClosedOnSuccessAndFailure() throws Exception {
        String source=ReportRegistrationMagicTest.source();
        int start=source.indexOf("var readUploadBytes ="),end=source.indexOf("\nvar csvRows",start);
        assertTrue(start>=0&&end>start);
        var script=MagicScript.create(source.substring(start,end)+"\nreturn readUploadBytes(stream)",null);
        var small=new TrackedStream(12);
        assertEquals(12,assertInstanceOf(byte[].class,script.execute(new MagicScriptContext(Map.of("stream",small)))).length);
        assertTrue(small.closed);
        var large=new TrackedStream(20971522);
        assertThrows(Exception.class,()->script.execute(new MagicScriptContext(Map.of("stream",large))));
        assertTrue(large.closed);
        assertEquals(1,large.available(),"Stop after the first byte beyond the size limit");
    }
    @Test void largeCsvUsesNativeScanningAndKeepsTextAndResourceBounds() throws Exception {
        StringBuilder csv=new StringBuilder(java.util.stream.IntStream.rangeClosed(1,10).mapToObj(i->"测试字段"+i).collect(Collectors.joining(","))+"\n");
        for(int i=0;i<12000;i++)for(int c=0;c<10;c++)csv.append("001").append("x".repeat(61)).append(c==9?'\n':',');
        long started=System.nanoTime();
        Map<?,?> result=assertTimeoutPreemptively(java.time.Duration.ofSeconds(20),()->parse("validation.csv",csv.toString().getBytes(StandardCharsets.UTF_8)));
        assertEquals(12001,((List<?>)result.get("matrix")).size());
        assertEquals("001"+"x".repeat(61),((List<?>)((List<?>)result.get("matrix")).get(1)).get(2));
        assertEquals(10,((List<?>)((List<?>)result.get("matrix")).get(1)).size());
        System.out.println("Native CSV bytes="+csv.length()+", rows=12000, elapsedMs="+(System.nanoTime()-started)/1_000_000+", SQL=0");
        for(String invalid:List.of("id,remark\n1,\"unclosed", "id,remark\n1,bad\"quote", "id\n"+"x".repeat(65536), "a,b\n"+",".repeat(201)))assertThrows(Exception.class,()->parse("validation.csv",invalid.getBytes(StandardCharsets.UTF_8)));
        var literal=parse("validation.csv","id,remark\n#001,\"line1\r\nline2\"\n\nnext,value".getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of("#001","line1\r\nline2"),((List<?>)literal.get("matrix")).get(1));
    }
    private Map<?, ?> parse(String filename, byte[] bytes) throws Exception {
        String source = ReportRegistrationMagicTest.source();
        String imports = source.lines().filter(line -> line.startsWith("import java.")
                || line.startsWith("import cn.hutool.") || line.startsWith("import com.fasterxml.jackson."))
                .collect(Collectors.joining("\n"));
        int start = source.indexOf("var positions =");
        int end = source.indexOf("\nif (action == 'parse')", start);
        assertTrue(start >= 0 && end > start, "Execute the actual deployed parser helpers");
        String script = imports + "\nvar text = value => value == null ? '' : (value + '').trim()\n"
                + source.substring(start, end)
                + "\nvar matrix = filename.endsWith('.json') ? jsonRows(bytes) : csvRows(bytes)\n"
                + "return {matrix:matrix,tables:regions(matrix,'validation',filename)}";
        return assertInstanceOf(Map.class, MagicScript.create(script, null).execute(
                new MagicScriptContext(Map.of("filename", filename, "bytes", bytes))));
    }

    @Test void txtRecognizesAllSeparatorsQuotedHeadersMultilineValuesAndLegacyEncoding() throws Exception {
        for (String separator : List.of("\t", ",", ";", "|")) {
            Map<?, ?> result = parse("validation.txt", ("id" + separator + "\"备注,说明\"\r\n001" + separator
                    + "\"第一行\n第二行\"").getBytes(StandardCharsets.UTF_8));
            assertEquals(List.of(List.of("id", "备注,说明"), List.of("001", "第一行\n第二行")), result.get("matrix"));
            assertEquals(1, ((List<?>) result.get("tables")).size());
        }
        Map<?, ?> legacy = parse("validation.txt", "姓名|id\n验证|001".getBytes(Charset.forName("GB18030")));
        assertEquals(List.of("姓名", "id"), ((List<?>) legacy.get("matrix")).getFirst());
        Map<?, ?> leading = parse("validation.txt", "\n \r\n||\r\nid|remark\n001|value".getBytes(StandardCharsets.UTF_8));
        Map<?, ?> table = (Map<?, ?>) ((List<?>) leading.get("tables")).getFirst();
        assertEquals(2, ((Number) table.get("fieldCount")).intValue());
        assertEquals(1, ((Number) table.get("recordCount")).intValue());
    }

    @Test void jsonUnionsHeadersPreservesLongIdentifiersAndKeepsOneTableAcrossEmptyRecords() throws Exception {
        String records = "[{\"id\":12345678901234567890,\"enabled\":true},{},{\"id\":2,\"remark\":\"001\"}]";
        for (String json : List.of(records, "{\"data\":" + records + "}", "{\"rows\":" + records + "}", "{\"list\":" + records + "}")) {
            Map<?, ?> result = parse("validation.json", json.getBytes(StandardCharsets.UTF_8));
            List<?> matrix = (List<?>) result.get("matrix");
            assertEquals(List.of("id", "enabled", "remark"), matrix.getFirst());
            assertEquals(List.of("12345678901234567890", "true", ""), matrix.get(1));
            List<?> tables = (List<?>) result.get("tables");
            assertEquals(1, tables.size());
            Map<?, ?> table = (Map<?, ?>) tables.getFirst();
            assertEquals("success", table.get("status"));
            assertEquals(2, ((Number) table.get("recordCount")).intValue());
            assertEquals(4, ((Number) table.get("endRow")).intValue());
        }
    }

    @Test void malformedNestedAmbiguousAndOversizedJsonFailBeforeAnyDatabaseOrStorageWrites() {
        for (String json : List.of("[{1:2}]", "[{\"id\":01}]", "[{\"id\":1},]", "[{\"id\":NaN}]", "[{\"id\":1}] true")) {
            Exception failure = assertThrows(Exception.class, () -> parse("validation.json", json.getBytes(StandardCharsets.UTF_8)));
            assertTrue(failure.getMessage().contains("JSON 格式不正确"), failure.getMessage());
        }
        for (String json : List.of("[{\"nested\":{\"id\":1}}]", "{\"data\":[],\"rows\":[]}", "[[1,2]]", "[]", "[{}]")) {
            assertThrows(Exception.class, () -> parse("validation.json", json.getBytes(StandardCharsets.UTF_8)));
        }
        String oversized = "[" + "{\"id\":1},".repeat(20000) + "{\"id\":1}]";
        Exception failure = assertThrows(Exception.class, () -> parse("validation.json", oversized.getBytes(StandardCharsets.UTF_8)));
        assertTrue(failure.getMessage().contains("20000"));
        String tooManyObjects = "[" + "{},".repeat(40002) + "{}]";
        Exception bounded = assertThrows(Exception.class, () -> parse("validation.json", tooManyObjects.getBytes(StandardCharsets.UTF_8)));
        assertTrue(bounded.getMessage().contains("对象数量过多"));
    }

    @Test void numericJsonKeysMatchBrowserColumnOrderAndRecordCountsNeedNoDatabaseReads() throws Exception {
        Map<?, ?> ordered = parse("validation.json", "[{\"id\":\"a\"},{\"2\":\"two\",\"1\":\"one\",\"01\":\"zero-one\",\"4294967295\":\"text\"}]".getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of("id", "1", "2", "01", "4294967295"), ((List<?>) ordered.get("matrix")).getFirst());
        for (int count : List.of(20, 100)) {
            String json = "[" + "{\"id\":1},".repeat(count - 1) + "{\"id\":1}]";
            Map<?, ?> result = parse("validation.json", json.getBytes(StandardCharsets.UTF_8));
            assertEquals(count + 1, ((List<?>) result.get("matrix")).size());
            System.out.println("JSON records=" + count + ", parser SQL=0; no database binding provided");
        }
    }
}
