package com.linewell.dataelement.integration.nifi.canvas.compile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.manifest.ComponentManifest;
import com.linewell.dataelement.integration.nifi.canvas.manifest.ManifestRegistry;
import com.linewell.dataelement.integration.nifi.canvas.mapping.FieldMappingService;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.model.nifi.NifiEntity;
import com.linewell.dataelement.platform.magic.module.HiveModule;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DslCompilerFileTransferProtocolTest {

    @Test
    void nativeWriterSeparatesSchemaFromTableForJdbcMetadataLookup() {
        Map<String, String> properties = new java.util.LinkedHashMap<>(
                Map.of("Table Name", "FJHYJ.ODS_EXAMPLE"));
        DslCompiler.normalizeNativeJdbcTableProperties(properties);
        org.assertj.core.api.Assertions.assertThat(properties)
                .containsEntry("Schema Name", "FJHYJ")
                .containsEntry("Table Name", "ODS_EXAMPLE");
    }

    @Test
    void genericFtpNodeUsesGetSftpWhenDatasourceProtocolIsSftp() throws Exception {
        NifiClient nifi = mock(NifiClient.class);
        ManifestRegistry registry = mock(ManifestRegistry.class);
        ComponentManifest manifest;
        try (var stream = getClass().getResourceAsStream("/manifests/sources/ftp.json")) {
            manifest = new ObjectMapper().readValue(stream, ComponentManifest.class);
        }
        when(registry.get("source.ftp")).thenReturn(manifest);
        when(nifi.getRootProcessGroupId()).thenReturn("root");
        when(nifi.listProcessGroups("root")).thenReturn(List.of());
        when(nifi.createProcessGroup(anyString(), anyString(), anyDouble(), anyDouble()))
                .thenReturn(entity("pg"));
        when(nifi.createControllerService(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(entity("controller-service"));
        when(nifi.createProcessor(anyString(), anyString(), anyString(), anyDouble(), anyDouble(),
                anyMap(), nullable(String.class), nullable(String.class))).thenReturn(entity("processor"));
        when(nifi.createConnection(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(entity("connection"));

        Pipeline pipeline = new Pipeline("task", "车辆登记信息接入任务", null, null, null,
                new Pipeline.Dsl(1, List.of(new Pipeline.Node("source", "source.ftp", "FTP 文件来源",
                        "source", 0, 0, Map.of(
                                "protocol", "sftp",
                                "hostname", "14.103.233.56",
                                "port", "22",
                                "username", "report-user",
                                "password", "not-a-real-password",
                                "remotePath", "/home", "ftpCharset", "GB18030", "ftpDelimiter", "\t",
                                "fileFormat", "json", "jsonRecordPath", "$.data.people"))), List.of()),
                null, null, null, null, null, null, null);

        new DslCompiler(nifi, registry, mock(FieldMappingService.class), mock(HiveModule.class)).compile(pipeline);

        verify(nifi).createProcessor(eq("pg"), eq("org.apache.nifi.processors.standard.GetSFTP"),
                eq("FTP 文件来源/fetch"), anyDouble(), anyDouble(), argThat(properties ->
                        "false".equals(properties.get("Delete Original"))),
                anyString(), eq("TIMER_DRIVEN"));
        verify(nifi).createControllerService(eq("pg"), anyString(), anyString(), argThat(properties ->
                "XLSX".equals(properties.get("Input File Type"))
                        && "${filename:replaceFirst('^(?:report_[^_]+_[^_]+_)?([^_]+)_.*$', '$1')}"
                        .equals(properties.get("Required Sheets"))));
        verify(nifi).createControllerService(eq("pg"), anyString(), anyString(), argThat(properties ->
                "GB18030".equals(properties.get("Character Set")) && "\t".equals(properties.get("Value Separator"))));
        verify(nifi).createProcessor(eq("pg"), eq("org.apache.nifi.processors.standard.EvaluateJsonPath"),
                eq("FTP 文件来源/JSON 数据集"), anyDouble(), anyDouble(), argThat(properties ->
                        "$.data.people".equals(properties.get("dataset")) && "flowfile-content".equals(properties.get("Destination"))),
                nullable(String.class), nullable(String.class));
        verify(nifi).createProcessor(eq("pg"), eq("org.apache.nifi.processors.standard.RouteOnAttribute"),
                eq("FTP 文件来源/文件类型分流"), anyDouble(), anyDouble(), argThat(properties ->
                        properties.get("json").contains("json|txt")), nullable(String.class), nullable(String.class));
    }

    @Test
    void reportFtpNodeUsesRegisteredTemplateSheetInsteadOfSafeRemoteFilename() throws Exception {
        NifiClient nifi = mock(NifiClient.class);
        ManifestRegistry registry = mock(ManifestRegistry.class);
        ComponentManifest manifest;
        try (var stream = getClass().getResourceAsStream("/manifests/sources/ftp.json")) {
            manifest = new ObjectMapper().readValue(stream, ComponentManifest.class);
        }
        when(registry.get("source.ftp")).thenReturn(manifest);
        when(nifi.getRootProcessGroupId()).thenReturn("root");
        when(nifi.listProcessGroups("root")).thenReturn(List.of());
        when(nifi.createProcessGroup(anyString(), anyString(), anyDouble(), anyDouble()))
                .thenReturn(entity("pg"));
        when(nifi.createControllerService(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(entity("controller-service"));
        when(nifi.createProcessor(anyString(), anyString(), anyString(), anyDouble(), anyDouble(),
                anyMap(), nullable(String.class), nullable(String.class))).thenReturn(entity("processor"));

        Pipeline pipeline = new Pipeline("task", "车辆登记信息接入任务", null, null, null,
                new Pipeline.Dsl(1, List.of(new Pipeline.Node("source", "source.ftp", "FTP 文件来源",
                        "source", 0, 0, Map.of(
                                "protocol", "sftp",
                                "hostname", "14.103.233.56",
                                "port", "22",
                                "username", "report-user",
                                "password", "not-a-real-password",
                                "remotePath", "/home",
                                "excelSheetName", "车辆登记信息"))), List.of()),
                null, null, null, null, null, null, null);

        new DslCompiler(nifi, registry, mock(FieldMappingService.class), mock(HiveModule.class)).compile(pipeline);

        verify(nifi).createControllerService(eq("pg"), anyString(), anyString(), argThat(properties ->
                "XLSX".equals(properties.get("Input File Type"))
                        && "车辆登记信息".equals(properties.get("Required Sheets"))));
    }

    @Test
    void reportFileWithChineseHeadersUsesThoseHeadersInFieldMappingQuery() throws Exception {
        NifiClient nifi = mock(NifiClient.class);
        ManifestRegistry registry = mock(ManifestRegistry.class);
        ComponentManifest ftp = manifest("/manifests/sources/ftp.json");
        ComponentManifest mapping = manifest("/manifests/transforms/field-mapping.json");
        when(registry.get("source.ftp")).thenReturn(ftp);
        when(registry.get("transform.field-mapping")).thenReturn(mapping);
        when(nifi.getRootProcessGroupId()).thenReturn("root");
        when(nifi.listProcessGroups("root")).thenReturn(List.of());
        when(nifi.createProcessGroup(anyString(), anyString(), anyDouble(), anyDouble()))
                .thenReturn(entity("pg"));
        when(nifi.createControllerService(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(entity("controller-service"));
        when(nifi.createProcessor(anyString(), anyString(), anyString(), anyDouble(), anyDouble(),
                anyMap(), nullable(String.class), nullable(String.class))).thenReturn(entity("processor"));
        when(nifi.createConnection(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(entity("connection"));

        Pipeline pipeline = new Pipeline("task", "车辆登记信息接入任务", null, null, null,
                new Pipeline.Dsl(1, List.of(
                        new Pipeline.Node("source", "source.ftp", "FTP 文件来源", "source", 0, 0, Map.of(
                                "protocol", "sftp", "hostname", "10.0.0.1", "port", "22",
                                "username", "report-user", "password", "not-a-real-password",
                                "remotePath", "/report", "fileHeaderMode", "registered-chinese",
                                "sourceColumns", List.of(Map.of(
                                        "column_name", "vehicle_id", "column_comment", "车辆编号")))),
                        new Pipeline.Node("mapping", "transform.field-mapping", "字段映射", "transform", 320, 0,
                                Map.of("mappings", """
                                        {"version":"1.0","passthroughUnmapped":false,"mappings":[
                                          {"from":"/vehicle_id","to":"/vehicle_id"}
                                        ]}
                                        """))
                ), List.of(new Pipeline.Edge("edge", "source", "mapping", null))),
                null, null, null, null, null, null, null);

        new DslCompiler(nifi, registry, new FieldMappingService(), mock(HiveModule.class)).compile(pipeline);

        verify(nifi).createProcessor(eq("pg"), eq("org.apache.nifi.processors.standard.QueryRecord"),
                eq("字段映射/map"), anyDouble(), anyDouble(), argThat(properties ->
                        String.valueOf(properties.get("success")).contains("车辆编号")
                                && !String.valueOf(properties.get("success")).contains("\"vehicle_id\" AS \"vehicle_id\"")),
                nullable(String.class), nullable(String.class));
    }

    private ComponentManifest manifest(String resource) throws Exception {
        try (var stream = getClass().getResourceAsStream(resource)) {
            return new ObjectMapper().readValue(stream, ComponentManifest.class);
        }
    }

    private NifiEntity entity(String id) {
        return new NifiEntity(null, Map.of("id", id), null, null);
    }
}
