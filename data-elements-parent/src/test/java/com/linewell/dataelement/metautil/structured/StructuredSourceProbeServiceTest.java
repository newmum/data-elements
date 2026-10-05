package com.linewell.dataelement.metautil.structured;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.net.ftp.FTPClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StructuredSourceProbeServiceTest {

    private StructuredSourceProbeService service;
    private AtomicInteger requestCount;

    @BeforeEach
    void setUp() throws Exception {
        requestCount = new AtomicInteger();
        HttpClient httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        String body = """
                {"data":{"persons":[{"personId":"001","name":"张三"}],
                "cases":[{"caseId":"A01","orgCode":"0350100"}]},"success":true}
                """;
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    requestCount.incrementAndGet();
                    return response;
                });
        service = new StructuredSourceProbeService(new ObjectMapper(), httpClient);
    }

    @Test
    void discoversMultipleCollectionsAndPreservesIdentifierTypes() {
        Map<String, Object> source = apiSource();

        StructuredProbeResult result = service.probe(source, true);

        assertThat(result.getTables()).extracting(item -> item.getSourcePath())
                .containsExactly("$.data.persons", "$.data.cases");
        assertThat(result.getTables().get(0).getColumns()).extracting("columnName")
                .containsExactly("personId", "name");
        assertThat(result.getTables().get(0).getColumns().get(0).getDataType()).isEqualTo("VARCHAR");
        assertThat(result.getTables().get(1).getColumns().get(1).getDataType()).isEqualTo("VARCHAR");
    }

    @Test
    void limitsProbeToConfiguredCollectionPaths() {
        Map<String, Object> source = apiSource();
        source.put("apiCollectionPaths", "[\"$.data.cases\"]");

        StructuredProbeResult result = service.probe(source, true);

        assertThat(result.getTables()).hasSize(1);
        assertThat(result.getTables().get(0).getSourcePath()).isEqualTo("$.data.cases");
        assertThat(service.sampleData(source, "api_cases", 10).getActualSize()).isEqualTo(1);
    }

    @Test
    void reusesProbeWhenOnlyPersistedDatasourceMetadataChanges() {
        Map<String, Object> formSource = apiSource();
        service.probe(formSource, true);

        Map<String, Object> persistedSource = new LinkedHashMap<>(formSource);
        persistedSource.put("tid", "datasource-001");
        persistedSource.put("dbName", "公安接口数据源");
        persistedSource.put("assetStatus", 0);

        assertThat(service.getTablesPage(persistedSource, 1, 20, null).getTotal()).isEqualTo(2);
        assertThat(requestCount).hasValue(1);
    }

    @Test
    void parsesJsonFileWithMultipleCollections() throws Exception {
        String content = """
                {"persons":[{"person_id":"001","name":"张三"}],
                 "cases":[{"case_no":"A01","status":"有效"}]}
                """;

        StructuredProbeResult result = service.parseFileForTest(
                "public_security.json",
                content.getBytes(StandardCharsets.UTF_8)
        );

        assertThat(result.getTables()).extracting(item -> item.getTable().getTableName())
                .containsExactly("public_security_persons", "public_security_cases");
        assertThat(result.getTables().get(0).getColumns()).extracting("columnName")
                .containsExactly("person_id", "name");
    }

    @Test
    void parsesTabDelimitedTxtWithoutRequiringDelimiterConfiguration() throws Exception {
        String content = "\"id\"\t\"name\"\t\"org_code\"\n"
                + "\"001\"\t\"张三\"\t\"350100\"\n"
                + "\"002\"\t\"李四\"\t\"350200\"\n";

        StructuredProbeResult result = service.parseFileForTest(
                "police_person.txt",
                content.getBytes(StandardCharsets.UTF_8)
        );

        assertThat(result.getTables()).hasSize(1);
        assertThat(result.getTables().get(0).getColumns()).extracting("columnName")
                .containsExactly("id", "name", "org_code");
        assertThat(result.getTables().get(0).getRows()).hasSize(2);
    }

    @Test
    void parsesJsonLinesStoredAsTxt() throws Exception {
        String content = "{\"event_id\":\"E01\",\"level\":1}\n"
                + "{\"event_id\":\"E02\",\"level\":2}\n";

        StructuredProbeResult result = service.parseFileForTest(
                "events.txt",
                content.getBytes(StandardCharsets.UTF_8)
        );

        assertThat(result.getTables().get(0).getColumns()).extracting("columnName")
                .containsExactly("event_id", "level");
        assertThat(result.getTables().get(0).getRows()).hasSize(2);
    }

    @Test
    void preservesDelimitedHeadersWhenFileHasNoDataRows() throws Exception {
        StructuredProbeResult result = service.parseFileForTest(
                "empty.csv",
                "id,name,org_code\n".getBytes(StandardCharsets.UTF_8)
        );

        assertThat(result.getTables().get(0).getColumns()).extracting("columnName")
                .containsExactly("id", "name", "org_code");
        assertThat(result.getTables().get(0).getRows()).isEmpty();
    }

    @Test
    void skipsEmptyJsonCollectionWithoutAnInferableSchema() throws Exception {
        StructuredProbeResult result = service.parseFileForTest(
                "empty.json",
                "[]".getBytes(StandardCharsets.UTF_8)
        );

        assertThat(result.getTables()).isEmpty();
    }

    @Test
    void samplesOnlyTenRowsFromLargeDeclaredRemoteJsonFile() throws Exception {
        StringBuilder content = new StringBuilder("[");
        for (int index = 1; index <= 25; index++) {
            if (index > 1) content.append(',');
            content.append("{\"id\":").append(index).append(",\"name\":\"row-").append(index).append("\"}");
        }
        content.append(']');

        StructuredProbeResult result = service.parseRemoteFileSampleForTest(
                "large.json",
                21L * 1024 * 1024,
                content.toString().getBytes(StandardCharsets.UTF_8)
        );

        assertThat(result.getTables()).hasSize(1);
        assertThat(result.getTables().get(0).getRows()).hasSize(10);
        assertThat(result.getTables().get(0).getRows().get(9)).containsEntry("id", 10L);
    }

    @Test
    void acceptsRootObjectJsonFromRemoteFtpStreamAsOneRecord() throws Exception {
        String content = """
                {"event_id":"E01","event":{"source":"ftp","level":2},"received":true}
                """;

        StructuredProbeResult result = service.parseRemoteFileSampleForTest(
                "event.json",
                21L * 1024 * 1024,
                content.getBytes(StandardCharsets.UTF_8)
        );

        assertThat(result.getTables()).hasSize(1);
        assertThat(result.getTables().get(0).getColumns()).extracting("columnName")
                .containsExactly("event_id", "event_source", "event_level", "received");
        assertThat(result.getTables().get(0).getRows()).containsExactly(Map.of(
                "event_id", "E01", "event_source", "ftp", "event_level", 2L, "received", true));
    }

    @Test
    void samplesOnlyTenRowsFromRemoteCsvFile() throws Exception {
        StringBuilder content = new StringBuilder("id,name\n");
        for (int index = 1; index <= 25; index++) {
            content.append(index).append(",row-").append(index).append('\n');
        }

        StructuredProbeResult result = service.parseRemoteFileSampleForTest(
                "large.csv",
                200L * 1024 * 1024,
                content.toString().getBytes(StandardCharsets.UTF_8)
        );

        assertThat(result.getTables()).hasSize(1);
        assertThat(result.getTables().get(0).getRows()).hasSize(10);
        assertThat(result.getTables().get(0).getRows().get(9)).containsEntry("id", "10");
    }

    @Test
    void acceptsExpected426Then226WhenStoppingAnFtpSampleEarly() throws Exception {
        FTPClient client = mock(FTPClient.class);
        when(client.abort()).thenReturn(false);
        when(client.getReplyCode()).thenReturn(426);
        when(client.getReply()).thenReturn(226);

        var stream = new StructuredSourceProbeService.PendingFtpInputStream(
                new java.io.ByteArrayInputStream("unread remainder".getBytes(StandardCharsets.UTF_8)), client);
        stream.read();
        stream.close();

        verify(client).abort();
        verify(client).getReply();
    }

    @Test
    void samplesOnlyFirstMatchingFtpFileWhenConfigured() throws Exception {
        StructuredProbeResult result = new StructuredProbeResult();
        List<StructuredSourceProbeService.RemoteFile> files = List.of(
                new StructuredSourceProbeService.RemoteFile("/data/b.json", "b.json", 32),
                new StructuredSourceProbeService.RemoteFile("/data/a.json", "a.json", 32)
        );

        StructuredSourceProbeService.RemoteFileProbeStats stats = service.parseRemoteFiles(
                Map.of("ftpSingleFile", true, "ftpCharset", "UTF-8", "ftpDelimiter", ","),
                files,
                file -> new ByteArrayInputStream(("[{\"file\":\"" + file.name() + "\"}]")
                        .getBytes(StandardCharsets.UTF_8)),
                result
        );

        assertThat(stats.matchedFiles()).isEqualTo(1);
        assertThat(result.getTables()).extracting(item -> item.getSourcePath()).containsExactly("/data/a.json");
    }

    @Test
    void retainsReadableFtpFilesWhenAnotherMatchingFileIsInvalid() throws Exception {
        StructuredProbeResult result = new StructuredProbeResult();
        List<StructuredSourceProbeService.RemoteFile> files = List.of(
                new StructuredSourceProbeService.RemoteFile("/data/bad.json", "bad.json", 16),
                new StructuredSourceProbeService.RemoteFile("/data/good.json", "good.json", 32)
        );

        StructuredSourceProbeService.RemoteFileProbeStats stats = service.parseRemoteFiles(
                Map.of("ftpCharset", "UTF-8", "ftpDelimiter", ","),
                files,
                file -> new ByteArrayInputStream(("bad.json".equals(file.name()) ? "not json" : "[{\"id\":1}]")
                        .getBytes(StandardCharsets.UTF_8)),
                result
        );

        assertThat(stats.matchedFiles()).isEqualTo(2);
        assertThat(stats.skippedFiles()).hasSize(1);
        assertThat(result.getTables()).extracting(item -> item.getSourcePath()).containsExactly("/data/good.json");
    }

    private Map<String, Object> apiSource() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("dbType", "api");
        source.put("apiMethod", "GET");
        source.put("apiUrl", "https://example.test/data");
        return source;
    }
}
