package com.linewell.haitong.standalone;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.h2.tools.RunScript;

final class FlowStore {
    record Flow(String id, String name, JsonNode dsl, long designVersion, String groupId,
                Long deployedVersion, JsonNode nativeMapping, String retiredGroupId, long updatedAt) {}
    record Summary(String id, String name, long updatedAt, String nifiProcessGroupId) {}
    record Page(List<Summary> list, long total) {}

    private final String url;
    private final String password;

    FlowStore(Path directory, String key) throws Exception {
        if (key.length() < 16 || key.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("HAITONG_STORE_KEY 至少 16 位，且不含空白字符");
        }
        Files.createDirectories(directory);
        url = "jdbc:h2:file:" + directory.resolve("haitong-flow").toAbsolutePath() + ";CIPHER=AES;DB_CLOSE_ON_EXIT=FALSE";
        password = key + " " + key;
        try (var connection = connect();
             var schema = new InputStreamReader(FlowStore.class.getResourceAsStream("/db/init.sql"), StandardCharsets.UTF_8)) {
            RunScript.execute(connection, schema);
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, "haitong", password);
    }

    Page page(int page, int size, String search) throws SQLException {
        var items = new ArrayList<Summary>();
        try (var connection = connect();
             var list = connection.prepareStatement("""
                 SELECT id,name,updated_at,group_id FROM haitong_flow
                 WHERE LOCATE(LOWER(?),LOWER(name))>0
                 ORDER BY updated_at DESC,id DESC LIMIT ? OFFSET ?
                 """);
             var count = connection.prepareStatement("SELECT COUNT(*) FROM haitong_flow WHERE LOCATE(LOWER(?),LOWER(name))>0")) {
            list.setString(1, search);
            list.setInt(2, size);
            list.setLong(3, (long) (page - 1) * size);
            try (var rows = list.executeQuery()) {
                while (rows.next()) items.add(new Summary(rows.getString(1), rows.getString(2), rows.getLong(3), rows.getString(4)));
            }
            count.setString(1, search);
            try (var result = count.executeQuery()) {
                result.next();
                return new Page(items, result.getLong(1));
            }
        }
    }

    Flow get(String id) throws Exception {
        try (var connection = connect(); var query = connection.prepareStatement("SELECT * FROM haitong_flow WHERE id=?")) {
            query.setString(1, id);
            try (var rows = query.executeQuery()) {
                if (!rows.next()) throw new LiteApplication.Failure(404, "流程不存在");
                return read(rows);
            }
        }
    }

    Flow save(String id, String name, JsonNode dsl, long expectedVersion) throws Exception {
        long time = System.currentTimeMillis();
        if (id == null) {
            id = UUID.randomUUID().toString();
            try (var connection = connect(); var insert = connection.prepareStatement(
                    "INSERT INTO haitong_flow(id,name,dsl,design_version,updated_at) VALUES (?,?,?,1,?)")) {
                insert.setString(1, id);
                insert.setString(2, name);
                insert.setString(3, dsl.toString());
                insert.setLong(4, time);
                insert.executeUpdate();
            }
        } else {
            var current = get(id);
            if (current.designVersion == expectedVersion && current.name.equals(name) && current.dsl.equals(dsl)) return current;
            try (var connection = connect(); var update = connection.prepareStatement("""
                    UPDATE haitong_flow SET name=?,dsl=?,design_version=design_version+1,updated_at=?
                    WHERE id=? AND design_version=?
                    """)) {
                update.setString(1, name);
                update.setString(2, dsl.toString());
                update.setLong(3, time);
                update.setString(4, id);
                update.setLong(5, expectedVersion);
                if (update.executeUpdate() != 1) throw new LiteApplication.Failure(409, "画布已变更，请重新打开后保存");
            }
        }
        return get(id);
    }

    void setDeployment(Flow flow, String group, JsonNode mapping) throws SQLException {
        try (var connection = connect(); var update = connection.prepareStatement("""
                UPDATE haitong_flow SET group_id=?,deployed_version=?,native_mapping=?,retired_group_id=?
                WHERE id=? AND design_version=?
                """)) {
            update.setString(1, group);
            update.setLong(2, flow.designVersion);
            update.setString(3, mapping.toString());
            update.setString(4, flow.groupId);
            update.setString(5, flow.id);
            update.setLong(6, flow.designVersion);
            if (update.executeUpdate() != 1) throw new LiteApplication.Failure(409, "画布在部署时发生变化，请重新部署");
        }
    }

    void clearRetired(String id) throws SQLException {
        try (var connection = connect(); var update = connection.prepareStatement(
                "UPDATE haitong_flow SET retired_group_id=NULL WHERE id=?")) {
            update.setString(1, id);
            update.executeUpdate();
        }
    }

    void delete(String id) throws SQLException {
        try (var connection = connect(); var delete = connection.prepareStatement("DELETE FROM haitong_flow WHERE id=?")) {
            delete.setString(1, id);
            delete.executeUpdate();
        }
    }

    private static Flow read(ResultSet rows) throws Exception {
        String mapping = rows.getString("native_mapping");
        return new Flow(rows.getString("id"), rows.getString("name"), LiteApplication.JSON.readTree(rows.getString("dsl")),
                rows.getLong("design_version"), rows.getString("group_id"), rows.getObject("deployed_version", Long.class),
                mapping == null ? null : LiteApplication.JSON.readTree(mapping), rows.getString("retired_group_id"), rows.getLong("updated_at"));
    }
}
