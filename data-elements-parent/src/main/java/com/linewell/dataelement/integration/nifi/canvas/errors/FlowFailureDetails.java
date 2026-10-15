package com.linewell.dataelement.integration.nifi.canvas.errors;

import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Bounded public diagnostics, including NiFi's property-validation response rather than only HTTP status. */
public final class FlowFailureDetails {
    private static final Pattern SECRET = Pattern.compile(
            "(?i)((?:[\"']?)(?:password|passwd|pwd|token|access_token|authorization|secret)(?:[\"']?)\\s*[:=]\\s*)(?:\"[^\"]*\"|'[^']*'|[^\\s,;&}]+)");
    private FlowFailureDetails() {}

    public static String safe(String text) {
        if (text == null) return "";
        String result = SECRET.matcher(text).replaceAll("$1[已隐藏]")
                .replaceAll("(?i)(Bearer\\s+)[\\w.+/-]+", "$1[已隐藏]")
                .replaceAll("(//)[^\\s/:@]+:[^\\s/@]+@", "$1[已隐藏]@");
        return result.length() > 12000 ? result.substring(0, 12000) + "\n（详情过长，已截断）" : result;
    }

    public static String describe(Throwable error) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> details = new LinkedHashSet<>();
        for (Throwable current = error; current != null && visited.size() < 8 && visited.add(current); current = current.getCause()) {
            String message = current.getMessage();
            details.add(message == null || message.isBlank() ? current.getClass().getSimpleName() : message);
            if (current instanceof NifiException nifi && nifi.body() != null && !nifi.body().isBlank()) {
                details.add("NiFi 响应（HTTP " + nifi.status() + "）：" + nifi.body());
            }
        }
        return safe(String.join("\n原因：", details));
    }
}
