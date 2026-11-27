package com.linewell.dataelement.mapping.core;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/** Shared tokenizer for registration previews, mapping execution and generated NiFi scripts. */
public final class MultiValueTokens {
    public static final int MAX_TOKENS = 1000;
    private static final String COMMON = "[,，、;；\\s\\p{Z}\\uFEFF]+";
    private static final Pattern COMMON_PATTERN = Pattern.compile(COMMON);
    private static final Pattern EDGE_SPACE = Pattern.compile("^[\\s\\p{Z}\\uFEFF]+|[\\s\\p{Z}\\uFEFF]+$");

    private MultiValueTokens() {}

    /** Common separator choices accept mixed punctuation/whitespace; custom delimiters stay literal. */
    public static Pattern pattern(String separator) {
        if (separator == null || separator.isEmpty() || separator.length() > 8)
            throw new MappingException("INVALID_ARGUMENT", "多值翻译分隔符须为1至8个字符");
        boolean common = separator.length() == 1 && (",，、;；".indexOf(separator.charAt(0)) >= 0
                || Character.isWhitespace(separator.charAt(0)) || Character.isSpaceChar(separator.charAt(0))
                || separator.charAt(0) == '\uFEFF');
        return common ? COMMON_PATTERN : Pattern.compile(Pattern.quote(separator));
    }

    public static List<String> split(Object raw, String separator) {
        List<String> result = new ArrayList<>();
        if (raw == null) return List.of();
        // A structured array already supplies token boundaries; do not split inside its individual codes.
        if (raw instanceof Collection<?> values) {
            for (Object value : values) add(result, value);
        } else if (raw.getClass().isArray()) {
            for (int index = 0; index < Array.getLength(raw); index++) add(result, Array.get(raw, index));
        } else {
            String value = raw.toString();
            var matcher = pattern(separator).matcher(value);
            int start = 0;
            while (matcher.find()) {
                add(result, value.substring(start, matcher.start()));
                start = matcher.end();
            }
            add(result, value.substring(start));
        }
        return List.copyOf(result);
    }

    private static void add(List<String> result, Object raw) {
        if (raw == null) return;
        String value = EDGE_SPACE.matcher(raw.toString()).replaceAll("");
        if (value.isEmpty()) return;
        if (result.size() >= MAX_TOKENS)
            throw new MappingException("BUDGET_EXCEEDED", "单字段多值编码超过1000");
        result.add(value);
    }
}
