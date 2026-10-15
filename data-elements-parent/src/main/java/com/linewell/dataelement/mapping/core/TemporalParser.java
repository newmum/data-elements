package com.linewell.dataelement.mapping.core;

import java.math.BigInteger;
import java.time.*;
import java.time.format.*;
import java.time.temporal.*;
import java.util.*;
import java.util.regex.Pattern;

/** Shared by preview and NiFi. Immutable formatters, bounded input and shape-based dispatch. */
public final class TemporalParser {
    private TemporalParser() {}
    private static final Pattern NUMERIC_DATE = Pattern.compile("(?:[0-9]{4}|[0-9]{2})([-/.])[0-9]{1,2}\\1[0-9]{1,2}(?:[ T].+)?");
    private static final Pattern OFFSET_SPACE = Pattern.compile("\\s+(?=[+-][0-9]{2}(?::?[0-9]{2})?(?:\\[.*])?$)");
    private static final Pattern SHORT_OFFSET = Pattern.compile("([+-][0-9]{2})([0-9]{2})(?=\\[|$)");
    private static final Pattern HOUR_OFFSET = Pattern.compile("([T ][0-9].*)([+-][0-9]{2})(?=\\[|$)");
    private static final Pattern DIGITS = Pattern.compile("[0-9]+");
    private static final Pattern ENGLISH_DATE = Pattern.compile("[A-Za-z]{3} [A-Za-z]{3} [0-9]{1,2} [0-9]{2}:[0-9]{2}:[0-9]{2} ([^ ]+) [0-9]{4}");
    private static final Pattern ORACLE_DATE = Pattern.compile("[0-9]{1,2}-[A-Za-z]{3}-[0-9]{2,4} .+");
    private static final Pattern PARTIAL_YEAR_MONTH = Pattern.compile("[0-9]{4}[-/][0-9]{1,2}");
    private static final Pattern PARTIAL_MONTH_DAY = Pattern.compile("[0-9]{1,2}-[0-9]{1,2}");
    private static final Pattern TIME_TEXT = Pattern.compile("[0-9]{1,2}:[0-9]{1,2}(?::[0-9]{1,2}(?:\\.[0-9]{1,9})?)?");
    private static final Pattern ISO_SEPARATOR = Pattern.compile("(?<=[0-9])T(?=[0-9])");
    private static final DateTimeFormatter TIME = strict(new DateTimeFormatterBuilder().appendPattern("H:m")
            .optionalStart().appendLiteral(':').appendValue(ChronoField.SECOND_OF_MINUTE, 1, 2, SignStyle.NOT_NEGATIVE)
            .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true).optionalEnd().optionalEnd());
    private static final Map<String, DateTimeFormatter> NUMERIC = numericFormatters();
    private static final DateTimeFormatter CHINESE = dateTime(new DateTimeFormatterBuilder().appendPattern("uuuu年M月d日"));
    private static final DateTimeFormatter COMPACT = pattern("uuuuMMddHHmmss");
    private static final DateTimeFormatter ZONE_SHORT = DateTimeFormatter.ofPattern("z",Locale.ENGLISH);
    private static final DateTimeFormatter ENGLISH = strict(new DateTimeFormatterBuilder().parseCaseInsensitive()
            .appendPattern("EEE MMM d HH:mm:ss VV uuuu"));
    private static final DateTimeFormatter ORACLE_TWO = oracle(2);
    private static final DateTimeFormatter ORACLE_FOUR = oracle(4);
    private static final DateTimeFormatter YEAR_MONTH = pattern("uuuu-M");
    private static final DateTimeFormatter MONTH_DAY = pattern("M-d");
    private static final DateTimeFormatter RFC = DateTimeFormatter.RFC_1123_DATE_TIME.withResolverStyle(ResolverStyle.STRICT);

    /** A compiled parser is safe to share across processor tasks. Numeric epoch units are always explicit. */
    public record Parser(String mode, DateTimeFormatter formatter, String outputType, LocalDate defaultDate) {
        public Object parse(Object value, ZoneId zone, Locale locale) {
            if (value == null || value == ValueTypes.MISSING) return null;
            try {
                Parts parts;
                if (mode.startsWith("EPOCH_")) parts = new Parts(null, null, null, null, epoch(value, mode));
                else if (mode.equals("AUTO")) parts = automatic(value, zone, defaultDate);
                else parts = parts(formatter.withLocale(locale).parse(text(value)), defaultDate);
                return convert(parts, outputType, defaultDate, zone);
            } catch (MappingException ex) { throw ex; }
            catch (RuntimeException ex) { throw new MappingException("DATE_INVALID", "日期或时间格式无效、日期不存在或值超出范围"); }
        }
    }

    public static Parser bind(String inputPattern, String type, Map<?, ?> options) {
        String mode = inputPattern == null || inputPattern.isBlank() ? "AUTO" : inputPattern.trim();
        String output = ValueTypes.type(type);
        if (!Set.of("DATE", "TIME", "DATETIME", "INSTANT").contains(output))
            throw new MappingException("INVALID_ARGUMENT", "日期解析的输出类型须为日期、时分秒、日期时间或绝对时间");
        if (options == null) options = Map.of();
        if (options.keySet().stream().anyMatch(k -> !Objects.equals(k, "defaultDate")))
            throw new MappingException("INVALID_ARGUMENT", "日期补全参数仅支持defaultDate");
        try {
            Object base = options.get("defaultDate");
            LocalDate defaultDate = base == null || base.toString().isBlank() ? null : LocalDate.parse(base.toString());
            if (mode.equalsIgnoreCase("AUTO")) return new Parser("AUTO", null, output, defaultDate);
            if (Set.of("EPOCH_SECONDS", "EPOCH_MILLIS", "EPOCH_MICROS", "EPOCH_NANOS").contains(mode))
                return new Parser(mode, null, output, defaultDate);
            if (mode.length() > 256) throw new IllegalArgumentException();
            return new Parser("PATTERN", pattern(normalizePattern(mode)), output, defaultDate);
        } catch (RuntimeException ex) { throw new MappingException("INVALID_ARGUMENT", "日期格式或补全日期无效，补全日期须为uuuu-MM-dd"); }
    }

    private static final Map<String, Parser> AUTO_PARSERS = Map.of("DATE", new Parser("AUTO", null, "DATE", null),
            "TIME", new Parser("AUTO", null, "TIME", null), "DATETIME", new Parser("AUTO", null, "DATETIME", null),
            "INSTANT", new Parser("AUTO", null, "INSTANT", null));
    public static Object auto(Object value, String type, ZoneId zone) { return AUTO_PARSERS.get(type).parse(value, zone, Locale.ROOT); }

    private record Parts(LocalDate date, LocalTime time, ZoneId sourceZone, ZoneOffset offset, Instant instant) {}

    private static Parts automatic(Object value, ZoneId zone, LocalDate defaultDate) {
        if (value instanceof LocalDateTime d) return new Parts(d.toLocalDate(), d.toLocalTime(), null, null, null);
        if (value instanceof LocalDate d) return new Parts(d, null, null, null, null);
        if (value instanceof LocalTime t) return new Parts(null, t, null, null, null);
        if (value instanceof ZonedDateTime d) return new Parts(null, null, null, null, d.toInstant());
        if (value instanceof OffsetDateTime d) return new Parts(null, null, null, null, d.toInstant());
        if (value instanceof Instant i) return new Parts(null, null, null, null, i);
        if (value instanceof java.sql.Timestamp d) return new Parts(null, null, null, null, d.toInstant());
        if (value instanceof java.sql.Date d) return new Parts(d.toLocalDate(), null, null, null, null);
        if (value instanceof java.sql.Time t) return new Parts(null, t.toLocalTime(), null, null, null);
        if (value instanceof java.util.Date d) return new Parts(null, null, null, null, d.toInstant());
        if(value instanceof Number) {
            String calendar;
            try {calendar=ValueTypes.decimal(value).toBigIntegerExact().toString();}
            catch(ArithmeticException ex){throw new MappingException("DATE_UNIT_REQUIRED","非整数时间数值须明确格式或时间戳单位");}
            if(calendar.length()!=8&&calendar.length()!=14)throw new MappingException("DATE_UNIT_REQUIRED","数值仅自动识别8位日期或14位紧凑日期时间；时间戳须明确单位");
            value=calendar;
        }
        if (!(value instanceof CharSequence)) throw new MappingException("DATE_UNIT_REQUIRED", "数值时间戳须明确选择秒、毫秒、微秒或纳秒单位");
        String s = text(value);
        if (DIGITS.matcher(s).matches()) {
            if (s.length() == 8) return parts(DateTimeFormatter.BASIC_ISO_DATE.parse(s), defaultDate);
            if (s.length() >= 14 && s.length() <= 23) {
                LocalDateTime d=LocalDateTime.parse(s.substring(0,14),COMPACT);
                if(s.length()>14)d=d.withNano(Integer.parseInt(s.substring(14)+"0".repeat(23-s.length())));
                return new Parts(d.toLocalDate(),d.toLocalTime(),null,null,null);
            }
            throw new MappingException("DATE_UNIT_REQUIRED", "纯数字仅自动识别8位日期或14至23位紧凑日期时间；时间戳须明确单位");
        }
        var numeric = NUMERIC_DATE.matcher(s);
        if (numeric.matches()) {
            String separator = numeric.group(1);
            int yearLength = s.indexOf(separator);
            // Day/month/year is deliberately not guessed as month/day/year.
            if (yearLength == 2 && separator.equals("/") || yearLength == 2 && separator.equals("."))
                throw new MappingException("DATE_AMBIGUOUS", "日月年或月日年格式须指定输入格式");
            String normalized = normalizeOffset(ISO_SEPARATOR.matcher(s).replaceFirst(" "));
            return parts(NUMERIC.get(yearLength + separator).parse(normalized), defaultDate);
        }
        if (s.indexOf('年') > 0) return parts(CHINESE.parse(s), defaultDate);
        if (PARTIAL_YEAR_MONTH.matcher(s).matches()) return parts(YEAR_MONTH.parse(s.replace('/', '-')), defaultDate);
        if (PARTIAL_MONTH_DAY.matcher(s).matches()) return parts(MONTH_DAY.parse(s), defaultDate);
        if (TIME_TEXT.matcher(s).matches()) return parts(TIME.parse(s), defaultDate);
        if (s.indexOf(',') > 0) return parts(RFC.parse(s), defaultDate);
        var english = ENGLISH_DATE.matcher(s);
        if (english.matches()) {
            String token = english.group(1);
            ZoneId source; boolean abbreviation=false;
            if (token.equals("UTC") || token.equals("GMT") || token.equals("Z") || token.contains("/") || token.startsWith("GMT+") || token.startsWith("GMT-")) source = ZoneId.of(token);
            else {
                // Date.toString() abbreviations have meaning only in the explicitly configured flow zone.
                source = zone; abbreviation=true;
            }
            Parts p = parts(ENGLISH.parse(s.substring(0, english.start(1)) + source.getId() + s.substring(english.end(1))), defaultDate);
            if(abbreviation&&!ZONE_SHORT.format(p.date.atTime(p.time).atZone(zone)).equals(token))
                throw new MappingException("DATE_AMBIGUOUS", "时区缩写与流程时区不一致，请使用UTC、GMT、数字偏移或明确的地区时区");
            return p;
        }
        if (ORACLE_DATE.matcher(s).matches()) {
            int end = s.indexOf(' '), yearLength = end - s.lastIndexOf('-', end);
            return parts((yearLength == 3 ? ORACLE_TWO : ORACLE_FOUR).parse(s), defaultDate);
        }
        throw new MappingException("DATE_INVALID", "未识别日期或时间格式，请选择指定格式；不会自动转换为NULL或1970年");
    }

    private static String text(Object value) {
        String s = value.toString().strip();
        if (s.isEmpty() || s.length() > 128) throw new MappingException("DATE_INVALID", "日期输入为空或超过128字符");
        return s;
    }

    private static String normalizeOffset(String s) {
        s = OFFSET_SPACE.matcher(s).replaceAll("");
        s = SHORT_OFFSET.matcher(s).replaceAll("$1:$2");
        return HOUR_OFFSET.matcher(s).replaceAll("$1$2:00");
    }

    private static Parts parts(TemporalAccessor parsed, LocalDate defaultDate) {
        LocalDate date = parsed.query(TemporalQueries.localDate());
        LocalTime time = parsed.query(TemporalQueries.localTime());
        if (date == null && (parsed.isSupported(ChronoField.YEAR) || parsed.isSupported(ChronoField.MONTH_OF_YEAR))) {
            if (defaultDate == null) throw incomplete();
            date = LocalDate.of(parsed.isSupported(ChronoField.YEAR) ? parsed.get(ChronoField.YEAR) : defaultDate.getYear(),
                    parsed.isSupported(ChronoField.MONTH_OF_YEAR) ? parsed.get(ChronoField.MONTH_OF_YEAR) : defaultDate.getMonthValue(),
                    parsed.isSupported(ChronoField.DAY_OF_MONTH) ? parsed.get(ChronoField.DAY_OF_MONTH) : defaultDate.getDayOfMonth());
        }
        return new Parts(date, time, parsed.query(TemporalQueries.zone()), parsed.query(TemporalQueries.offset()), null);
    }

    private static Object convert(Parts p, String type, LocalDate defaultDate, ZoneId zone) {
        if (p.instant != null) return switch (type) {
            case "INSTANT" -> p.instant;
            case "DATE" -> p.instant.atZone(zone).toLocalDate();
            case "TIME" -> p.instant.atZone(zone).toLocalTime();
            default -> p.instant.atZone(zone).toLocalDateTime();
        };
        LocalDate date = p.date != null ? p.date : defaultDate;
        if (type.equals("TIME")) {
            if (p.time == null) throw incomplete();
            return p.time;
        }
        if (date == null) throw incomplete();
        LocalDateTime local = date.atTime(p.time == null ? LocalTime.MIDNIGHT : p.time);
        if (p.sourceZone != null || p.offset != null || type.equals("INSTANT")) {
            ZoneId source = p.sourceZone == null ? p.offset == null ? zone : p.offset : p.sourceZone;
            var offsets = source.getRules().getValidOffsets(local);
            if (offsets.isEmpty() || p.offset == null && offsets.size() != 1 || p.offset != null && !offsets.contains(p.offset))
                throw new MappingException("DATE_AMBIGUOUS", "时间处于夏令时重叠/缺口，或地区时区与偏移不一致");
            Instant instant = local.toInstant(p.offset == null ? offsets.getFirst() : p.offset);
            if (type.equals("INSTANT")) return instant;
            local = instant.atZone(zone).toLocalDateTime();
        }
        // DATE/DATETIME are calendar values without an offset. Only INSTANT resolution
        // above needs a unique zone offset; a birthday must not fail on historical DST.
        return type.equals("DATE") ? local.toLocalDate() : local;
    }

    private static MappingException incomplete() { return new MappingException("DATE_INCOMPLETE", "日期信息不完整；年月、月日或纯时间转完整日期须配置补全日期"); }

    private static Instant epoch(Object value, String mode) {
        BigInteger number = ValueTypes.decimal(text(value)).toBigIntegerExact();
        long factor = switch (mode) { case "EPOCH_SECONDS" -> 1; case "EPOCH_MILLIS" -> 1000; case "EPOCH_MICROS" -> 1_000_000; default -> 1_000_000_000; };
        BigInteger[] split = number.divideAndRemainder(BigInteger.valueOf(factor));
        return Instant.ofEpochSecond(split[0].longValueExact(), split[1].longValueExact() * (1_000_000_000 / factor));
    }

    private static DateTimeFormatter strict(DateTimeFormatterBuilder b) { return b.toFormatter(Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT); }
    private static DateTimeFormatter pattern(String p) { return DateTimeFormatter.ofPattern(p, Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT); }
    private static DateTimeFormatter dateTime(DateTimeFormatterBuilder b) {
        return strict(b.optionalStart().appendLiteral(' ').append(TIME)
                .optionalStart().appendOffsetId().optionalEnd()
                .optionalStart().appendLiteral('[').appendZoneId().appendLiteral(']').optionalEnd().optionalEnd());
    }
    private static Map<String, DateTimeFormatter> numericFormatters() {
        Map<String, DateTimeFormatter> map = new HashMap<>();
        for (String separator : List.of("-", "/", ".")) {
            map.put("4" + separator, dateTime(new DateTimeFormatterBuilder().appendPattern("uuuu" + separator + "M" + separator + "d")));
            map.put("2" + separator, dateTime(new DateTimeFormatterBuilder().appendValueReduced(ChronoField.YEAR, 2, 2, 2000)
                    .appendPattern(separator + "M" + separator + "d")));
        }
        return Map.copyOf(map);
    }
    private static DateTimeFormatter oracle(int yearDigits) {
        DateTimeFormatterBuilder b = new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d-MMM-");
        if (yearDigits == 2) b.appendValueReduced(ChronoField.YEAR, 2, 2, 2000); else b.appendValue(ChronoField.YEAR, 4);
        return strict(b.appendPattern(" hh.mm.ss").optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true).optionalEnd().appendPattern(" a"));
    }
    /** Compatibility with yyyy/yy, without rewriting quoted literal text. */
    private static String normalizePattern(String pattern) {
        StringBuilder out = new StringBuilder(); boolean quoted = false;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '\'') { out.append(c); if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '\'') out.append(pattern.charAt(++i)); else quoted = !quoted; }
            else out.append(c == 'y' && !quoted ? 'u' : c);
        }
        return out.toString();
    }
}
