package com.linewell.dataelement.magic.runtime.route;

import jakarta.servlet.http.HttpServletRequest;
import org.ssssssss.magicapi.core.model.ApiInfo;
import org.ssssssss.magicapi.core.servlet.MagicHttpServletRequest;

/** Selects JSON binding and bounded capture from the resource selected by Magic dispatch. */
public enum MagicJsonBody {
    OBJECT_MAP,
    EXACT_TREE,
    CACHED;

    private static final String OPTION = "jsonBody";
    private static final String MATCHED = MagicJsonBody.class.getName() + ".matched";

    public static MagicJsonBody declared(ApiInfo api) {
        if (api == null || api.getOptions() == null) {
            return null;
        }
        var options = api.getOptions().stream()
                .filter(option -> option != null && OPTION.equals(option.getName()))
                .toList();
        if (options.isEmpty()) {
            return null;
        }
        if (!"POST".equals(api.getMethod()) || options.size() != 1
                || !(options.getFirst().getValue() instanceof String value)) {
            throw new IllegalArgumentException("JSON 请求体选项只允许在 POST 资源中声明一次");
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("未知 JSON 请求体选项：" + value, error);
        }
    }

    static void select(MagicHttpServletRequest request, ApiInfo api) {
        if ("POST".equals(request.getMethod())) {
            request.setAttribute(MATCHED, declared(api));
        }
    }

    public static MagicJsonBody matched(HttpServletRequest request) {
        return request == null ? null : (MagicJsonBody) request.getAttribute(MATCHED);
    }
}
