package com.linewell.dataelement.metautil.ddl;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.statement.SQLCreateTableStatement;
import com.linewell.dataelement.metautil.model.dto.ColumnInfo;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Portable Hive DDL, shared by materialization and the canvas DDL preview. */
public final class HiveTableDdl {
    private HiveTableDdl() { }
    private static final Pattern NUMERIC_ARGS = Pattern.compile("\\((\\d+)\\s*(?:,\\s*(-?\\d+))?\\)");

    public static String generate(String tableName, List<ColumnInfo> columns, String comment) {
        if (columns == null || columns.isEmpty()) throw new IllegalArgumentException("字段列表不能为空");
        String[] names = text(tableName).split("\\.", -1);
        if (names.length > 2) throw new IllegalArgumentException("Hive 表名最多包含数据库名和表名");
        // The selected Hive database is connection context, not part of the
        // user-facing CREATE TABLE statement. Keep a qualified input backward
        // compatible, but generate only the physical table name.
        String table = identifier(names[names.length - 1]);
        Set<String> used = new HashSet<>();
        StringBuilder sql = new StringBuilder("CREATE TABLE ").append(table).append(" (\n");
        for (int i = 0; i < columns.size(); i++) {
            ColumnInfo column = columns.get(i);
            if (column == null) throw new IllegalArgumentException("字段不能为空");
            String name = identifier(column.getColumnName());
            if (!used.add(name.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("存在重复字段：" + name);
            if (i > 0) sql.append(",\n");
            sql.append("  ").append(name).append(" ").append(type(column));
            if (!text(column.getColumnComment()).isEmpty()) sql.append(" COMMENT '").append(literal(column.getColumnComment())).append("'");
            // Hive 2.x relational constraints require special non-enforced syntax;
            // source defaults/identity/NOT NULL/PK are deliberately not copied.
        }
        sql.append("\n)");
        if (!text(comment).isEmpty()) sql.append(" COMMENT '").append(literal(comment)).append("'");
        return sql.append(";\n").toString();
    }

    static String type(ColumnInfo column) {
        String raw = text(column.getColumnType());
        if (raw.isEmpty()) raw = text(column.getDataType());
        if (raw.isEmpty()) throw new IllegalArgumentException("字段 " + column.getColumnName() + " 的类型不能为空");
        String upper = raw.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (upper.matches("^(ARRAY|MAP|STRUCT|UNIONTYPE)\\s*<.*")) {
            try {
                var statements = SQLUtils.parseStatements("CREATE TABLE t (c " + raw + ")", DbType.hive);
                if (statements.size() == 1 && statements.get(0) instanceof SQLCreateTableStatement create
                        && create.getColumnDefinitions().size() == 1) {
                    return SQLUtils.toSQLString(create.getColumnDefinitions().get(0).getDataType(), DbType.hive);
                }
            } catch (RuntimeException ignored) { /* Preserve unrepresentable values as text. */ }
            return "STRING";
        }
        String base = upper.replaceAll("\\(.*?\\)", "").replace(" UNSIGNED", "").replace(" ZEROFILL", "").trim();
        boolean oracleSource = text(column.getSourceDatabaseType()).toLowerCase(Locale.ROOT).contains("oracle");
        if (oracleSource && base.equals("DATE")) return "TIMESTAMP";
        // Oracle FLOAT is a NUMBER subtype with up to 38 decimal digits, unlike Hive's binary FLOAT.
        if (oracleSource && base.equals("FLOAT")) return "STRING";
        if (upper.contains("WITH TIME ZONE") || upper.contains("WITH LOCAL TIME ZONE")) return "STRING";
        if (Set.of("NUMBER", "NUMERIC", "DECIMAL", "DEC").contains(base)) {
            Integer precision = column.getPrecision(), scale = column.getScale();
            var args = NUMERIC_ARGS.matcher(upper);
            if (args.find()) {
                try { precision = Integer.valueOf(args.group(1)); scale = args.group(2) == null ? 0 : Integer.valueOf(args.group(2)); }
                catch (NumberFormatException invalid) { return "STRING"; }
            }
            // Never silently truncate unconstrained Oracle NUMBER or precision > 38.
            if (precision == null || precision < 1 || precision > 38 || scale == null || scale < 0 || scale > precision) return "STRING";
            return "DECIMAL(" + precision + "," + scale + ")";
        }
        if (upper.contains("UNSIGNED")) {
            switch (base) {
                case "BIGINT", "INT8" -> { return "DECIMAL(20,0)"; }
                case "INT", "INTEGER", "MEDIUMINT", "INT4" -> { return "BIGINT"; }
                case "SMALLINT", "INT2" -> { return "INT"; }
                case "TINYINT" -> { return "SMALLINT"; }
            }
        }
        return switch (base) {
            case "TINYINT" -> "TINYINT";
            case "SMALLINT", "INT2" -> "SMALLINT";
            case "INT", "INTEGER", "MEDIUMINT", "INT4", "SERIAL" -> "INT";
            case "BIGINT", "INT8", "BIGSERIAL" -> "BIGINT";
            case "FLOAT", "REAL", "BINARY_FLOAT", "FLOAT4" -> "FLOAT";
            case "DOUBLE", "DOUBLE PRECISION", "BINARY_DOUBLE", "FLOAT8" -> "DOUBLE";
            case "BOOLEAN", "BOOL" -> "BOOLEAN";
            case "BIT" -> upper.matches("BIT(?:\\(1\\))?") ? "BOOLEAN" : "BINARY";
            case "DATE" -> "DATE";
            case "DATETIME", "DATETIME2", "SMALLDATETIME", "TIMESTAMP", "TIMESTAMP WITHOUT TIME ZONE" -> "TIMESTAMP";
            case "BINARY", "VARBINARY", "LONGVARBINARY", "BLOB", "TINYBLOB", "MEDIUMBLOB", "LONGBLOB", "RAW", "LONG RAW", "BYTEA", "IMAGE" -> "BINARY";
            default -> "STRING";
        };
    }

    private static String identifier(String raw) {
        String value = text(raw);
        if (value.length() > 1 && ((value.startsWith("`") && value.endsWith("`")) || (value.startsWith("\"") && value.endsWith("\"")))) value = value.substring(1, value.length() - 1);
        if (value.isEmpty() || value.contains("`") || value.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("无效的 Hive 字段或表名");
        return "`" + value + "`";
    }
    private static String literal(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'").replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
    }
    private static String text(String value) { return value == null ? "" : value.trim(); }
}
