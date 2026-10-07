package com.linewell.dataelement.integration.nifi.canvas.errors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory + file-backed store of {@link FlowError}s, keyed by pipeline id.
 *
 * <p>Each pipeline keeps at most {@link #MAX_PER_FLOW} most recent errors.
 * On each mutation the list is also persisted beneath the backend log directory
 * to {@code errors/{flowId}.json}
 * so the UI can reload error context after a backend restart.
 */
@Service
public class ErrorService {

    private static final Logger log = LoggerFactory.getLogger(ErrorService.class);
    private static final int MAX_PER_FLOW = 100;

    private final Map<String, List<FlowError>> store = new ConcurrentHashMap<>();
    private final Path baseDir;
    private final ObjectMapper mapper;

    public ErrorService(@Value("${storage.error-root-dir:${DATA_ELEMENTS_LOG_DIR:../logs/backend}}") String rootDir, ObjectMapper mapper) {
        this.baseDir = Path.of(rootDir, "errors").toAbsolutePath().normalize();
        this.mapper = mapper;
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(baseDir);
        log.info("ErrorService persistence dir: {}", baseDir);
    }

    public List<FlowError> list(String flowId) {
        return new ArrayList<>(load(flowId));
    }

    public synchronized FlowError add(String flowId, FlowError err) {
        if (err.id() == null) {
            err = new FlowError(
                    UUID.randomUUID().toString(),
                    err.level(), err.phase(), err.nodeId(), err.nodeLabel(),
                    err.fieldKey(), err.message(), err.detail(), err.suggestion(),
                    err.occurredAt() == null ? Instant.now() : err.occurredAt());
        }
        List<FlowError> list = load(flowId);
        list.add(0, err);
        if (list.size() > MAX_PER_FLOW) {
            list.subList(MAX_PER_FLOW, list.size()).clear();
        }
        persist(flowId, list);
        return err;
    }

    public synchronized void addAll(String flowId, Collection<FlowError> errors) {
        if (errors.isEmpty()) return;
        for (FlowError e : errors) add(flowId, e);
    }

    /** Replace all errors for a single phase (used to refresh validation errors on each save/start). */
    public synchronized void replacePhase(String flowId, FlowError.ErrorPhase phase, Collection<FlowError> errors) {
        List<FlowError> list = load(flowId);
        list.removeIf(e -> e.phase() == phase);
        for (FlowError e : errors) {
            FlowError stamped = new FlowError(
                    e.id() == null ? UUID.randomUUID().toString() : e.id(),
                    e.level(), e.phase(), e.nodeId(), e.nodeLabel(),
                    e.fieldKey(), e.message(), e.detail(), e.suggestion(),
                    e.occurredAt() == null ? Instant.now() : e.occurredAt());
            list.add(0, stamped);
        }
        if (list.size() > MAX_PER_FLOW) {
            list.subList(MAX_PER_FLOW, list.size()).clear();
        }
        persist(flowId, list);
    }

    public synchronized void clear(String flowId) {
        store.remove(flowId);
        try {
            Files.deleteIfExists(pathOf(flowId));
        } catch (IOException ex) {
            log.warn("Delete errors file for {} failed: {}", flowId, ex.getMessage());
        }
    }

    private List<FlowError> load(String flowId) {
        return store.computeIfAbsent(flowId, this::loadFromDisk);
    }

    private List<FlowError> loadFromDisk(String flowId) {
        Path p = pathOf(flowId);
        if (!Files.exists(p)) return Collections.synchronizedList(new ArrayList<>());
        try {
            List<FlowError> read = mapper.readValue(p.toFile(), new TypeReference<List<FlowError>>() {});
            return Collections.synchronizedList(new ArrayList<>(read));
        } catch (IOException ex) {
            log.warn("Reload errors for {} failed: {}", flowId, ex.getMessage());
            return Collections.synchronizedList(new ArrayList<>());
        }
    }

    private void persist(String flowId, List<FlowError> list) {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(pathOf(flowId).toFile(), list);
        } catch (IOException ex) {
            log.warn("Persist errors for {} failed: {}", flowId, ex.getMessage());
        }
    }

    private Path pathOf(String flowId) {
        if (flowId == null || flowId.isBlank() || flowId.contains("/") || flowId.contains("\\") || flowId.contains("..")) {
            throw new IllegalArgumentException("Invalid flow id: " + flowId);
        }
        return baseDir.resolve(flowId + ".json");
    }
}
