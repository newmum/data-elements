package com.linewell.dataelement.mapping.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

public final class ValueTypes {
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
        String value = raw == null ? "ANY" : raw.toUpperCase(Locale.ROOT).replaceAll("\\([^)]*\\)", "").trim();
        if(value.equals("INT UNSIGNED")||value.equals("INTEGER UNSIGNED"))return "LONG";
        if(value.equals("BIGINT UNSIGNED"))return "DECIMAL";
        value=value.replaceAll("\\s+UNSIGNED$","");
        return switch (value) {
            case "VARCHAR", "VARCHAR2", "NVARCHAR", "NVARCHAR2", "CHAR", "NCHAR", "TEXT", "CLOB", "LONGVARCHAR" -> "STRING";
            case "TINYINT", "SMALLINT", "INTEGER", "INT2", "INT4" -> "INT";
            case "BIGINT", "INT8" -> "LONG";
            case "NUMERIC", "NUMBER", "DEC" -> "DECIMAL";
            case "FLOAT", "REAL", "DOUBLE PRECISION" -> "DOUBLE";
            case "BOOL", "BIT" -> "BOOLEAN";
            case "TIMESTAMP", "DATETIME2" -> "DATETIME";
            case "TIMESTAMP WITH TIME ZONE", "TIMESTAMPTZ" -> "INSTANT";
            case "TIMESTAMP WITHOUT TIME ZONE" -> "DATETIME";
            default -> value;
        };
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
                    if (precision < 1 || precision > 1000 || scale < 0 || scale > precision) throw new IllegalArgumentException();
                    BigDecimal n = decimal(value).setScale(scale, rounding);
                    if (n.precision() > precision) throw new ArithmeticException();
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
                case "DATE" -> value instanceof Timestamp||value instanceof Instant||value instanceof OffsetDateTime?instant(value,zone).atZone(zone).toLocalDate():date(value);
                case "DATETIME" -> dateTime(value,zone);
                case "INSTANT" -> instant(value, zone);
                case "ARRAY" -> { if (!(value instanceof Collection<?>)) throw new IllegalArgumentException(); yield value; }
                default -> throw new MappingException("UNSUPPORTED_TYPE", "不支持的逻辑目标类型: " + rawType);
            };
        } catch (MappingException ex) { throw ex; }
        catch (RuntimeException ex) { throw new MappingException("TYPE_MISMATCH", "目标类型转换失败或超出精度/范围: " + type(rawType)); }
    }
    public static LocalDate date(Object v) {
        if (v instanceof LocalDate d) return d;
        if (v instanceof java.sql.Date d) return d.toLocalDate();
        if (v instanceof LocalDateTime d) return d.toLocalDate();
        return LocalDate.parse(v.toString(), DateTimeFormatter.ISO_LOCAL_DATE.withResolverStyle(ResolverStyle.STRICT));
    }
    public static LocalDateTime dateTime(Object v) {
        return dateTime(v,ZoneId.systemDefault());
    }
    public static LocalDateTime dateTime(Object v,ZoneId zone) {
        if (v instanceof LocalDateTime d) return d;
        if (v instanceof Timestamp d) return d.toInstant().atZone(zone).toLocalDateTime();
        if (v instanceof Instant d) return d.atZone(zone).toLocalDateTime();
        if (v instanceof OffsetDateTime d) return d.toInstant().atZone(zone).toLocalDateTime();
        if (v instanceof LocalDate d) return d.atStartOfDay();
        String text = v.toString().trim().replace(' ', 'T');
        if (text.length() == 10) return date(text).atStartOfDay();
        return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME.withResolverStyle(ResolverStyle.STRICT));
    }
    public static Instant instant(Object v, ZoneId zone) {
        if (v instanceof Instant i) return i;
        if (v instanceof OffsetDateTime d) return d.toInstant();
        if (v instanceof java.sql.Date d) return instant(d.toLocalDate().atStartOfDay(),zone);
        if (v instanceof java.util.Date d) return d.toInstant();
        if (v.toString().endsWith("Z")) return Instant.parse(v.toString());
        LocalDateTime local = dateTime(v);
        var offsets = zone.getRules().getValidOffsets(local);
        if (offsets.size() != 1) throw new MappingException("DATE_AMBIGUOUS", "本地时间处于夏令时重叠或缺口");
        return local.toInstant(offsets.getFirst());
    }
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
