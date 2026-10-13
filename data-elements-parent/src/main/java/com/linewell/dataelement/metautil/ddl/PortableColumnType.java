package com.linewell.dataelement.metautil.ddl;

import com.linewell.dataelement.metautil.model.dto.ColumnInfo;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Canonical type families used by both metadata DDL generation and imported CREATE TABLE definitions. */
public final class PortableColumnType {
    private PortableColumnType() { }
    private static final Pattern ARGS = Pattern.compile("\\((\\d+)(?:\\s*,\\s*(-?\\d+))?\\)");
    public enum Kind { CHAR, VARCHAR, TEXT, SMALLINT, INTEGER, BIGINT, DECIMAL, FLOAT, DOUBLE, BOOLEAN, DATE, TIME, TIMESTAMP, BINARY, BLOB, JSON, UUID, UNKNOWN }

    public static Kind kind(String raw) {
        String type = base(raw);
        if (raw != null && raw.toUpperCase(Locale.ROOT).contains("(MAX)")) {
            if (type.contains("CHAR")) return Kind.TEXT;
            if (type.contains("BINARY")) return Kind.BLOB;
        }
        if (type.endsWith("[]") || type.matches("^(ARRAY|MAP|STRUCT|UNIONTYPE).*")) return Kind.JSON;
        return switch (type) {
            case "CHAR", "NCHAR", "CHARACTER", "BPCHAR" -> Kind.CHAR;
            case "VARCHAR", "VARCHAR2", "NVARCHAR", "NVARCHAR2", "CHARACTER VARYING", "NATIONAL CHARACTER VARYING", "KEYWORD", "ROWID", "UROWID" -> Kind.VARCHAR;
            case "TEXT", "TINYTEXT", "MEDIUMTEXT", "LONGTEXT", "CLOB", "NCLOB", "LONG", "LONGVARCHAR", "LONG NVARCHAR", "STRING" -> Kind.TEXT;
            case "TINYINT", "SMALLINT", "INT2", "SMALLSERIAL" -> Kind.SMALLINT;
            case "INT", "INTEGER", "INT4", "MEDIUMINT", "SERIAL" -> Kind.INTEGER;
            case "BIGINT", "INT8", "BIGSERIAL" -> Kind.BIGINT;
            case "DECIMAL", "DEC", "NUMERIC", "NUMBER" -> Kind.DECIMAL;
            case "REAL", "FLOAT4", "BINARY_FLOAT" -> Kind.FLOAT;
            case "DOUBLE", "DOUBLE PRECISION", "FLOAT8", "BINARY_DOUBLE" -> Kind.DOUBLE;
            case "FLOAT" -> Kind.FLOAT;
            case "BOOL", "BOOLEAN", "BIT" -> Kind.BOOLEAN;
            case "DATE" -> Kind.DATE;
            case "TIME", "TIMETZ", "TIME WITH TIME ZONE", "TIME WITHOUT TIME ZONE" -> Kind.TIME;
            case "DATETIME", "DATETIME2", "DATETIMEOFFSET", "SMALLDATETIME", "TIMESTAMP", "TIMESTAMPTZ", "TIMESTAMP WITH TIME ZONE", "TIMESTAMP WITHOUT TIME ZONE", "TIMESTAMP WITH LOCAL TIME ZONE" -> Kind.TIMESTAMP;
            case "BINARY", "VARBINARY", "RAW", "ROWVERSION" -> Kind.BINARY;
            case "BLOB", "LONGBLOB", "MEDIUMBLOB", "TINYBLOB", "BYTEA", "LONG RAW", "IMAGE", "LONG VARBINARY" -> Kind.BLOB;
            case "JSON", "JSONB", "OBJECT", "ARRAY" -> Kind.JSON;
            case "UUID", "UNIQUEIDENTIFIER" -> Kind.UUID;
            default -> Kind.UNKNOWN;
        };
    }

    public static String base(String raw) {
        // Old registration snapshots can carry explicit Chinese display types.
        // Normalize only unambiguous labels; retain raw text and its arguments
        // on ColumnInfo so lengths, precision and source metadata are unchanged.
        String normalized = nativeBase(raw);
        return switch (normalized) {
            case "字符串" -> "VARCHAR";
            case "字符" -> "CHAR";
            case "文本", "长文本" -> "TEXT";
            case "整数" -> "INTEGER";
            case "长整数" -> "BIGINT";
            case "小数" -> "DECIMAL";
            case "浮点数" -> "DOUBLE";
            case "日期" -> "DATE";
            case "时间" -> "TIME";
            case "日期时间" -> "TIMESTAMP";
            case "布尔" -> "BOOLEAN";
            case "二进制" -> "BLOB";
            case "对象", "数组" -> "JSON";
            default -> normalized;
        };
    }

    /** SQL Server's native TIMESTAMP is rowversion; a date/time display label is not. */
    public static boolean isSqlServerTimestamp(String raw) {
        return "TIMESTAMP".equals(nativeBase(raw));
    }

    private static String nativeBase(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT).replaceAll("\\([^)]*\\)", "")
                .replaceAll("\\s+(UNSIGNED|ZEROFILL)\\b", "").replaceAll("\\s+", " ").trim();
    }

    public static String render(DatabaseType target, ColumnInfo column, String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("字段类型不能为空");
        DdlDialect.Family family = DdlDialect.family(target);
        Kind kind = kind(raw);
        var args = ARGS.matcher(raw);
        Long length = column == null ? null : column.getLength();
        Integer precision = column == null ? null : column.getPrecision(), scale = column == null ? null : column.getScale();
        if (args.find()) {
            long first = Long.parseLong(args.group(1));
            if (length == null || length <= 0) length = first;
            if (precision == null || precision <= 0) precision = first > Integer.MAX_VALUE ? null : (int) first;
            if (scale == null && args.group(2) != null) scale = Integer.parseInt(args.group(2));
        }
        String source = column == null ? "" : text(column.getSourceDatabaseType());
        DatabaseType sourceType = optionalSourceType(source);
        boolean oracleSource = sourceType != null && DdlDialect.family(sourceType) == DdlDialect.Family.ORACLE;
        if (base(raw).equals("LONG") && !oracleSource) kind = Kind.BIGINT;
        if (sourceType == DatabaseType.SQLSERVER && isSqlServerTimestamp(raw)) { kind = Kind.BINARY; length = 8L; }
        if (oracleSource && base(raw).equals("DATE") && family != DdlDialect.Family.ORACLE) kind = Kind.TIMESTAMP;
        if (oracleSource && base(raw).equals("FLOAT")) kind = Kind.DECIMAL;
        boolean unsigned = raw.toUpperCase(Locale.ROOT).contains("UNSIGNED");
        if (unsigned && kind == Kind.BIGINT) { kind = Kind.DECIMAL; precision = 20; scale = 0; }
        else if (unsigned && kind == Kind.INTEGER) kind = Kind.BIGINT;
        else if (unsigned && kind == Kind.SMALLINT) kind = Kind.INTEGER;
        long size = length == null ? 0 : length;
        String numeric = precision != null && precision > 0
                ? "(" + precision + (scale == null ? "" : "," + scale) + ")" : "";
        return switch (kind) {
            case CHAR, VARCHAR -> {
                boolean national = Set.of("NCHAR","NVARCHAR","NVARCHAR2","NATIONAL CHARACTER VARYING").contains(base(raw));
                long limit = family == DdlDialect.Family.ORACLE ? national ? 2000 : 4000 : family == DdlDialect.Family.SQLSERVER ? national ? 4000 : 8000 : 65535;
                if (national && size > limit && family == DdlDialect.Family.ORACLE) yield "NCLOB";
                if (size > limit) yield textType(family);
                if (family == DdlDialect.Family.HIVE) yield "STRING";
                if (size <= 0 && family == DdlDialect.Family.POSTGRES) yield kind == Kind.CHAR ? "CHAR" : "VARCHAR";
                long bounded = size > 0 ? size : kind == Kind.CHAR ? 1 : 255;
                String name = kind == Kind.CHAR ? "CHAR" : family == DdlDialect.Family.ORACLE ? "VARCHAR2" : "VARCHAR";
                if (national && family == DdlDialect.Family.SQLSERVER) name = kind == Kind.CHAR ? "NCHAR" : "NVARCHAR";
                if (national && family == DdlDialect.Family.ORACLE) name = kind == Kind.CHAR ? "NCHAR" : "NVARCHAR2";
                yield name + "(" + bounded + ")";
            }
            case TEXT -> target == DatabaseType.HETU ? "VARCHAR" : textType(family);
            case SMALLINT -> family == DdlDialect.Family.ORACLE ? "NUMBER(5)" : "SMALLINT";
            case INTEGER -> family == DdlDialect.Family.ORACLE ? "NUMBER(10)" : "INTEGER";
            case BIGINT -> family == DdlDialect.Family.ORACLE ? "NUMBER(19)" : "BIGINT";
            case DECIMAL -> {
                if (family == DdlDialect.Family.HIVE && (precision == null || precision <= 0 || precision > 38 || scale == null || scale < 0 || scale > precision)) yield "STRING";
                yield (family == DdlDialect.Family.ORACLE ? "NUMBER" : family == DdlDialect.Family.POSTGRES ? "NUMERIC" : "DECIMAL")
                        + (numeric.isEmpty() && family == DdlDialect.Family.MYSQL ? "(18,0)" : numeric);
            }
            case FLOAT -> family == DdlDialect.Family.ORACLE ? "BINARY_FLOAT" : family == DdlDialect.Family.SQLSERVER ? "REAL" : "FLOAT";
            case DOUBLE -> switch (family) { case ORACLE -> "BINARY_DOUBLE"; case SQLSERVER -> "FLOAT(53)"; case POSTGRES, DB2, ANSI -> "DOUBLE PRECISION"; default -> "DOUBLE"; };
            case BOOLEAN -> switch (family) { case ORACLE -> "NUMBER(1)"; case SQLSERVER -> "BIT"; case MYSQL -> "TINYINT"; default -> "BOOLEAN"; };
            case DATE -> "DATE";
            case TIME -> family == DdlDialect.Family.ORACLE || family == DdlDialect.Family.HIVE ? "TIMESTAMP" : "TIME";
            case TIMESTAMP -> raw.toUpperCase(Locale.ROOT).contains("WITH TIME ZONE") || Set.of("TIMESTAMPTZ","DATETIMEOFFSET").contains(base(raw))
                    ? switch (family) { case POSTGRES, ORACLE -> "TIMESTAMP WITH TIME ZONE"; case SQLSERVER -> "DATETIMEOFFSET"; default -> textType(family); }
                    : switch (family) { case MYSQL -> "DATETIME"; case SQLSERVER -> "DATETIME2"; default -> "TIMESTAMP"; };
            case BINARY, BLOB -> target == DatabaseType.HETU ? "VARBINARY" : switch (family) {
                case ORACLE -> kind == Kind.BINARY && size > 0 && size <= 2000 ? "RAW(" + size + ")" : "BLOB";
                case POSTGRES -> "BYTEA";
                case MYSQL -> kind == Kind.BINARY && size > 0 && size <= 65535 ? "VARBINARY(" + size + ")" : "LONGBLOB";
                case SQLSERVER -> kind == Kind.BINARY && size > 0 && size <= 8000 ? "VARBINARY(" + size + ")" : "VARBINARY(MAX)";
                case HIVE -> "BINARY";
                default -> "BLOB";
            };
            case JSON -> family == DdlDialect.Family.POSTGRES ? "JSONB" : family == DdlDialect.Family.MYSQL ? "JSON" : textType(family);
            case UUID -> family == DdlDialect.Family.POSTGRES ? "UUID" : family == DdlDialect.Family.SQLSERVER ? "UNIQUEIDENTIFIER" : family == DdlDialect.Family.HIVE ? "STRING" : family == DdlDialect.Family.ORACLE ? "VARCHAR2(36)" : "VARCHAR(36)";
            // Preserve a native extension if it belongs to the destination family. Otherwise use unbounded text.
            case UNKNOWN -> source.isBlank() || sameFamily(source, target) ? raw.trim() : textType(family);
        };
    }

    public static String textType(DdlDialect.Family family) {
        return switch (family) { case ORACLE -> "CLOB"; case SQLSERVER -> "NVARCHAR(MAX)"; case MYSQL -> "LONGTEXT"; case HIVE -> "STRING"; case DB2 -> "CLOB"; default -> "TEXT"; };
    }
    private static boolean sameFamily(String source, DatabaseType target) {
        try { return DdlDialect.family(DatabaseType.fromCode(source)) == DdlDialect.family(target); }
        catch (IllegalArgumentException unknown) { return false; }
    }
    private static DatabaseType optionalSourceType(String source) {
        try { return source.isBlank() ? null : DatabaseType.fromCode(source); }
        catch (IllegalArgumentException unknown) { return null; }
    }
    private static String text(String value) { return value == null ? "" : value.trim(); }
}
