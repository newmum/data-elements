package com.linewell.dataelement.integration.nifi.canvas.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.entity.NifiPipelineMigrationMarkT;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.entity.NifiPipelineT;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.mapper.NifiPipelineMigrationMarkTMapper;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.mapper.NifiPipelineTMapper;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.DslHasher;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nifi.pipeline-migration", name = "enabled", havingValue = "true")
public class PipelineFileToDbMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PipelineFileToDbMigrationRunner.class);
    private static final String MIGRATION_MARK_KEY = "pipeline-file-to-db-v1";

    private final ObjectMapper mapper;
    private final NifiPipelineTMapper nifiPipelineTMapper;
    private final NifiPipelineMigrationMarkTMapper migrationMarkTMapper;

    @Value("${storage.root-dir:./data}")
    private String rootDir;

    @Override
    public void run(String... args) throws Exception {
        if (migrationMarkTMapper.selectById(MIGRATION_MARK_KEY) != null) {
            log.info("Pipeline migration skipped: mark {} already exists", MIGRATION_MARK_KEY);
            return;
        }
        Path dir = Path.of(rootDir, "pipelines").toAbsolutePath().normalize();
        if (!Files.exists(dir)) {
            log.warn("Pipeline migration skipped: directory not found {}", dir);
            return;
        }
        List<Path> files = Files.list(dir).filter(p -> p.toString().endsWith(".json")).toList();
        int success = 0;
        int failed = 0;
        for (Path file : files) {
            try {
                migrateOne(file);
                success++;
            } catch (Exception e) {
                failed++;
                log.warn("Pipeline migration failed for {}: {}", file.getFileName(), e.getMessage());
            }
        }
        NifiPipelineMigrationMarkT mark = new NifiPipelineMigrationMarkT();
        mark.setMarkKey(MIGRATION_MARK_KEY);
        mark.setMarkValue("success:" + success + ",failed:" + failed);
        mark.setCreatedAt(System.currentTimeMillis());
        migrationMarkTMapper.insert(mark);
        log.info("Pipeline migration finished. total={}, success={}, failed={}", files.size(), success, failed);
    }

    private void migrateOne(Path file) throws IOException {
        JsonNode root = mapper.readTree(file.toFile());
        String id = text(root, "id");
        if (isBlank(id)) {
            String name = file.getFileName().toString();
            id = name.endsWith(".json") ? name.substring(0, name.length() - 5) : name;
        }

        NifiPipelineT entity = new NifiPipelineT();
        entity.setId(id);
        entity.setName(text(root, "name"));
        entity.setDescription(text(root, "description"));
        entity.setStatus(normalizeStatus(text(root, "status")));
        entity.setDslJson(toJson(root.get("dsl")));
        entity.setDslVersion(longValue(root.path("dsl").path("version")));
        entity.setNifiProcessGroupId(text(root, "nifiProcessGroupId"));
        entity.setLastDeployedHash(text(root, "lastDeployedHash"));
        entity.setLastDeployedAt(longValue(root.path("lastDeployedAt")));
        entity.setLastStoppedAt(longValue(root.path("lastStoppedAt")));
        entity.setNodeMappingJson(toJson(root.get("nodeMapping")));
        entity.setLastBulletinId(longValue(root.path("lastBulletinId")));
        entity.setCreatedAt(longValue(root.path("createdAt")));
        entity.setUpdatedAt(longValue(root.path("updatedAt")));
        entity.setIsDel(0);

        Pipeline pipeline = mapper.treeToValue(root, Pipeline.class);
        if (pipeline != null && pipeline.dsl() != null) {
            entity.setDslHash(DslHasher.hash(pipeline.dsl()));
        }

        NifiPipelineT existing = nifiPipelineTMapper.selectById(id);
        if (existing == null) {
            nifiPipelineTMapper.insert(entity);
        } else {
            nifiPipelineTMapper.updateById(entity);
        }
    }

    private String normalizeStatus(String status) {
        if (isBlank(status)) {
            return PipelineStatus.SAVED.name();
        }
        if ("STOP".equalsIgnoreCase(status)) {
            return PipelineStatus.STOPPED.name();
        }
        for (PipelineStatus value : PipelineStatus.values()) {
            if (value.name().equalsIgnoreCase(status)) {
                return value.name();
            }
        }
        return PipelineStatus.SAVED.name();
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private Long longValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asLong();
    }

    private String toJson(JsonNode node) throws IOException {
        if (node == null || node.isNull()) {
            return null;
        }
        return mapper.writeValueAsString(node);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
