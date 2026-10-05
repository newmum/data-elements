package com.linewell.dataelement.platform.integration.pingao.directory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 20230224 unified-user contract. No database writes or automatic identity matching. */
@Component
public class PingaoDirectoryClient {
    private final PingaoDirectoryProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http;
    private String token;
    private Instant expiresAt = Instant.EPOCH;

    @Autowired
    public PingaoDirectoryClient(PingaoDirectoryProperties properties, ObjectMapper mapper) {
        this(properties, mapper, HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.getTimeout()).followRedirects(HttpClient.Redirect.NEVER).build());
    }

    PingaoDirectoryClient(PingaoDirectoryProperties properties, ObjectMapper mapper, HttpClient http) {
        this.properties = properties;
        this.mapper = mapper;
        this.http = http;
    }

    public PingaoDirectorySnapshot fetch(long since) {
        properties.requireConfigured();
        long startedAt = System.currentTimeMillis();
        if (since < 0 || since > startedAt) throw failure("增量时间戳无效");
        // Complete both streams before exposing any snapshot to callers.
        var organizations = pages(properties.getOrganizationsUrl(), since, "orgId", this::organization);
        var users = pages(properties.getUsersUrl(), since, "userId", this::user);
        return new PingaoDirectorySnapshot(since, startedAt, organizations, users);
    }

    private <T> List<T> pages(String endpoint, long since, String idField, Function<JsonNode, T> parse) {
        List<T> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        long total = -1;
        long pageCount = -1;
        for (int current = 1; current <= properties.getMaxPages(); current++) {
            URI uri = query(endpoint, "current=" + current + "&size=" + properties.getPageSize()
                    + "&all=false&lastUpdatedDate=" + since);
            JsonNode response = send(request(uri).header("Authorization", "Bearer " + accessToken()).GET().build());
            if (!"1".equals(response.path("code").asText())) throw failure("目录接口业务状态不是成功");
            JsonNode page = response.path("repData");
            long actualTotal = number(page, "total");
            long actualPages = number(page, "pages");
            long size = number(page, "size");
            long actualCurrent = number(page, "current");
            long calculatedPages = actualTotal == 0 ? 0 : (actualTotal - 1) / properties.getPageSize() + 1;
            if (actualTotal > properties.getMaxRecords() || actualPages > properties.getMaxPages()
                    || size != properties.getPageSize() || actualCurrent != current
                    || (actualPages != calculatedPages && !(actualTotal == 0 && actualPages == 1))) {
                throw failure("目录分页元数据不一致或超出限制");
            }
            if (total < 0) { total = actualTotal; pageCount = actualPages; }
            if (total != actualTotal || pageCount != actualPages) throw failure("目录分页期间总量变化，请重新预检");
            JsonNode rows = page.path("records");
            long expected = Math.min(properties.getPageSize(), total - (long) (current - 1) * properties.getPageSize());
            if (!rows.isArray() || rows.size() != expected) throw failure("目录分页记录不完整");
            for (JsonNode row : rows) {
                String id = required(row, idField);
                if (!ids.add(id)) throw failure("目录跨页存在重复唯一标识");
                if (!properties.getExternalTenantCode().equals(required(row, "eCode"))) {
                    throw failure("目录返回了非配置租户的数据");
                }
                result.add(parse.apply(row));
            }
            if (current >= pageCount) return List.copyOf(result);
        }
        throw failure("目录分页超过上限");
    }

    private PingaoDirectorySnapshot.Organization organization(JsonNode row) {
        return new PingaoDirectorySnapshot.Organization(required(row, "orgId"), organizationCode(row),
                optional(row, "name"), optional(row, "parentId"), optional(row, "parentOrgNum"),
                flag(row, "status"), flag(row, "isDeleted"), number(row, "lastUpdatedDate"));
    }

    private PingaoDirectorySnapshot.User user(JsonNode row) {
        List<PingaoDirectorySnapshot.Membership> memberships = new ArrayList<>();
        if (row.has("orgData")) {
            if (!row.get("orgData").isArray()) throw failure("orgData 必须是数组");
            Set<String> seen = new HashSet<>();
            for (JsonNode org : row.get("orgData")) {
                String id = required(org, "orgId");
                long type = number(org, "type");
                if (type != 1 && type != 4) throw failure("orgData 包含未确认的组织关系类型");
                if (!seen.add(id + ":" + type)) throw failure("orgData 存在重复关系");
                memberships.add(new PingaoDirectorySnapshot.Membership(id, organizationCode(org), (int) type));
            }
        } else if (!optional(row, "orgId").isBlank()) {
            // Top-level organization has no documented relationship type: 0 means unconfirmed.
            memberships.add(new PingaoDirectorySnapshot.Membership(required(row, "orgId"), optional(row, "orgNum"), 0));
        }
        return new PingaoDirectorySnapshot.User(required(row, "userId"), optional(row, "loginId"),
                optional(row, "name"), flag(row, "status"), flag(row, "isDeleted"),
                number(row, "lastUpdatedDate"), memberships);
    }

    private synchronized String accessToken() {
        if (token != null && expiresAt.isAfter(Instant.now().plusSeconds(60))) return token;
        String credentials = Base64.getEncoder().encodeToString((properties.getClientId() + ":"
                + properties.getClientSecret()).getBytes(StandardCharsets.UTF_8));
        URI uri = query(properties.getTokenUrl(), "grant_type=client_credentials");
        JsonNode payload = send(request(uri).header("Authorization", "Basic " + credentials)
                .POST(HttpRequest.BodyPublishers.noBody()).build());
        if (!properties.getExternalTenantCode().equals(required(payload, "eCode"))) {
            throw failure("应用令牌租户与目录配置不一致");
        }
        long seconds = number(payload, "expires_in");
        if (seconds <= 0 || seconds > 31536000) throw failure("应用令牌有效期无效");
        String value = required(payload, "access_token");
        expiresAt = Instant.now().plusSeconds(seconds);
        token = value;
        return value;
    }

    private HttpRequest.Builder request(URI uri) {
        return HttpRequest.newBuilder(uri).timeout(properties.getTimeout()).header("Accept", "application/json");
    }

    private JsonNode send(HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) throw failure("统一用户服务 HTTP " + response.statusCode());
            JsonNode body = mapper.readTree(response.body());
            if (body == null || !body.isObject()) throw failure("统一用户服务返回格式无效");
            return body;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure("统一用户服务调用被中断");
        } catch (java.io.IOException exception) {
            // Do not propagate payloads, credentials or URL parameters into API errors/logs.
            throw failure("统一用户服务网络或 JSON 解析失败");
        }
    }

    static URI query(String endpoint, String query) {
        URI uri;
        try { uri = URI.create(endpoint); } catch (IllegalArgumentException exception) { throw failure("目录地址格式无效"); }
        if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                || uri.getRawQuery() != null) {
            throw failure("目录地址必须为不带查询参数的 HTTP(S) 完整地址");
        }
        return URI.create(endpoint + "?" + query);
    }

    private static boolean flag(JsonNode row, String field) {
        String value = required(row, field);
        if (!"0".equals(value) && !"1".equals(value)) throw failure("目录状态字段必须为 0 或 1");
        return "1".equals(value);
    }

    private static long number(JsonNode row, String field) {
        try {
            String text = required(row, field);
            if (!text.matches("[0-9]+")) throw failure("目录数值字段无效：" + field);
            return Long.parseLong(text);
        } catch (NumberFormatException exception) { throw failure("目录数值字段越界：" + field); }
    }

    private static String required(JsonNode row, String field) {
        String value = optional(row, field);
        if (value.isBlank()) throw failure("目录响应缺少字段：" + field);
        return value;
    }

    private static String optional(JsonNode row, String field) {
        JsonNode value = row.path(field);
        if (value.isMissingNode() || value.isNull()) return "";
        if (!value.isTextual() && !value.isNumber()) throw failure("目录字段类型无效：" + field);
        return value.asText().trim();
    }

    /**
     * The contract calls orgNum the organization code, but the real directory can
     * return it as whitespace for external organizations while retaining code/icode.
     * Keep the external ID as the identity key; this value is diagnostic metadata
     * only and is never used to auto-match a local organization.
     */
    private static String organizationCode(JsonNode row) {
        for (String field : List.of("orgNum", "code", "icode")) {
            String value = optional(row, field);
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private static IllegalStateException failure(String message) { return new IllegalStateException(message); }
}
