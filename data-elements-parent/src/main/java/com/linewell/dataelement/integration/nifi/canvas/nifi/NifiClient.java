package com.linewell.dataelement.integration.nifi.canvas.nifi;

import com.linewell.dataelement.integration.nifi.canvas.config.NifiNodeRuntimeResolver;
import com.linewell.dataelement.integration.nifi.canvas.config.NifiNodeRuntimeResolver.RuntimeNode;
import com.linewell.dataelement.model.nifi.NifiEntity;
import com.linewell.dataelement.model.nifi.RevisionDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thin wrapper over NiFi 2.x /nifi-api.
 * Responsibilities:
 *  - Authentication (single-user token via /access/token), cached & auto-refreshed.
 *  - Revision-aware PUT/DELETE (re-fetch on 409 once).
 *  - Convenience methods for Process Groups / Processors / Controller Services / Connections.
 */
@Component
public class NifiClient {

    private static final Logger log = LoggerFactory.getLogger(NifiClient.class);
    private static final String API_PREFIX = "/nifi-api";
    private static final String EXECUTE_SQL_RECORD_TYPE = "org.apache.nifi.processors.standard.ExecuteSQLRecord";
    private static final String USE_AVRO_LOGICAL_TYPES = "Use Avro Logical Types";
    private static final Duration TOKEN_TTL = Duration.ofMinutes(10);
    private static final MediaType APPLICATION_JSON_UTF8 =
            new MediaType("application", "json", StandardCharsets.UTF_8);

    private final NifiNodeRuntimeResolver runtimeResolver;
    private final ObjectMapper mapper;
    private final String clientId = UUID.randomUUID().toString();
    private final Map<String, TokenHolder> tokens = new ConcurrentHashMap<>();
    private final Map<String, ProvenanceProcessorField> provenanceProcessorFields = new ConcurrentHashMap<>();

    public NifiClient(NifiNodeRuntimeResolver runtimeResolver, ObjectMapper mapper) {
        this.runtimeResolver = runtimeResolver;
        this.mapper = mapper;
    }

    public String clientId() { return clientId; }

    // ---------------------------------------------------------------------
    // Auth
    // ---------------------------------------------------------------------

    private String authHeader() {
        return authHeader(runtimeResolver.resolve());
    }

    private String authHeader(RuntimeNode node) {
        TokenHolder cached = tokens.get(node.tokenCacheKey());
        if (cached != null && cached.expiresAt.isAfter(Instant.now())) {
            return "Bearer " + cached.token;
        }
        return "Bearer " + acquireToken(node);
    }

    private synchronized String acquireToken(RuntimeNode node) {
        TokenHolder cached = tokens.get(node.tokenCacheKey());
        if (cached != null && cached.expiresAt.isAfter(Instant.now())) {
            return cached.token;
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("username", node.username());
        form.add("password", node.password());
        try {
            String t = runtimeResolver.restClient(node).post()
                    .uri(API_PREFIX + "/access/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            if (t == null || t.isBlank()) {
                throw new NifiException("Empty token from NiFi", 500, null);
            }
            tokens.put(node.tokenCacheKey(), new TokenHolder(t.trim(), Instant.now().plus(TOKEN_TTL)));
            log.info("Acquired NiFi access token for node {} (cached for {}m)", node.code(), TOKEN_TTL.toMinutes());
            return t.trim();
        } catch (HttpStatusCodeException e) {
            throw wrap("Acquire NiFi token", e);
        } catch (ResourceAccessException e) {
            throw connect("Acquire NiFi token", e);
        }
    }

    private RestClient http() {
        return runtimeResolver.restClient(runtimeResolver.resolve());
    }

    private RestClient http(RuntimeNode node) {
        return runtimeResolver.restClient(node);
    }

    /**
     * Verifies a saved NiFi node through NiFi's authenticated root-flow API.
     * Credentials stay in the tenant node record and never pass through the browser.
     */
    public void testConnection(String nodeId) {
        RuntimeNode node = runtimeResolver.resolveById(nodeId);
        get(node, "/flow/process-groups/root", JsonNode.class);
    }

    /** Reads NiFi system diagnostics for one tenant-owned saved node. */
    public JsonNode systemDiagnostics(String nodeId) {
        RuntimeNode node = runtimeResolver.resolveById(nodeId);
        return get(node, "/system-diagnostics", JsonNode.class);
    }

    // ---------------------------------------------------------------------
    // Low-level HTTP
    // ---------------------------------------------------------------------

    /**
     * Relays a native NiFi UI/API request while keeping NiFi credentials on the server.
     */
    public ResponseEntity<byte[]> proxy(HttpMethod method, String path, HttpHeaders requestHeaders, byte[] body) {
        try {
            RestClient.RequestBodySpec spec = http().method(method).uri(path);
            requestHeaders.forEach((name, values) -> {
                if (isForwardableRequestHeader(name)) {
                    values.forEach(value -> spec.header(name, value));
                }
            });
            String auth = authHeader();
            if (auth != null) spec.header(HttpHeaders.AUTHORIZATION, auth);

            RestClient.RequestHeadersSpec<?> request = body == null || body.length == 0
                    ? spec
                    : spec.body(body);
            return request.exchange((outgoing, incoming) -> {
                HttpHeaders headers = new HttpHeaders();
                incoming.getHeaders().forEach((name, values) -> {
                    if (isForwardableResponseHeader(name)) {
                        headers.put(name, values);
                    }
                });
                return new ResponseEntity<>(incoming.getBody().readAllBytes(), headers, incoming.getStatusCode());
            }, false);
        } catch (ResourceAccessException e) {
            throw connect(method + " " + path, e);
        }
    }

    private static boolean isForwardableRequestHeader(String name) {
        return !name.equalsIgnoreCase(HttpHeaders.AUTHORIZATION)
                && !name.equalsIgnoreCase(HttpHeaders.HOST)
                && !name.equalsIgnoreCase(HttpHeaders.CONNECTION)
                && !name.equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH)
                && !name.equalsIgnoreCase(HttpHeaders.ACCEPT_ENCODING)
                && !name.equalsIgnoreCase(HttpHeaders.ORIGIN)
                && !name.equalsIgnoreCase(HttpHeaders.REFERER);
    }

    private static boolean isForwardableResponseHeader(String name) {
        return !name.equalsIgnoreCase(HttpHeaders.CONNECTION)
                && !name.equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH)
                && !name.equalsIgnoreCase(HttpHeaders.TRANSFER_ENCODING);
    }

    public <T> T get(String path, Class<T> type) {
        return get(runtimeResolver.resolve(), path, type);
    }

    private <T> T get(RuntimeNode node, String path, Class<T> type) {
        try {
            var spec = http(node).get().uri(API_PREFIX + path).accept(MediaType.APPLICATION_JSON);
            String auth = authHeader(node);
            if (auth != null) spec.header(HttpHeaders.AUTHORIZATION, auth);
            return spec.retrieve().body(type);
        } catch (HttpStatusCodeException e) {
            throw wrap("GET " + path, e);
        } catch (ResourceAccessException e) {
            throw connect("GET " + path, e);
        }
    }

    public <T> T post(String path, Object body, Class<T> type) {
        try {
            var spec = http().post().uri(API_PREFIX + path)
                    .contentType(APPLICATION_JSON_UTF8)
                    .accept(MediaType.APPLICATION_JSON);
            String auth = authHeader();
            if (auth != null) spec.header(HttpHeaders.AUTHORIZATION, auth);
            return spec.body(jsonBody(body)).retrieve().body(type);
        } catch (HttpStatusCodeException e) {
            throw wrap("POST " + path, e);
        } catch (ResourceAccessException e) {
            throw connect("POST " + path, e);
        }
    }

    public <T> T put(String path, Object body, Class<T> type) {
        try {
            var spec = http().put().uri(API_PREFIX + path)
                    .contentType(APPLICATION_JSON_UTF8)
                    .accept(MediaType.APPLICATION_JSON);
            String auth = authHeader();
            if (auth != null) spec.header(HttpHeaders.AUTHORIZATION, auth);
            return spec.body(jsonBody(body)).retrieve().body(type);
        } catch (HttpStatusCodeException e) {
            throw wrap("PUT " + path, e);
        } catch (ResourceAccessException e) {
            throw connect("PUT " + path, e);
        }
    }

    public void delete(String path) {
        try {
            var spec = http().delete().uri(API_PREFIX + path);
            String auth = authHeader();
            if (auth != null) spec.header(HttpHeaders.AUTHORIZATION, auth);
            spec.retrieve().toBodilessEntity();
        } catch (HttpStatusCodeException e) {
            throw wrap("DELETE " + path, e);
        } catch (ResourceAccessException e) {
            throw connect("DELETE " + path, e);
        }
    }

    // ---------------------------------------------------------------------
    // Revision-aware PUT (auto-retry on 409)
    // ---------------------------------------------------------------------

    /**
     * Fetch the entity at {@code entityPath}, merge its current revision into {@code body},
     * then PUT to {@code putPath}. On 409, refetch revision and retry once.
     */
    public NifiEntity putWithRevision(String entityPath, String putPath, ObjectNode componentBody) {
        for (int attempt = 0; attempt < 2; attempt++) {
            NifiEntity current = get(entityPath, NifiEntity.class);
            RevisionDto rev = current.revision() == null
                    ? RevisionDto.initial(clientId)
                    : new RevisionDto(current.revision().version(), clientId, null);
            ObjectNode envelope = mapper.createObjectNode();
            envelope.set("revision", mapper.valueToTree(rev));
            envelope.set("component", componentBody);
            envelope.put("disconnectedNodeAcknowledged", false);
            try {
                return put(putPath, envelope, NifiEntity.class);
            } catch (NifiException e) {
                if (e.status() == 409 && attempt == 0) {
                    log.warn("PUT {} got 409, refetching revision and retrying once", putPath);
                    continue;
                }
                throw e;
            }
        }
        throw new NifiException("PUT " + putPath + " failed after revision retry", 409, null);
    }

    // ---------------------------------------------------------------------
    // Convenience operations
    // ---------------------------------------------------------------------

    public String getRootProcessGroupId() {
        String configured = runtimeResolver.resolve().rootProcessGroupId();
        if (configured != null && !configured.isBlank() && !"root".equalsIgnoreCase(configured.trim())) {
            return configured.trim();
        }
        JsonNode node = get("/flow/process-groups/root", JsonNode.class);
        // structure: { processGroupFlow: { id: "..." } }
        JsonNode id = node.path("processGroupFlow").path("id");
        if (id.isMissingNode()) {
            throw new NifiException("Cannot find root process group id in response", 500, node.toString());
        }
        return id.asText();
    }

    public NifiEntity createProcessGroup(String parentId, String name, double x, double y) {
        return createProcessGroup(parentId, name, x, y, null);
    }

    public NifiEntity createProcessGroup(String parentId, String name, double x, double y, String comments) {
        ObjectNode component = mapper.createObjectNode();
        component.put("name", name);
        if (comments != null) component.put("comments", comments);
        ObjectNode pos = mapper.createObjectNode().put("x", x).put("y", y);
        component.set("position", pos);
        ObjectNode envelope = mapper.createObjectNode();
        envelope.set("revision", mapper.valueToTree(RevisionDto.initial(clientId)));
        envelope.set("component", component);
        return post("/process-groups/" + parentId + "/process-groups", envelope, NifiEntity.class);
    }

    public JsonNode getProcessGroup(String id) {
        return get("/process-groups/" + id, JsonNode.class);
    }

    public java.util.List<JsonNode> listProcessGroups(String parentId) {
        JsonNode root = get("/process-groups/" + parentId + "/process-groups", JsonNode.class);
        java.util.List<JsonNode> groups = new java.util.ArrayList<>();
        root.path("processGroups").forEach(groups::add);
        return groups;
    }

    /** Change display metadata without moving a manually arranged group. */
    public void updateProcessGroupMetadata(String id, String name, String comments) {
        JsonNode current = getProcessGroup(id);
        ObjectNode body = mapper.createObjectNode();
        body.set("revision", current.path("revision"));
        body.set("component", mapper.createObjectNode().put("id", id)
                .put("name", name).put("comments", comments));
        put("/process-groups/" + id, body, JsonNode.class);
    }

    public NifiEntity createProcessor(String pgId, String type, String name, double x, double y, Map<String, String> properties, String schedulingPeriod, String schedulingStrategy) {
        ObjectNode component = mapper.createObjectNode();
        component.put("type", type);
        component.put("name", name);
        ObjectNode pos = mapper.createObjectNode().put("x", x).put("y", y);
        component.set("position", pos);
        ObjectNode config = mapper.createObjectNode();
        if (schedulingPeriod != null) config.put("schedulingPeriod", schedulingPeriod);
        if (schedulingStrategy != null) config.put("schedulingStrategy", schedulingStrategy);
        ObjectNode props = mapper.createObjectNode();
        if (properties != null) {
            properties.forEach(props::put);
        }
        // Every generated ExecuteSQLRecord must preserve JDBC date/time and decimal
        // types as logical record types instead of relying on NiFi's false default.
        if (EXECUTE_SQL_RECORD_TYPE.equals(type)) {
            props.put(USE_AVRO_LOGICAL_TYPES, "true");
        }
        if (!props.isEmpty()) {
            config.set("properties", props);
        }
        component.set("config", config);
        ObjectNode envelope = mapper.createObjectNode();
        envelope.set("revision", mapper.valueToTree(RevisionDto.initial(clientId)));
        envelope.set("component", component);
        return post("/process-groups/" + pgId + "/processors", envelope, NifiEntity.class);
    }

    public NifiEntity createControllerService(String pgId, String type, String name, Map<String, String> properties) {
        ObjectNode component = mapper.createObjectNode();
        component.put("type", type);
        component.put("name", name);
        if (properties != null && !properties.isEmpty()) {
            ObjectNode props = mapper.createObjectNode();
            properties.forEach(props::put);
            component.set("properties", props);
        }
        ObjectNode envelope = mapper.createObjectNode();
        envelope.set("revision", mapper.valueToTree(RevisionDto.initial(clientId)));
        envelope.set("component", component);
        return post("/process-groups/" + pgId + "/controller-services", envelope, NifiEntity.class);
    }

    public NifiEntity setProcessorRunStatus(String processorId, String state) {
        // state in {RUNNING, STOPPED, DISABLED}
        NifiEntity current = get("/processors/" + processorId, NifiEntity.class);
        // A process-group state change is asynchronous in NiFi.  The group can
        // already have started this processor between listing the flow and this
        // individual update; starting it a second time returns 409 although the
        // requested end state has been reached.
        if (hasComponentState(current, state)) return current;
        try {
            return putProcessorRunStatus(processorId, state, current);
        } catch (NifiException error) {
            if (error.status() != 409) throw error;
            // 409 can be either a revision race or a simultaneous group start.
            // Refresh once: success is idempotent when the requested state is
            // already present; otherwise retry with NiFi's latest revision.
            NifiEntity refreshed = get("/processors/" + processorId, NifiEntity.class);
            if (hasComponentState(refreshed, state)) return refreshed;
            log.info("Processor {} state update conflicted; retrying with the latest NiFi revision", processorId);
            return putProcessorRunStatus(processorId, state, refreshed);
        }
    }

    private NifiEntity putProcessorRunStatus(String processorId, String state, NifiEntity current) {
        ObjectNode envelope = mapper.createObjectNode();
        Long version = current.revision() == null ? 0L : current.revision().version();
        ObjectNode rev = mapper.createObjectNode();
        rev.put("version", version);
        rev.put("clientId", clientId);
        envelope.set("revision", rev);
        envelope.put("state", state);
        envelope.put("disconnectedNodeAcknowledged", false);
        return put("/processors/" + processorId + "/run-status", envelope, NifiEntity.class);
    }

    private static boolean hasComponentState(NifiEntity entity, String expectedState) {
        if (entity == null || entity.component() == null || expectedState == null) return false;
        Object state = entity.component().get("state");
        return state != null && expectedState.equalsIgnoreCase(String.valueOf(state));
    }

    public NifiEntity setControllerServiceRunStatus(String csId, String state) {
        // state in {ENABLED, DISABLED}
        ObjectNode envelope = mapper.createObjectNode();
        NifiEntity current = get("/controller-services/" + csId, NifiEntity.class);
        Long version = current.revision() == null ? 0L : current.revision().version();
        ObjectNode rev = mapper.createObjectNode();
        rev.put("version", version);
        rev.put("clientId", clientId);
        envelope.set("revision", rev);
        envelope.put("state", state);
        envelope.put("disconnectedNodeAcknowledged", false);
        return put("/controller-services/" + csId + "/run-status", envelope, NifiEntity.class);
    }

    /**
     * Block until the controller service reaches the given target state
     * (typically {@code ENABLED}). Returns true if the state was reached within
     * {@code timeoutMs}; false otherwise. Polls every 250 ms.
     */
    public boolean waitForControllerServiceState(String csId, String targetState, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                JsonNode entity = get("/controller-services/" + csId, JsonNode.class);
                String state = entity.path("component").path("state").asText("");
                if (targetState.equalsIgnoreCase(state)) return true;
            } catch (Exception ignored) { /* keep polling */ }
            try { Thread.sleep(250L); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); return false; }
        }
        return false;
    }

    /**
     * Block until the controller service reaches {@code ENABLED}. Throws if it
     * falls back to {@code DISABLED} (configuration invalid) or times out. The
     * thrown exception carries NiFi's reported {@code validationErrors} so the
     * caller can surface them to the UI.
     */
    public void waitForControllerServiceEnabled(String csId, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String lastState = "";
        java.util.List<String> validationErrors = java.util.List.of();
        while (System.currentTimeMillis() < deadline) {
            JsonNode entity;
            try {
                entity = get("/controller-services/" + csId, JsonNode.class);
            } catch (Exception e) {
                try { Thread.sleep(250L); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt();
                    throw new NifiException("Interrupted while waiting for CS " + csId, 500, null); }
                continue;
            }
            JsonNode comp = entity.path("component");
            lastState = comp.path("state").asText("");
            JsonNode ve = comp.path("validationErrors");
            if (ve.isArray() && !ve.isEmpty()) {
                java.util.List<String> errs = new java.util.ArrayList<>();
                ve.forEach(n -> errs.add(n.asText()));
                validationErrors = errs;
            }
            if ("ENABLED".equalsIgnoreCase(lastState)) return;
            if ("DISABLED".equalsIgnoreCase(lastState) && !validationErrors.isEmpty()) {
                throw new NifiException("Controller service " + csId
                        + " is DISABLED (invalid config): " + String.join("; ", validationErrors),
                        400, null);
            }
            try { Thread.sleep(250L); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt();
                throw new NifiException("Interrupted while waiting for CS " + csId, 500, null); }
        }
        String msg = "Controller service " + csId + " did not reach ENABLED within "
                + (timeoutMs / 1000) + "s (last state=" + lastState + ")";
        if (!validationErrors.isEmpty()) msg += "; validationErrors=" + String.join("; ", validationErrors);
        throw new NifiException(msg, 504, null);
    }

    /**
     * Mark the given relationships as auto-terminated on the processor (anything
     * not in {@code keepConnectedRelationships} that the processor supports).
     * Required because NiFi refuses to start a processor with an unconnected
     * non-terminated relationship.
     */
    public void autoTerminateUnusedRelationships(String processorId, java.util.Set<String> keepConnected) {
        JsonNode entity = get("/processors/" + processorId, JsonNode.class);
        JsonNode rels = entity.path("component").path("relationships");
        if (!rels.isArray() || rels.isEmpty()) return;
        java.util.List<String> autoTerm = new java.util.ArrayList<>();
        for (JsonNode r : rels) {
            String name = r.path("name").asText(null);
            if (name == null) continue;
            if (keepConnected != null && keepConnected.stream().anyMatch(name::equalsIgnoreCase)) continue;
            autoTerm.add(name);
        }
        if (autoTerm.isEmpty()) return;

        Long version = entity.path("revision").path("version").asLong(0L);
        ObjectNode envelope = mapper.createObjectNode();
        ObjectNode rev = mapper.createObjectNode();
        rev.put("version", version);
        rev.put("clientId", clientId);
        envelope.set("revision", rev);
        ObjectNode component = mapper.createObjectNode();
        component.put("id", processorId);
        ObjectNode config = mapper.createObjectNode();
        var arr = mapper.createArrayNode();
        autoTerm.forEach(arr::add);
        config.set("autoTerminatedRelationships", arr);
        component.set("config", config);
        envelope.set("component", component);
        envelope.put("disconnectedNodeAcknowledged", false);
        try {
            put("/processors/" + processorId, envelope, JsonNode.class);
        } catch (NifiException e) {
            log.warn("Auto-terminate relationships on {} failed: {}", processorId, e.getMessage());
        }
    }

    /**
     * Set entire process group state: state in {RUNNING, STOPPED}.
     */
    public JsonNode setProcessGroupState(String pgId, String state) {
        ObjectNode body = mapper.createObjectNode();
        body.put("id", pgId);
        body.put("state", state);
        body.put("disconnectedNodeAcknowledged", false);
        return put("/flow/process-groups/" + pgId, body, JsonNode.class);
    }

    /**
     * NiFi normally starts every processor when a process group is set to
     * RUNNING.  Some installed NiFi versions can leave individual processors
     * STOPPED after a previous validation/runtime stop, while the parent group
     * reports RUNNING.  Resume those processors explicitly so a scheduled
     * source (notably GetSFTP/GetFTP) actually polls its remote directory.
     *
     * @return number of processors explicitly resumed
     */
    public int resumeStoppedProcessors(String pgId) {
        JsonNode flow = get("/flow/process-groups/" + pgId, JsonNode.class);
        if (flow == null || flow.isNull()) return 0;
        JsonNode processors = flow.path("processGroupFlow").path("flow").path("processors");
        if (!processors.isArray()) return 0;

        int resumed = 0;
        for (JsonNode processor : processors) {
            JsonNode component = processor.path("component");
            String processorId = component.path("id").asText("");
            String state = component.path("state").asText("");
            if (processorId.isBlank() || !"STOPPED".equalsIgnoreCase(state)) continue;
            try {
                setProcessorRunStatus(processorId, "RUNNING");
            } catch (NifiException error) {
                // The immediately preceding process-group RUNNING command may
                // still be applying.  A conflict is successful only when the
                // processor reaches RUNNING shortly afterwards; otherwise it
                // remains a genuine start failure and is surfaced to the user.
                if (error.status() != 409 || !waitForProcessorState(processorId, "RUNNING", 2_000L)) {
                    throw error;
                }
                log.info("Processor {} was started by the process-group transition after a 409 conflict", processorId);
            }
            resumed++;
        }
        return resumed;
    }

    private boolean waitForProcessorState(String processorId, String targetState, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (hasComponentState(get("/processors/" + processorId, NifiEntity.class), targetState)) return true;
            } catch (NifiException ignored) {
                // Keep waiting through the short state transition window.
            }
            try { Thread.sleep(200L); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    public NifiEntity createConnection(String pgId, String sourceId, String sourceType,
                                       String destId, String destType,
                                       java.util.List<String> selectedRelationships) {
        ObjectNode component = mapper.createObjectNode();

        ObjectNode source = mapper.createObjectNode();
        source.put("id", sourceId);
        source.put("type", sourceType);   // "PROCESSOR" / "OUTPUT_PORT" / etc.
        source.put("groupId", pgId);
        component.set("source", source);

        ObjectNode dest = mapper.createObjectNode();
        dest.put("id", destId);
        dest.put("type", destType);
        dest.put("groupId", pgId);
        component.set("destination", dest);

        var relsNode = mapper.createArrayNode();
        if (selectedRelationships != null) {
            selectedRelationships.forEach(relsNode::add);
        }
        component.set("selectedRelationships", relsNode);

        ObjectNode envelope = mapper.createObjectNode();
        envelope.set("revision", mapper.valueToTree(RevisionDto.initial(clientId)));
        envelope.set("component", component);
        return post("/process-groups/" + pgId + "/connections", envelope, NifiEntity.class);
    }

    /** Finalize a stopped generated graph; failures remain queued instead of auto-terminating. */
    public void finalizeGeneratedProcessGroup(String pgId) {
        JsonNode flow = get("/flow/process-groups/" + pgId, JsonNode.class)
                .path("processGroupFlow").path("flow");
        JsonNode processors = flow.path("processors");
        if (!processors.isArray()) throw new IllegalStateException("Cannot read NiFi processors: " + pgId);
        if (processors.isEmpty()) return;
        var links = NativeCanvasLayout.links(flow.path("connections"));
        var positions = NativeCanvasLayout.positions(processors, links);
        for (JsonNode entity : processors) {
            JsonNode processor = entity.path("component");
            String id = entity.path("id").asText();
            String type = processor.path("type").asText();
            var used = new java.util.LinkedHashSet<String>();
            links.stream().filter(l -> id.equals(l.source())).forEach(l -> used.addAll(l.relationships()));
            var retained = new java.util.ArrayList<String>();
            if (!type.endsWith(".GenerateTableFetch") && !type.endsWith(".RouteOnAttribute")) {
                for (JsonNode relationship : processor.path("relationships")) {
                    String name = relationship.path("name").asText();
                    if (("failure".equals(name) || "retry".equals(name)) && !used.contains(name)) retained.add(name);
                }
            }
            if (!retained.isEmpty()) {
                var existingLoop = links.stream().filter(l -> id.equals(l.source()) && id.equals(l.target())
                        && NativeCanvasLayout.isErrorLink(l)).findFirst();
                if (existingLoop.isPresent()) {
                    var loop = existingLoop.get();
                    var relationships = new java.util.ArrayList<>(loop.relationships());
                    relationships.addAll(retained);
                    ObjectNode update = mapper.createObjectNode().put("id", loop.id());
                    update.set("selectedRelationships", mapper.valueToTree(relationships));
                    putWithRevision("/connections/" + loop.id(), "/connections/" + loop.id(), update);
                    links.set(links.indexOf(loop), new NativeCanvasLayout.Link(loop.id(), id, id, relationships));
                } else {
                    NifiEntity loop = createConnection(pgId, id, "PROCESSOR", id, "PROCESSOR", retained);
                    links.add(new NativeCanvasLayout.Link(loop.id(), id, id, retained));
                }
                used.addAll(retained);
            }
            ObjectNode update = mapper.createObjectNode().put("id", id);
            update.set("position", mapper.valueToTree(positions.get(id)));
            var autoTerminated = mapper.createArrayNode();
            for (JsonNode relationship : processor.path("relationships")) {
                String name = relationship.path("name").asText();
                if (!used.contains(name)) autoTerminated.add(name);
            }
            update.set("config", mapper.createObjectNode().set("autoTerminatedRelationships", autoTerminated));
            // Applying an empty list is essential when failure was previously auto-terminated.
            putWithRevision("/processors/" + id, "/processors/" + id, update);
        }
        int lane = 0;
        for (var link : links) {
            if (!positions.containsKey(link.source()) || !positions.containsKey(link.target())) continue;
            var route = NativeCanvasLayout.route(link, positions, lane);
            if (!link.source().equals(link.target()) && !route.bends().isEmpty()) lane++;
            ObjectNode update = mapper.createObjectNode().put("id", link.id()).put("labelIndex", route.labelIndex());
            update.set("bends", mapper.valueToTree(route.bends()));
            if (link.source().equals(link.target()) && NativeCanvasLayout.isErrorLink(link)) {
                update.put("flowFileExpiration", "0 sec");
                update.put("backPressureObjectThreshold", 10000);
                update.put("backPressureDataSizeThreshold", "1 GB");
            }
            putWithRevision("/connections/" + link.id(), "/connections/" + link.id(), update);
        }
    }

    /** A redeploy must not destroy records retained in success or failure queues. */
    public void requireEmptyQueuesForRedeploy(String pgId) {
        try {
            getProcessGroup(pgId);
        } catch (NifiException e) {
            if (e.status() == 404) return;
            throw e;
        }
        setProcessGroupState(pgId, "STOPPED");
        waitForAllProcessorsStopped(pgId, 30_000L);
        JsonNode snapshot = getProcessGroupStatus(pgId).path("processGroupStatus").path("aggregateSnapshot");
        long queued = snapshot.path("queuedCount").asLong(-1);
        if (queued != 0) {
            throw new IllegalStateException(queued < 0
                    ? "无法确认旧流程队列为空，已保留流程并中止重新部署"
                    : "旧流程还有 " + queued + " 条排队数据（含失败重试数据），已保留流程并中止重新部署，请先处理队列");
        }
    }

    public JsonNode getProcessGroupStatus(String pgId) {
        // recursive=true gives status of nested processors as well.
        return get("/flow/process-groups/" + pgId + "/status?recursive=true", JsonNode.class);
    }

    public JsonNode getProcessor(String processorId) {
        return get("/processors/" + processorId, JsonNode.class);
    }

    public void deleteProcessGroup(String pgId) {
        // Need version + clientId as query params
        NifiEntity current = get("/process-groups/" + pgId, NifiEntity.class);
        Long version = current.revision() == null ? 0L : current.revision().version();
        delete("/process-groups/" + pgId + "?version=" + version + "&clientId=" + clientId);
    }

    // ---------------------------------------------------------------------
    // Cleanup helpers (PG teardown must be ordered: stop → drain → disable CS → delete)
    // ---------------------------------------------------------------------

    /** List direct child connections of a PG. Returns array of connection JSON entities. */
    public java.util.List<JsonNode> listConnections(String pgId) {
        JsonNode root = get("/process-groups/" + pgId + "/connections", JsonNode.class);
        java.util.List<JsonNode> out = new java.util.ArrayList<>();
        JsonNode arr = root.path("connections");
        if (arr.isArray()) arr.forEach(out::add);
        return out;
    }

    /** List direct child controller services of a PG. */
    public java.util.List<JsonNode> listControllerServices(String pgId) {
        // includeAncestorGroups=false avoids picking up parent CS
        JsonNode root = get("/flow/process-groups/" + pgId + "/controller-services?includeAncestorGroups=false&includeDescendantGroups=false",
                JsonNode.class);
        java.util.List<JsonNode> out = new java.util.ArrayList<>();
        JsonNode arr = root.path("controllerServices");
        if (arr.isArray()) arr.forEach(out::add);
        return out;
    }

    /**
     * Resolves a processor's fully-qualified type from the extensions that are
     * installed on the selected NiFi node. The canvas stores a stable product
     * name (for example {@code PutHDFS}) so it does not need to know the
     * extension's Java package name.
     */
    public java.util.Optional<String> findProcessorType(String simpleTypeName) {
        if (simpleTypeName == null || simpleTypeName.isBlank()) {
            return java.util.Optional.empty();
        }
        JsonNode root = get("/flow/processor-types", JsonNode.class);
        JsonNode types = root.path("processorTypes");
        if (!types.isArray()) {
            return java.util.Optional.empty();
        }
        for (JsonNode processor : types) {
            String type = processor.path("type").asText("").trim();
            if (simpleTypeName.equals(type) || type.endsWith("." + simpleTypeName)) {
                return java.util.Optional.of(type);
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * Issue a flowfile-queue drop request and poll until {@code finished=true}.
     * NiFi rejects deletion of connections / PGs whose queues still hold data.
     */
    public void dropFlowFiles(String connectionId, long timeoutMs) {
        JsonNode created = post("/flowfile-queues/" + connectionId + "/drop-requests",
                mapper.createObjectNode(), JsonNode.class);
        String dropId = created.path("dropRequest").path("id").asText(null);
        if (dropId == null) throw new NifiException("drop-request returned no id for connection " + connectionId, 502, null);
        long deadline = System.currentTimeMillis() + timeoutMs;
        try {
            while (System.currentTimeMillis() < deadline) {
                JsonNode req = get("/flowfile-queues/" + connectionId + "/drop-requests/" + dropId, JsonNode.class);
                if (req.path("dropRequest").path("finished").asBoolean(false)) return;
                try { Thread.sleep(300L); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt();
                    throw new NifiException("Interrupted while dropping FlowFiles on " + connectionId, 500, null); }
            }
            throw new NifiException("Drop request did not finish within " + (timeoutMs/1000) + "s on " + connectionId, 504, null);
        } finally {
            try { delete("/flowfile-queues/" + connectionId + "/drop-requests/" + dropId); }
            catch (Exception ignored) { /* best-effort cleanup */ }
        }
    }

    /** Wait until every processor in the PG (recursive) reports STOPPED or DISABLED. */
    public void waitForAllProcessorsStopped(String pgId, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            JsonNode status = get("/flow/process-groups/" + pgId + "/status?recursive=true", JsonNode.class);
            int running = status.path("processGroupStatus").path("aggregateSnapshot")
                    .path("activeThreadCount").asInt(0);
            JsonNode snap = status.path("processGroupStatus").path("aggregateSnapshot");
            // Some versions also expose "processorStatusSnapshots"; rely on activeThreadCount as primary.
            if (running == 0 && snap.path("queuedCount").isMissingNode()) return;
            // Cross-check via /flow/process-groups/{id} for any RUNNING processor.
            JsonNode flow = get("/flow/process-groups/" + pgId, JsonNode.class);
            boolean anyRunning = false;
            for (JsonNode p : flow.path("processGroupFlow").path("flow").path("processors")) {
                String s = p.path("component").path("state").asText("");
                if ("RUNNING".equalsIgnoreCase(s) || "STARTING".equalsIgnoreCase(s)) { anyRunning = true; break; }
            }
            if (!anyRunning && running == 0) return;
            try { Thread.sleep(500L); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt();
                throw new NifiException("Interrupted while waiting for processors to stop", 500, null); }
        }
        throw new NifiException("Not all processors in PG " + pgId + " stopped within " + (timeoutMs/1000) + "s", 504, null);
    }

    public boolean allProcessorsStoppedByState(String pgId) {
        JsonNode flow = get("/flow/process-groups/" + pgId, JsonNode.class);
        JsonNode processors = flow.path("processGroupFlow").path("flow").path("processors");
        if (!processors.isArray()) return true;
        for (JsonNode processor : processors) {
            String state = processor.path("component").path("state").asText("");
            if ("RUNNING".equalsIgnoreCase(state) || "STARTING".equalsIgnoreCase(state)) {
                return false;
            }
        }
        return true;
    }

    public String activeThreadSummary(String pgId) {
        JsonNode status = get("/flow/process-groups/" + pgId + "/status?recursive=true", JsonNode.class);
        java.util.List<String> rows = new java.util.ArrayList<>();
        JsonNode processors = status.path("processGroupStatus").path("aggregateSnapshot").path("processorStatusSnapshots");
        if (processors.isArray()) {
            for (JsonNode item : processors) {
                JsonNode snap = item.path("processorStatusSnapshot");
                int active = snap.path("activeThreadCount").asInt(0);
                if (active > 0) {
                    rows.add(snap.path("name").asText(snap.path("id").asText()) + " activeThreads=" + active);
                }
            }
        }
        return String.join("; ", rows);
    }

    public void terminateActiveProcessorThreads(String pgId) {
        JsonNode status = get("/flow/process-groups/" + pgId + "/status?recursive=true", JsonNode.class);
        JsonNode processors = status.path("processGroupStatus").path("aggregateSnapshot").path("processorStatusSnapshots");
        if (!processors.isArray()) return;
        for (JsonNode item : processors) {
            JsonNode snap = item.path("processorStatusSnapshot");
            int active = snap.path("activeThreadCount").asInt(0);
            String processorId = snap.path("id").asText("");
            if (active <= 0 || processorId.isBlank()) {
                continue;
            }
            try {
                delete("/processors/" + processorId + "/threads");
                log.warn("Requested termination of {} active thread(s) on processor {}", active, processorId);
            } catch (Exception e) {
                log.warn("Terminate active threads failed for processor {}: {}", processorId, e.getMessage());
            }
        }
    }

    /** Wait until the controller service reaches DISABLED. */
    public void waitForControllerServiceDisabled(String csId, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String last = "";
        while (System.currentTimeMillis() < deadline) {
            try {
                JsonNode entity = get("/controller-services/" + csId, JsonNode.class);
                last = entity.path("component").path("state").asText("");
                if ("DISABLED".equalsIgnoreCase(last)) return;
            } catch (Exception ignored) { /* keep polling */ }
            try { Thread.sleep(250L); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt();
                throw new NifiException("Interrupted while waiting for CS " + csId + " to disable", 500, null); }
        }
        throw new NifiException("Controller service " + csId + " did not reach DISABLED within "
                + (timeoutMs/1000) + "s (last state=" + last + ")", 504, null);
    }

    /**
     * Full ordered teardown of a PG: stop → wait STOPPED → drain queues → disable CS
     * → wait DISABLED → delete PG. Throws on any unrecoverable failure so the caller
     * can fail-fast and avoid creating orphan PGs alongside the old one.
     */
    public void cleanupProcessGroup(String pgId) {
        // If PG no longer exists in NiFi, treat as already cleaned up.
        try {
            get("/process-groups/" + pgId, JsonNode.class);
        } catch (NifiException e) {
            if (e.status() == 404) {
                log.info("Cleanup PG {}: not found in NiFi, treating as already deleted", pgId);
                return;
            }
            throw e;
        }
        log.info("Cleanup PG {}: stopping", pgId);
        try { setProcessGroupState(pgId, "STOPPED"); }
        catch (Exception e) { log.warn("Stop PG {} failed (continuing): {}", pgId, e.getMessage()); }
        waitForAllProcessorsStopped(pgId, 30_000L);

        log.info("Cleanup PG {}: draining connection queues", pgId);
        for (JsonNode conn : listConnections(pgId)) {
            String cid = conn.path("id").asText(null);
            if (cid == null) continue;
            try { dropFlowFiles(cid, 30_000L); }
            catch (Exception e) { log.warn("Drop queue on connection {} failed (continuing): {}", cid, e.getMessage()); }
        }

        log.info("Cleanup PG {}: disabling controller services", pgId);
        java.util.List<JsonNode> services = listControllerServices(pgId);
        java.util.List<String> csIds = new java.util.ArrayList<>();
        for (JsonNode cs : services) {
            String id = cs.path("id").asText(null);
            if (id == null) continue;
            csIds.add(id);
            try { setControllerServiceRunStatus(id, "DISABLED"); }
            catch (Exception e) { log.warn("Disable CS {} failed (continuing): {}", id, e.getMessage()); }
        }
        for (String id : csIds) {
            try { waitForControllerServiceDisabled(id, 30_000L); }
            catch (Exception e) { log.warn("CS {} did not disable in time (continuing): {}", id, e.getMessage()); }
        }

        log.info("Cleanup PG {}: deleting", pgId);
        deleteProcessGroup(pgId);
        log.info("Cleanup PG {} done", pgId);
    }

    /**
     * Recursively cleanup a process group tree by root/parent pg id.
     * Deletes child PGs first, then deletes the parent PG itself.
     */
    public void cleanupProcessGroupTree(String parentPgId) {
        java.util.LinkedHashSet<String> postOrder = new java.util.LinkedHashSet<>();
        collectProcessGroupPostOrder(parentPgId, postOrder);
        for (String pgId : postOrder) {
            cleanupProcessGroup(pgId);
        }
    }

    private byte[] jsonBody(Object body) {
        try {
            return mapper.writeValueAsBytes(body);
        } catch (Exception e) {
            throw new IllegalStateException("Serialize NiFi request body failed", e);
        }
    }

    private void collectProcessGroupPostOrder(String pgId, java.util.LinkedHashSet<String> out) {
        JsonNode flow = get("/flow/process-groups/" + pgId, JsonNode.class);
        JsonNode groups = flow.path("processGroupFlow").path("flow").path("processGroups");
        if (groups.isArray()) {
            for (JsonNode g : groups) {
                String childId = g.path("id").asText(null);
                if (childId == null || childId.isBlank()) continue;
                collectProcessGroupPostOrder(childId, out);
            }
        }
        out.add(pgId);
    }

    // ---------------------------------------------------------------------
    // Bulletins (runtime errors / warnings)
    // ---------------------------------------------------------------------

    /**
     * Returns the bulletin board scoped to the given process group, only including
     * bulletins newer than {@code afterId}.
     */
    public JsonNode getBulletinBoard(String pgId, long afterId) {
        StringBuilder qs = new StringBuilder("/flow/bulletin-board?groupId=").append(pgId);
        if (afterId > 0) qs.append("&after=").append(afterId);
        return get(qs.toString(), JsonNode.class);
    }

    // ---------------------------------------------------------------------
    // Provenance / Lineage
    // ---------------------------------------------------------------------

    /**
     * Submit a provenance lineage query rooted at a given event id and return the
     * lineage entity (which may be still processing — caller polls until finished).
     * NiFi 2.x wraps the request body inside lineage.request.
     */
    public JsonNode submitLineage(String eventId, String clusterNodeId) {
        ObjectNode req = mapper.createObjectNode();
        ObjectNode l = mapper.createObjectNode();
        ObjectNode inner = mapper.createObjectNode();
        inner.put("lineageRequestType", "FLOWFILE");
        inner.put("eventId", eventId);
        if (clusterNodeId != null) inner.put("clusterNodeId", clusterNodeId);
        l.set("request", inner);
        req.set("lineage", l);
        return post("/provenance/lineage", req, JsonNode.class);
    }

    public JsonNode getLineage(String lineageId) {
        return getLineage(lineageId, null);
    }

    public JsonNode getLineage(String lineageId, String clusterNodeId) {
        return get(lineagePath(lineageId, clusterNodeId), JsonNode.class);
    }

    public void deleteLineage(String lineageId) {
        deleteLineage(lineageId, null);
    }

    public void deleteLineage(String lineageId, String clusterNodeId) {
        try { delete(lineagePath(lineageId, clusterNodeId)); }
        catch (NifiException ignored) { /* best effort cleanup */ }
    }

    private static String lineagePath(String lineageId, String clusterNodeId) {
        String path = "/provenance/lineage/" + lineageId;
        // A clustered query is owned by the event's node. NiFi requires that
        // identifier for polling and deletion, not only for the initial POST.
        return clusterNodeId == null || clusterNodeId.isBlank() ? path : path
                + "?clusterNodeId=" + java.net.URLEncoder.encode(clusterNodeId, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Submit a provenance search query (latest events for a component). */
    public JsonNode submitProvenance(String componentId, int maxResults) {
        ObjectNode req = mapper.createObjectNode();
        ObjectNode p = mapper.createObjectNode();
        // NiFi 2.x ProvenanceDTO requires the actual filter inside `request`.
        ObjectNode request = mapper.createObjectNode();
        request.put("maxResults", Math.max(1, maxResults));
        request.put("componentId", componentId);
        p.set("request", request);
        req.set("provenance", p);
        return post("/provenance", req, JsonNode.class);
    }

    /** Processor searches must use the field ID advertised by this NiFi node.
     * A top-level request.componentId is not a ProvenanceRequestDTO filter. */
    public JsonNode submitProcessorProvenance(String processorId, int maxResults) {
        if (processorId == null || processorId.isBlank()) throw new IllegalArgumentException("Processor id is required");
        String cacheKey = runtimeResolver.resolve().tokenCacheKey();
        ProvenanceProcessorField cached = provenanceProcessorFields.get(cacheKey);
        if (cached == null || cached.expiresAt().isBefore(Instant.now())) {
            JsonNode options = get("/provenance/search-options", JsonNode.class);
            String fieldId = null;
            for (JsonNode field : options.path("provenanceOptions").path("searchableFields")) {
                String name = field.path("field").asText("");
                if ("processorId".equalsIgnoreCase(name) || "componentId".equalsIgnoreCase(name)) {
                    fieldId = field.path("id").asText(null);
                    break;
                }
            }
            if (fieldId == null || fieldId.isBlank()) {
                throw new NifiException("当前 NiFi 未提供处理器事件筛选，不能执行无范围的血缘查询", 422, null);
            }
            cached = new ProvenanceProcessorField(fieldId, Instant.now().plus(Duration.ofMinutes(5)));
            provenanceProcessorFields.put(cacheKey, cached);
        }
        ObjectNode request = mapper.createObjectNode();
        request.put("maxResults", Math.max(1, Math.min(maxResults, 100)));
        request.put("incrementalResults", false);
        request.putObject("searchTerms").putObject(cached.id()).put("value", processorId).put("inverse", false);
        ObjectNode body = mapper.createObjectNode();
        body.putObject("provenance").set("request", request);
        return post("/provenance", body, JsonNode.class);
    }

    private record ProvenanceProcessorField(String id, Instant expiresAt) {}

    public JsonNode getProvenance(String provenanceId) {
        return get("/provenance/" + provenanceId, JsonNode.class);
    }

    public void deleteProvenance(String provenanceId) {
        try { delete("/provenance/" + provenanceId); }
        catch (NifiException ignored) { /* best effort cleanup */ }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private NifiException wrap(String op, HttpStatusCodeException e) {
        HttpStatusCode code = e.getStatusCode();
        String body = e.getResponseBodyAsString();
        // 404 is often a benign "resource gone" — let callers decide; log at debug.
        if (code.value() == 404) {
            log.debug("{} -> 404 : {}", op, trim(body, 500));
        } else {
            log.warn("{} -> {} : {}", op, code.value(), trim(body, 500));
        }
        return new NifiException(op + " failed: " + code, code.value(), body);
    }

    private NifiException connect(String op, ResourceAccessException e) {
        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        log.warn("{} -> NiFi unreachable: {}", op, msg);
        return new NifiException(op + " failed: NiFi unreachable - " + msg, 502, null);
    }

    private static String trim(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    private record TokenHolder(String token, Instant expiresAt) {}
}
