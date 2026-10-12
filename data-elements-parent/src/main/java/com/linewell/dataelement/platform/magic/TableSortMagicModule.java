package com.linewell.dataelement.platform.magic;

import java.math.BigDecimal;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;

/** Sorting primitives only: ownership, filters and pagination stay in the calling read service. */
@Component
@MagicModule("tableSort")
public class TableSortMagicModule {
    public String field(Map<String, ?> request, Map<String, String> allowed, String fallback) {
        String requested = text(request == null ? null : request.get("sortField"));
        if (requested.isEmpty()) requested = fallback;
        String expression = allowed.get(requested);
        if (expression == null) throw new IllegalArgumentException("不支持的排序字段：" + requested);
        return expression;
    }

    public String direction(Map<String, ?> request) {
        String value = text(request == null ? null : request.get("sortDir")).toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return "desc";
        if (!value.equals("asc") && !value.equals("desc")) throw new IllegalArgumentException("排序方向只能是 asc 或 desc");
        return value;
    }

    /** For already bounded authorized configuration/detail collections; never call on a paginated page. */
    public List<Map<String, Object>> rows(Collection<Map<String, Object>> rows, Map<String, ?> request,
                                           Map<String, String> allowed, String fallback, String identity) {
        if (rows.size() > 1000) throw new IllegalArgumentException("列表超过 1000 项，请使用数据库分页排序");
        String key = field(request, allowed, fallback);
        boolean descending = direction(request).equals("desc");
        Collator collator = Collator.getInstance(Locale.CHINA);
        Comparator<Map<String, Object>> comparator = (left, right) -> {
            Object a = left.get(key), b = right.get(key);
            if (a == null || text(a).isEmpty()) return b == null || text(b).isEmpty() ? compareId(left, right, identity) : 1;
            if (b == null || text(b).isEmpty()) return -1;
            int result = a instanceof Number && b instanceof Number
                    ? new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString()))
                    : collator.compare(text(a), text(b));
            if (result == 0) return compareId(left, right, identity);
            return descending ? -result : result;
        };
        List<Map<String, Object>> result = new ArrayList<>(rows);
        result.sort(comparator);
        return result;
    }

    private static int compareId(Map<String, Object> left, Map<String, Object> right, String identity) {
        return text(left.get(identity)).compareTo(text(right.get(identity)));
    }
    private static String text(Object value) { return value == null ? "" : value.toString().trim(); }
}
