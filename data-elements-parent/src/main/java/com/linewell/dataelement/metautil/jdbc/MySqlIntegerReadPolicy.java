package com.linewell.dataelement.metautil.jdbc;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** External MySQL readers preserve the native TINYINT range instead of treating display width as boolean. */
public final class MySqlIntegerReadPolicy {
    private static final String PROPERTY = "tinyInt1isBit";
    private MySqlIntegerReadPolicy() {}

    public static String url(String url) {
        if (url == null || !url.startsWith("jdbc:mysql:")) return url;
        int question = url.indexOf('?');
        if (question < 0) return url + "?" + PROPERTY + "=false";
        String[] parameters = url.substring(question + 1).split("&", -1);
        boolean found = false;
        for (int index = 0; index < parameters.length; index++) {
            String parameter = parameters[index];
            int equals = parameter.indexOf('=');
            String key = equals < 0 ? parameter : parameter.substring(0, equals);
            try { key = URLDecoder.decode(key, StandardCharsets.UTF_8); }
            catch (IllegalArgumentException invalid) { continue; } // Preserve the original driver's invalid-URL handling.
            if (PROPERTY.equalsIgnoreCase(key)) {
                parameters[index] = PROPERTY + "=false";
                found = true;
            }
        }
        if (found) return url.substring(0, question + 1) + String.join("&", parameters);
        return url + (url.endsWith("?") || url.endsWith("&") ? "" : "&") + PROPERTY + "=false";
    }
}
