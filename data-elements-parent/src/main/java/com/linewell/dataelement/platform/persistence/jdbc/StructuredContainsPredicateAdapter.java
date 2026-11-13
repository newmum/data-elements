package com.linewell.dataelement.platform.persistence.jdbc;

import com.linewell.dataelement.platform.magic.StructuredTableReadMagicModule;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Compiles a structural contains predicate with the same renderer as tableRead; no business decisions. */
@Component
public final class StructuredContainsPredicateAdapter {
    public record Fragment(String sql, Map<String, Object> parameters) { }

    /** Column types and binding namespace must come from the server, never from the client predicate. */
    public Fragment compile(DataSource source, Map<?, ?> predicate, Map<String, String> columnTypes,
            String namespace) {
        if (!predicate.keySet().equals(java.util.Set.of("operator", "column", "value"))
                || !"contains".equals(predicate.get("operator"))
                || !(predicate.get("column") instanceof String column) || !columnTypes.containsKey(column)
                || !(predicate.get("value") instanceof String value)
                || namespace == null || !namespace.matches("[A-Za-z][A-Za-z0-9_]{0,40}")) {
            throw new IllegalArgumentException("结构化文本条件无效");
        }
        var reader = new JdbcTemplate(source);
        var tableRead = new StructuredTableReadMagicModule(reader);
        return reader.execute((ConnectionCallback<Fragment>) connection -> {
            var compiled = tableRead.compileContains(connection, column, columnTypes.get(column), value);
            // Only renderer-generated placeholders are translated. Values never become SQL text.
            String[] parts = compiled.sql().split("\\?", -1);
            if (parts.length != compiled.parameters().size() + 1) {
                throw new IllegalStateException("结构化条件绑定数量不一致");
            }
            var parameters = new LinkedHashMap<String, Object>();
            var sql = new StringBuilder(parts[0]);
            for (int index = 0; index < compiled.parameters().size(); index++) {
                String name = namespace + index;
                parameters.put(name, compiled.parameters().get(index));
                sql.append(':').append(name).append(parts[index + 1]);
            }
            return new Fragment(sql.toString(), java.util.Collections.unmodifiableMap(parameters));
        });
    }
}