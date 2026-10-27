package com.linewell.dataelement.metautil.ddl;

import com.alibaba.druid.DbType;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared SQL capabilities. Conversion is source -> column model -> family, never a source/target matrix. */
public final class DdlDialect {
    private DdlDialect() { }
    public enum Family { MYSQL, POSTGRES, ORACLE, SQLSERVER, HIVE, DB2, CLICKHOUSE, ANSI }

    public static Family family(DatabaseType type) {
        if (type == null) return Family.ANSI;
        return switch (type) {
            case MYSQL, MARIADB, OCEANBASE_MYSQL, GBASE8A, DORIS, STARROCKS -> Family.MYSQL;
            case POSTGRESQL, KINGBASE, GAUSSDB, HIGHGO, VERTICA -> Family.POSTGRES;
            case ORACLE, OCEANBASE_ORACLE, DAMENG -> Family.ORACLE;
            case SQLSERVER -> Family.SQLSERVER;
            case HIVE, MAXCOMPUTE -> Family.HIVE;
            case DB2 -> Family.DB2;
            case CLICKHOUSE -> Family.CLICKHOUSE;
            default -> Family.ANSI;
        };
    }

    public static DbType parser(DatabaseType type) {
        return switch (family(type)) {
            case ORACLE -> DbType.oracle;
            case POSTGRES -> DbType.postgresql;
            case SQLSERVER -> DbType.sqlserver;
            case HIVE -> DbType.hive;
            case DB2 -> DbType.db2;
            case CLICKHOUSE -> DbType.clickhouse;
            case MYSQL -> DbType.mysql;
            case ANSI -> type == DatabaseType.HETU ? DbType.presto : DbType.mysql;
        };
    }

    public static String scope(DataSourceConfig config) {
        if (config.getSchema() != null && !config.getSchema().isBlank()) return single(config.getSchema());
        if (config.getResolvedSchema() != null && !config.getResolvedSchema().isBlank()) return single(config.getResolvedSchema());
        return switch (family(config.getEffectiveDatabaseType())) {
            case ORACLE -> {
                // Dameng registrations historically store the business schema in database.
                String owner = config.getEffectiveDatabaseType() == DatabaseType.DAMENG && notBlank(config.getDatabase())
                        ? config.getDatabase() : config.getUsername();
                if (!notBlank(owner)) throw new IllegalArgumentException("请配置目标库 Schema");
                yield single(owner.split("[@#]", 2)[0]).toUpperCase(Locale.ROOT);
            }
            case POSTGRES -> "public";
            case SQLSERVER -> "dbo";
            case DB2 -> {
                if (!notBlank(config.getUsername())) throw new IllegalArgumentException("请配置目标库Schema");
                yield single(config.getUsername()).toUpperCase(Locale.ROOT);
            }
            default -> {
                if (!notBlank(config.getDatabase())) throw new IllegalArgumentException("请配置目标数据库名");
                yield single(config.getDatabase());
            }
        };
    }

    public static String quote(DatabaseType type, String identifier) {
        if (identifier == null || identifier.isBlank() || identifier.indexOf('\0') >= 0)
            throw new IllegalArgumentException("表名或字段名不能为空");
        return switch (family(type)) {
            case MYSQL, HIVE, CLICKHOUSE -> "`" + identifier.replace("`", "``") + "`";
            case SQLSERVER -> "[" + identifier.replace("]", "]]") + "]";
            default -> "\"" + identifier.replace("\"", "\"\"") + "\"";
        };
    }

    /** Handles dots inside quoted identifiers, Unicode, escaped quotes and SQL Server catalog.schema.table. */
    public static List<Identifier> names(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("表名不能为空");
        List<Identifier> parts = new ArrayList<>();
        int pos = 0;
        while (pos < value.length()) {
            while (pos < value.length() && Character.isWhitespace(value.charAt(pos))) pos++;
            if (pos == value.length()) throw new IllegalArgumentException("表名缺少名称部分");
            char first = value.charAt(pos);
            boolean quoted = first == '"' || first == '`' || first == '[';
            StringBuilder token = new StringBuilder();
            if (quoted) {
                char end = first == '[' ? ']' : first;
                pos++;
                boolean closed = false;
                while (pos < value.length()) {
                    char c = value.charAt(pos++);
                    if (c == end) {
                        if (pos < value.length() && value.charAt(pos) == end) { token.append(c); pos++; }
                        else { closed = true; break; }
                    } else token.append(c);
                }
                if (!closed) throw new IllegalArgumentException("表名引号未闭合");
                while (pos < value.length() && Character.isWhitespace(value.charAt(pos))) pos++;
            } else {
                while (pos < value.length() && value.charAt(pos) != '.') token.append(value.charAt(pos++));
                String raw = token.toString().trim();
                if (!raw.matches("[\\p{L}_][\\p{L}\\p{N}_$#]*")) throw new IllegalArgumentException("表名不合法");
                token.setLength(0); token.append(raw);
            }
            if (token.isEmpty() || token.toString().codePoints().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("表名不合法");
            parts.add(new Identifier(token.toString(), quoted));
            if (parts.size() > 3) throw new IllegalArgumentException("表名最多包含数据库、Schema和表名");
            if (pos < value.length()) {
                if (value.charAt(pos++) != '.' || pos == value.length()) throw new IllegalArgumentException("表名不合法");
            }
        }
        return List.copyOf(parts);
    }

    public static String single(String value) {
        List<Identifier> names = names(value);
        if (names.size() != 1) throw new IllegalArgumentException("Schema必须是单个名称");
        return names.getFirst().value();
    }

    private static boolean notBlank(String value) { return value != null && !value.isBlank(); }
    public record Identifier(String value, boolean quoted) { }
}
