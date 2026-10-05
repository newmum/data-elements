package com.linewell.dataelement.platform.magic.module;

import com.linewell.dataelement.platform.tenant.infrastructure.datasource.UseControlDataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

@Component
@UseControlDataSource
@MagicModule("magicResourceLink")
public class MagicResourceLinkModule {

    private final JdbcTemplate jdbcTemplate;

    public MagicResourceLinkModule(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Comment("Attach shared Magic API file counts to business service rows")
    public List<Map<String, Object>> attachApiFileCounts(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return rows == null ? List.of() : rows;
        }

        List<String> apiIds = rows.stream()
                .map(this::rowId)
                .filter(id -> !id.isBlank())
                .distinct()
                .toList();
        Map<String, Long> counts = countByApiIds(apiIds);
        rows.forEach(row -> row.put("apiFileCount", counts.getOrDefault(rowId(row), 0L)));
        return rows;
    }

    @Comment("Count shared Magic API files linked to one business service")
    public long countByApiId(String apiId) {
        if (apiId == null || apiId.isBlank()) {
            return 0L;
        }
        Long count = jdbcTemplate.queryForObject(
                "select count(1) from api_file_t where is_del = 0 and api_id = ?",
                Long.class,
                apiId);
        return count == null ? 0L : count;
    }

    @Comment("Link one shared Magic API file to a business service")
    public int link(String fileId, String apiId) {
        if (fileId == null || fileId.isBlank() || apiId == null || apiId.isBlank()) {
            throw new IllegalArgumentException("fileId and apiId are required");
        }
        return jdbcTemplate.update(
                "update api_file_t set api_id = ?, updated_time = current_timestamp where tid = ? and is_del = 0",
                apiId,
                fileId);
    }

    @Comment("Remove shared Magic API file links for a deleted business service")
    public int unlinkByApiId(String apiId) {
        if (apiId == null || apiId.isBlank()) {
            return 0;
        }
        return jdbcTemplate.update(
                "update api_file_t set api_id = null, updated_time = current_timestamp "
                        + "where api_id = ? and is_del = 0",
                apiId);
    }

    private Map<String, Long> countByApiIds(List<String> apiIds) {
        if (apiIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", apiIds.stream().map(ignored -> "?").toList());
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbcTemplate.query(
                "select api_id, count(1) as total from api_file_t where is_del = 0 and api_id in ("
                        + placeholders + ") group by api_id",
                preparedStatement -> {
                    for (int index = 0; index < apiIds.size(); index++) {
                        preparedStatement.setString(index + 1, apiIds.get(index));
                    }
                },
                resultSet -> {
                    while (resultSet.next()) {
                        counts.put(resultSet.getString("api_id"), resultSet.getLong("total"));
                    }
                    return counts;
                });
        return counts;
    }

    private String rowId(Map<String, Object> row) {
        for (String key : List.of("id", "ID", "tid", "TID")) {
            Object value = row.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return "";
    }
}
