package com.linewell.dataelement.shared.error;

import java.util.regex.Pattern;

/** Redacts credentials and business payloads from exception details. */
public final class ErrorDetailSanitizer {
    private static final Pattern PRIVATE_KEY = Pattern.compile("(?s)-----BEGIN [^-]*PRIVATE KEY-----.*?-----END [^-]*PRIVATE KEY-----");
    private static final Pattern SECRET = Pattern.compile("(?i)([\\\"']?(?:password|passwd|pwd|secret|client_secret|access_token|refresh_token|token|api[_-]?key|authorization|cookie|密码|口令)[\\\"']?\\s*[:=]\\s*)(?:\\\"[^\\\"]*\\\"|'[^']*'|[^\\s,;&}]+)");
    private static final Pattern AUTH = Pattern.compile("(?i)\\b(Bearer|Basic)\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern USERINFO = Pattern.compile("(?i)([a-z][a-z0-9+.-]*://)[^\\s/@]+:[^\\s/@]+@");
    private static final Pattern QUOTED = Pattern.compile("'[^'\\r\\n]*'");
    private static final Pattern LONG_NUMBER = Pattern.compile("(?<![A-Za-z0-9])\\d{11,}(?![A-Za-z0-9])");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private ErrorDetailSanitizer() {}

    public static String errorText(String value) {
        String safe = text(value);
        // Exceptions can embed a request/record JSON payload; it is not diagnostic metadata.
        int objectStart = safe.indexOf('{'), objectEnd = safe.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) safe = safe.substring(0, objectStart) + "[结构化原文已移除]" + safe.substring(objectEnd + 1);
        return safe.replaceAll("(?im)^(\\s*(?:###\\s*)?(?:SQL|Parameters|params|payload|record|body|原始数据|数据内容)\\s*[:=]).*$", "$1[原文已移除]");
    }

    public static String text(String value) {
        if (value == null) return "";
        // Bound work before applying regexes to untrusted error text.
        String input = value.length() > 32000 ? value.substring(0, 32000) + "\n[内容超限，已截取；证据不完整]" : value;
        input = PRIVATE_KEY.matcher(input).replaceAll("[私钥已移除]");
        input = AUTH.matcher(input).replaceAll("$1 [已脱敏]");
        input = SECRET.matcher(input).replaceAll("$1[已脱敏]");
        input = USERINFO.matcher(input).replaceAll("$1[已脱敏]@");
        input = QUOTED.matcher(input).replaceAll("'[值已脱敏]'");
        input = LONG_NUMBER.matcher(input).replaceAll("[长号码已脱敏]");
        return EMAIL.matcher(input).replaceAll("[邮箱已脱敏]");
    }
}
