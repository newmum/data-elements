package com.linewell.dataelement.platform.persistence;

import java.util.Locale;
import java.util.Map;

/** Client fields are identifiers in an explicit map, never SQL fragments. */
public final class ServerTableOrder {
    private ServerTableOrder() {}

    public static String sql(String field, String direction, Map<String, String> allowed,
                             String defaultField, String defaultDirection, String identity) {
        String key = field == null || field.isBlank() ? defaultField : field.trim();
        String expression = allowed.get(key);
        if (expression == null) throw new IllegalArgumentException("不支持的排序字段：" + key);
        String order = direction == null || direction.isBlank() ? defaultDirection : direction.trim().toLowerCase(Locale.ROOT);
        if (!order.equals("asc") && !order.equals("desc")) throw new IllegalArgumentException("排序方向只能是 asc 或 desc");
        return "CASE WHEN " + expression + " IS NULL THEN 1 ELSE 0 END ASC, "
                + expression + " " + order + ", " + identity + " ASC";
    }
}
