package com.linewell.dataelement.integration.nifi.canvas.manifest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HiveManifestKerberosTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void hiveSourceAndSinkBindDbcpToTheServerManagedKeytabService() throws Exception {
        assertHiveManifestUsesKerberosService("/manifests/sources/hive.json");
        assertHiveManifestUsesKerberosService("/manifests/sinks/hive-sink.json");
    }

    @Test
    void hiveSinkDefaultsToLinewellWriterAndOffersTheNativeAlternatives() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/manifests/sinks/hive-sink.json")) {
            assertThat(stream).isNotNull();
            ComponentManifest manifest = mapper.readValue(stream, ComponentManifest.class);
            var field = manifest.fields().stream().filter(candidate -> "hiveWriteMode".equals(candidate.key()))
                    .findFirst().orElseThrow();
            assertThat(field.defaultValue()).isEqualTo("LINEWELL_HDFS");
            assertThat(field.options()).extracting(FieldSchema.Option::label)
                    .containsExactly("Linewell PutHwHDFS（默认）", "NiFi 原生 PutHDFS", "标准写入（HiveRecordPut）");
            assertThat(field.options()).extracting(FieldSchema.Option::value)
                    .containsExactly("LINEWELL_HDFS", "HDFS_BATCH", "HIVE_RECORD_PUT");
            assertThat(manifest.fields()).extracting(FieldSchema::key).contains("hdfsDirectory");
        }
    }

    private void assertHiveManifestUsesKerberosService(String resource) throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(resource)) {
            assertThat(stream).as("manifest resource %s", resource).isNotNull();
            ComponentManifest manifest = mapper.readValue(stream, ComponentManifest.class);

            Map<String, CompileSpec.ControllerServiceSpec> services = manifest.compile().controllerServices().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            CompileSpec.ControllerServiceSpec::localId,
                            service -> service));

            CompileSpec.ControllerServiceSpec kerberos = services.get("kerberos");
            assertThat(kerberos.type()).isEqualTo("org.apache.nifi.kerberos.KerberosKeytabUserService");
            assertThat(kerberos.properties())
                    .containsEntry("Kerberos Principal", "${config.userPrincipal}")
                    .containsEntry("Kerberos Keytab", "${config.keytabPath}");

            assertThat(services.get("dbcp").properties())
                    .containsEntry("Kerberos User Service", "${cs.kerberos}");
        }
    }
}
