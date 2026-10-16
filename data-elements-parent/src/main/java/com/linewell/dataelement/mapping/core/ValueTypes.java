package com.linewell.dataelement.mapping.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class ValueTypes {
    private static final Set<String> LOGICAL_TYPES = Set.of("ANY", "STRING", "INT", "LONG", "DECIMAL", "DOUBLE", "BOOLEAN", "DATE", "DATETIME", "TIME", "INSTANT", "ARRAY");
    private static final Pattern DIMENSIONS = Pattern.compile("\\([^)]*\\)");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern UNSIGNED = Pattern.compile("\\s+UNSIGNED$");
    public static final Object MISSING = new Object();
    private ValueTypes() {}
    public static BigDecimal decimal(Object value) {
        if (value == null || value == MISSING) throw new MappingException("TYPE_MISMATCH", "空值不能参与数值计算");
        try {
            BigDecimal n=value instanceof BigDecimal number?number:new BigDecimal(value.toString().trim());
            if(n.precision()>1000||Math.abs((long)n.scale())>1000)throw new MappingException("BUDGET_EXCEEDED","数值精度或指数超过1000位");
            return n;
        }
        catch (NumberFormatException ex) { throw new MappingException("TYPE_MISMATCH", "输入不是有效的精确数值"); }
    }
    public static String type(String raw) {
        if (raw == null) return "ANY";
        if (LOGICAL_TYPES.contains(raw)) return raw;
        String value = WHITESPACE.matcher(DIMENSIONS.matcher(raw.toUpperCase(Locale.ROOT)).replaceAll("").trim()).replaceAll(" ");
        if(value.equals("INT UNSIGNED")||value.equals("INTEGER UNSIGNED"))return "LONG";
        if(value.equals("BIGINT UNSIGNED"))return "DECIMAL";
        value=UNSIGNED.matcher(value).replaceAll("");
        return switch (value) {
            case "VARCHAR", "VARCHAR2", "NVARCHAR", "NVARCHAR2", "CHAR", "NCHAR", "TEXT", "CLOB", "LONGVARCHAR",
                 "CHARACTER VARYING", "CHAR VARYING", "NCHAR VARYING", "NATIONAL CHARACTER VARYING", "NATIONAL CHAR VARYING",
                 "CHARACTER", "NATIONAL CHARACTER", "NATIONAL CHAR", "LONGNVARCHAR", "NCLOB", "NTEXT", "BPCHAR",
                 "TINYTEXT", "MEDIUMTEXT", "LONGTEXT", "VARCHAR_IGNORECASE", "VARCHAR_CASESENSITIVE" -> "STRING";
            case "TINYINT", "SMALLINT", "MEDIUMINT", "INTEGER", "INT2", "INT4" -> "INT";
            case "BIGINT", "INT8" -> "LONG";
            case "NUMERIC", "NUMBER", "DEC" -> "DECIMAL";
            case "FLOAT", "REAL", "DOUBLE PRECISION" -> "DOUBLE";
            case "BOOL", "BIT" -> "BOOLEAN";
            case "TIMESTAMP", "DATETIME2", "SMALLDATETIME", "DATETIME64" -> "DATETIME";
            case "TIMESTAMP WITH TIME ZONE", "TIMESTAMP WITH LOCAL TIME ZONE", "TIMESTAMPTZ", "DATETIMEOFFSET" -> "INSTANT";
            case "TIMESTAMP WITHOUT TIME ZONE" -> "DATETIME";
            case "TIME WITHOUT TIME ZONE" -> "TIME";
            default -> value;
        };
    }
    public static String requireType(String raw) {
        String type = type(raw);
        if (!LOGICAL_TYPES.contains(type)) throw new MappingException("UNSUPPORTED_TYPE", "不支持的逻辑目标类型: " + raw);
        return type;
    }
    public static Object cast(Object value, String rawType, int precision, int scale, RoundingMode rounding) {
        return cast(value,rawType,precision,scale,rounding,ZoneId.systemDefault());
    }
    public static Object cast(Object value,String rawType,int precision,int scale,RoundingMode rounding,ZoneId zone) {
        if (value == null || value == MISSING) return null;
        try {
            return switch (type(rawType)) {
                case "ANY" -> value;
                case "STRING" -> value.toString();
                case "INT" -> value instanceof Boolean b?(b?1:0):decimal(value).intValueExact();
                case "LONG" -> value instanceof Boolean b?(b?1L:0L):decimal(value).longValueExact();
                case "DECIMAL" -> {
                    requireDecimalDimensions(precision, scale);
                    BigDecimal original = decimal(value);
                    if (precision == 0) yield original;
                    BigDecimal n;
                    try { n = original.setScale(scale, rounding); }
                    catch (ArithmeticException ex) {
                        throw new MappingException("TYPE_MISMATCH", "小数位数" + Math.max(0, original.stripTrailingZeros().scale())
                                + "超出目标DECIMAL(" + precision + "," + scale + ")可精确表示的范围；请扩大目标小数位数或显式配置舍入规则");
                    }
                    if (n.precision() > precision) throw new MappingException("TYPE_MISMATCH",
                            "数值需要" + n.precision() + "位有效精度，超出目标DECIMAL(" + precision + "," + scale + ")；请扩大目标精度");
                    yield n;
                }
                case "DOUBLE" -> {
                    double n = Double.parseDouble(value.toString());
                    if (!Double.isFinite(n)) throw new ArithmeticException();
                    yield n;
                }
                case "BOOLEAN" -> switch (value.toString().trim().toLowerCase(Locale.ROOT)) {
                    case "true", "1" -> true;
                    case "false", "0" -> false;
                    default -> throw new IllegalArgumentException();
                };
                case "DATE", "DATETIME", "TIME", "INSTANT" -> TemporalParser.auto(value,type(rawType),zone);
                case "ARRAY" -> { if (!(value instanceof Collection<?>)) throw new IllegalArgumentException(); yield value; }
                default -> throw new MappingException("UNSUPPORTED_TYPE", "不支持的逻辑目标类型: " + rawType);
            };
        } catch (MappingException ex) { throw ex; }
        catch (RuntimeException ex) { throw new MappingException("TYPE_MISMATCH", "目标类型转换失败或超出精度/范围: " + type(rawType)); }
    }
    /** (0,0) means an unconstrained SQL NUMERIC/NUMBER; it never implies an integer. */
    public static void requireDecimalDimensions(int precision, int scale) {
        if (precision == 0 && scale == 0) return;
        // Oracle also permits negative scale and scale greater than precision.
        if (precision < 1 || precision > 1000 || scale < -1000 || scale > 1000)
            throw new MappingException("INVALID_ARGUMENT", "目标小数精度须为1至1000（0表示保留精度），scale须在-1000至1000之间");
    }
    public static LocalDate date(Object value) { return (LocalDate)TemporalParser.auto(value,"DATE",ZoneId.systemDefault()); }
    public static LocalDateTime dateTime(Object value) { return dateTime(value,ZoneId.systemDefault()); }
    public static LocalDateTime dateTime(Object value,ZoneId zone) { return (LocalDateTime)TemporalParser.auto(value,"DATETIME",zone); }
    public static LocalTime time(Object value) { return (LocalTime)TemporalParser.auto(value,"TIME",ZoneId.systemDefault()); }
    public static Instant instant(Object value,ZoneId zone) { return (Instant)TemporalParser.auto(value,"INSTANT",zone); }
    public static Object canonical(Object value) {
        return value instanceof Number ? decimal(value).stripTrailingZeros() : value;
    }
    public static String field(String name) {
        String n = name == null ? "" : name.trim().replaceFirst("^/", "");
        if (!n.matches("[\\p{L}_][\\p{L}\\p{N}_$#]{0,127}")) throw new MappingException("INVALID_FIELD", "字段引用必须是一级字段标识");
        return n;
    }
    public static long bytes(Object value, int depth) {
        if (depth > 32) throw new MappingException("BUDGET_EXCEEDED", "记录嵌套深度超限");
        if (value == null || value == MISSING) return 8;
        if (value instanceof Map<?,?> map) {
            long sum = 64;
            for (var e : map.entrySet()) { sum += bytes(e.getKey(), depth + 1) + bytes(e.getValue(), depth + 1); if (sum > 8_388_608) break; }
            return sum;
        }
        if (value instanceof Collection<?> values) {
            long sum = 32;
            for (Object item : values) { sum += bytes(item, depth + 1); if (sum > 8_388_608) break; }
            return sum;
        }
        if (value instanceof byte[] b) return b.length;
        return 32L + value.toString().length() * 3L;
    }
}
