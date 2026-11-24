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
    void kafkaIsolationLevelUsesTheDeclaredDefaultAndExplicitLegacySetting() throws Exception {
        for (String level : List.of("default", "read_committed", "read_uncommitted", "legacy")) {
            NifiClient nifi = mock(NifiClient.class);
            ManifestRegistry registry = mock(ManifestRegistry.class);
            ComponentManifest manifest;
            try (var stream = getClass().getResourceAsStream("/manifests/sources/kafka.json")) {
                manifest = new ObjectMapper().readValue(stream, ComponentManifest.class);
            }
            when(registry.get("source.kafka")).thenReturn(manifest);
            when(nifi.getRootProcessGroupId()).thenReturn("root");
            HiveServiceFixtures.stubGlobals(nifi);
            when(nifi.listProcessGroups("root")).thenReturn(List.of());
            when(nifi.createProcessGroup(anyString(), anyString(), anyDouble(), anyDouble())).thenReturn(entity("pg"));
            when(nifi.createControllerService(anyString(), anyString(), anyString(), anyMap())).thenReturn(entity("service"));
            when(nifi.createProcessor(anyString(), anyString(), anyString(), anyDouble(), anyDouble(),
                    anyMap(), nullable(String.class), nullable(String.class))).thenReturn(entity("processor"));
            when(nifi.createConnection(anyString(), anyString(), anyString(), anyString(), anyString(), any())).thenReturn(entity("connection"));
            Map<String, Object> config = new java.util.LinkedHashMap<>(Map.of(
                    "bootstrapServers", "validation.invalid:9092", "topic", "business", "groupId", "validation"));
            String serviceType = "org.apache.nifi.kafka.service.Kafka3ConnectionService";
            if (level.equals("legacy")) {
                serviceType = "com.linewell.dataelement.nifi.HaitongLegacyKafkaConnectionService";
                config.put("kafkaServiceType", serviceType);
                config.put("isolationLevel", "read_uncommitted");
            } else if (!level.equals("default")) {
                config.put("isolationLevel", level);
            }
            Pipeline pipeline = new Pipeline("task", "Kafka", null, null, null,
                    new Pipeline.Dsl(1, List.of(new Pipeline.Node("source", "source.kafka", "Kafka",
                            "source", 0, 0, config)), List.of()), null, null, null, null, null, null, null);
            new DslCompiler(nifi, registry, mock(FieldMappingService.class), mock(HiveModule.class)).compile(pipeline);
            String expected = switch (level) {
                case "default" -> "read_committed";
                case "legacy" -> "read_uncommitted";
                default -> level;
            };
            verify(nifi).createControllerService(eq("pg"), eq(serviceType),
                    anyString(), argThat(properties -> expected.equals(properties.get("isolation.level"))));
        }
    }


    @Test void registeredSourceShapesUseOneAuthorizedBatchAndRejectMissingOrStaleSnapshots() throws Exception {
        for(int count:List.of(20,100)) {
            var json=new ObjectMapper();var nifi=mock(NifiClient.class);var schemas=mock(com.linewell.dataelement.integration.nifi.canvas.mapping.RegisteredMappingSchemaService.class);
            var compiler=new DslCompiler(nifi,mock(ManifestRegistry.class),mock(FieldMappingService.class),mock(HiveModule.class));compiler.setRegisteredSchemas(schemas);
            var shape=new com.linewell.dataelement.mapping.core.StructuredJsonRecords.Shape();shape.accept(com.linewell.dataelement.mapping.core.StructuredJsonRecords.normalize(json.readTree("{\"@timestamp\":\"time\"}")));
            var nodes=new java.util.ArrayList<Pipeline.Node>();var resolved=new java.util.LinkedHashMap<String,com.linewell.dataelement.integration.nifi.canvas.mapping.RegisteredMappingSchemaService.Resolved>();
            var fields=json.createArrayNode();fields.addObject().put("name","timestamp");
            for(int i=0;i<count;i++) {
                String id="source-"+i;nodes.add(new Pipeline.Node(id,"source.kafka","Kafka","source",0,0,Map.of("registeredDatasourceId","db","sourceTableId","table-"+i,"sourceRecordShape",Map.of("untrusted",true))));
                resolved.put(id,new com.linewell.dataelement.integration.nifi.canvas.mapping.RegisteredMappingSchemaService.Resolved("db","table-"+i,"","topic",fields,Map.of("db",Map.of("dbType","kafka")),false,false,shape.json()));
            }
            when(schemas.resolve(anyMap())).thenReturn(resolved);
            var pipeline=new Pipeline("task","批量来源",null,null,null,new Pipeline.Dsl(1,nodes,List.of()),null,null,null,null,null,null,null);
            compiler.preflightSources(pipeline);
            verify(schemas,org.mockito.Mockito.times(1)).resolve(argThat(refs->refs.size()==count&&refs.values().stream().allMatch(ref->ref.path("datasourceId").asText().equals("db"))));
            verify(nifi).requireMappingExtension();
            resolved.put("source-0",new com.linewell.dataelement.integration.nifi.canvas.mapping.RegisteredMappingSchemaService.Resolved("db","table-0","","topic",fields,Map.of("db",Map.of("dbType","kafka"))));
            var error=org.junit.jupiter.api.Assertions.assertThrows(com.linewell.dataelement.mapping.core.MappingException.class,()->compiler.preflightSources(pipeline));org.junit.jupiter.api.Assertions.assertEquals("SOURCE_SHAPE_REQUIRED",error.code());
            when(schemas.resolve(anyMap())).thenThrow(new com.linewell.dataelement.mapping.core.MappingException("RESOURCE_FORBIDDEN","无权访问某一来源"));
            org.junit.jupiter.api.Assertions.assertEquals("RESOURCE_FORBIDDEN",org.junit.jupiter.api.Assertions.assertThrows(com.linewell.dataelement.mapping.core.MappingException.class,()->compiler.preflightSources(pipeline)).code());
            org.mockito.Mockito.verify(nifi,org.mockito.Mockito.never()).createProcessGroup(anyString(),anyString(),anyDouble(),anyDouble());
        }
    }

    @Test
    void minioSelectsExactRegisteredObjectBeforeFetchAndExtractsTheSelectedDataset() throws Exception {
        for (String format : List.of("xlsx", "json")) {
            var nifi = mock(NifiClient.class);
            var registry = mock(ManifestRegistry.class);
            when(registry.get("source.minio")).thenReturn(manifest("/manifests/sources/minio.json"));
            when(nifi.getRootProcessGroupId()).thenReturn("root");
            HiveServiceFixtures.stubGlobals(nifi);
            when(nifi.listProcessGroups("root")).thenReturn(List.of());
            when(nifi.createProcessGroup(anyString(), anyString(), anyDouble(), anyDouble())).thenReturn(entity("pg"));
            when(nifi.createControllerService(anyString(), anyString(), anyString(), anyMap())).thenReturn(entity("cs"));
            var ids = new java.util.LinkedHashMap<String, String>();
            var properties = new java.util.LinkedHashMap<String, Map<String, String>>();
            when(nifi.createProcessor(anyString(), anyString(), anyString(), anyDouble(), anyDouble(), anyMap(),
                    nullable(String.class), nullable(String.class))).thenAnswer(call -> {
                String name = call.getArgument(2); String id = "processor-" + ids.size();
                ids.put(name, id); properties.put(name, call.getArgument(5)); return entity(id);
            });
            String key = " managed/Report#'${literal}." + format;
            var config = Map.<String, Object>of("minioEndpoint", "http://validation.invalid:9000",
                    "minioBucket", "validation", "minioAccessKey", "validation", "minioSecretKey", "validation-only",
                    "minioObjectKey", key, "fileFormat", format, "excelSheetName", "月#统计",
                    "jsonRecordPath", format.equals("json") ? "$.data.people" : "$");
            var pipeline = new Pipeline("task", "已登记MinIO数据集", null, null, null,
                    new Pipeline.Dsl(1, List.of(new Pipeline.Node("source", "source.minio", "MinIO", "source", 0, 0, config)), List.of()),
                    null, null, null, null, null, null, null);
            new DslCompiler(nifi, registry, mock(FieldMappingService.class), mock(HiveModule.class)).compile(pipeline);
            String encoded = java.util.Base64.getEncoder().encodeToString(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            org.assertj.core.api.Assertions.assertThat(properties.get("MinIO/已登记对象"))
                    .containsEntry("selected", "${filename:base64Encode():equals('" + encoded + "')}");
            verify(nifi).createConnection("pg", ids.get("MinIO/列出对象"), "PROCESSOR", ids.get("MinIO/已登记对象"), "PROCESSOR", List.of("success"));
            verify(nifi).createConnection("pg", ids.get("MinIO/已登记对象"), "PROCESSOR", ids.get("MinIO/读取对象"), "PROCESSOR", List.of("selected"));
            org.assertj.core.api.Assertions.assertThat(properties.get("MinIO/读取对象")).containsEntry("Object Key", "${filename}");
            verify(nifi).createControllerService(eq("pg"), anyString(), anyString(), argThat(p ->
                    "XLSX".equals(p.get("Input File Type")) && "月#统计".equals(p.get("Required Sheets"))));
            if (format.equals("json")) {
                org.assertj.core.api.Assertions.assertThat(properties.get("MinIO/JSON 数据集"))
                        .containsEntry("dataset", "$.data.people").containsEntry("Destination", "flowfile-content");
                verify(nifi).createConnection("pg", ids.get("MinIO/文件类型分流"), "PROCESSOR", ids.get("MinIO/JSON 数据集"), "PROCESSOR", List.of("json"));
                verify(nifi).createConnection("pg", ids.get("MinIO/JSON 数据集"), "PROCESSOR", ids.get("MinIO/规范化记录"), "PROCESSOR", List.of("matched"));
            } else {
                org.assertj.core.api.Assertions.assertThat(properties).doesNotContainKey("MinIO/JSON 数据集");
            }
        }
    }

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
        HiveServiceFixtures.stubGlobals(nifi);
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
        HiveServiceFixtures.stubGlobals(nifi);
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
        verifyFileHeaderMapping(true);
    }

    @Test
    void registeredCsvWithChineseCommentsKeepsItsPhysicalHeaderInMappingQuery() throws Exception {
        verifyFileHeaderMapping(false);
    }

    private void verifyFileHeaderMapping(boolean reportTemplate) throws Exception {
        NifiClient nifi = mock(NifiClient.class);
        ManifestRegistry registry = mock(ManifestRegistry.class);
        ComponentManifest ftp = manifest("/manifests/sources/ftp.json");
        ComponentManifest mapping = manifest("/manifests/transforms/field-mapping.json");
        when(registry.get("source.ftp")).thenReturn(ftp);
        when(registry.get("transform.field-mapping")).thenReturn(mapping);
        when(nifi.getRootProcessGroupId()).thenReturn("root");
        HiveServiceFixtures.stubGlobals(nifi);
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
                                "remotePath", "/report", "fileHeaderMode", reportTemplate ? "registered-chinese" : "",
                                "fileFormat", "csv",
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
                        reportTemplate
                                ? String.valueOf(properties.get("success")).contains("车辆编号")
                                    && !String.valueOf(properties.get("success")).contains("\"vehicle_id\" AS \"vehicle_id\"")
                                : String.valueOf(properties.get("success")).contains("\"vehicle_id\" AS \"vehicle_id\"")
                                    && !String.valueOf(properties.get("success")).contains("车辆编号")),
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
