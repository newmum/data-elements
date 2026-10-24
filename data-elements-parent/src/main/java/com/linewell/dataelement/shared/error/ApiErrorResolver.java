package com.linewell.dataelement.shared.error;

import com.linewell.dataelement.platform.tenant.domain.TenantAccessException;
import java.io.EOFException;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLException;
import org.elasticsearch.client.ResponseException;

/**
 * Converts exception chains into a concise public message and complete diagnostic detail.
 */
public final class ApiErrorResolver {

    private static final Pattern DATABASE_ERROR_CODE = Pattern.compile(
            "\\b(?:ORA|TNS|PLS|DB2|SQL|DM|KCI|GBASE|GAUSS|KINGBASE)-\\d{3,10}\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final int MAX_CAUSE_DEPTH = 32;
    private static final int MAX_MESSAGE_LENGTH = 300;

    private ApiErrorResolver() {
    }

    public static ApiErrorDetails resolve(Throwable throwable) {
        Throwable actual = throwable == null
                ? new IllegalStateException("Unknown API error")
                : throwable;
        TenantAccessException tenantError = findCause(
                actual,
                TenantAccessException.class
        );
        if (tenantError != null) {
            return new ApiErrorDetails(
                    tenantError.getCode(),
                    defaultText(tenantError.getMessage(), "无权访问当前租户数据"),
                    stackTrace(actual),
                    newTraceId(),
                    actual.getClass().getName()
            );
        }
        Throwable root = rootCause(actual);
        String messages = causalMessages(actual);
        String combined = messages.toLowerCase(Locale.ROOT);
        ErrorDescriptor searchError = describeSearchFailure(actual, combined);
        String backendMessage = extractBackendMessage(messages);
        ErrorDescriptor descriptor = searchError == null
                ? describe(root, combined, backendMessage)
                : searchError;
        return new ApiErrorDetails(
                descriptor.code(),
                descriptor.message(),
                stackTrace(actual),
                newTraceId(),
                actual.getClass().getName()
        );
    }

    /** Identify the failed client from its cause/stack, rather than guessing from an endpoint name. */
    private static ErrorDescriptor describeSearchFailure(Throwable throwable, String messages) {
        if (!hasSearchClientCause(throwable)) {
            return null;
        }
        ResponseException response = findCause(throwable, ResponseException.class);
        int status = response == null ? 0 : response.getResponse().getStatusLine().getStatusCode();
        var clientError = findCause(throwable, co.elastic.clients.elasticsearch._types.ElasticsearchException.class);
        if (status == 0 && clientError != null) {
            status = clientError.status();
        }
        if (status == 401 || status == 403) {
            return new ErrorDescriptor("SEARCH-AUTH-003",
                    "全文检索服务认证或授权失败，请联系管理员检查服务账号及索引访问权限。");
        }
        if (status == 429 || status == 502 || status == 503 || status == 504) {
            return new ErrorDescriptor("SEARCH-UNAVAILABLE-001",
                    "全文检索服务暂时不可用，请稍后重试；如持续失败，请联系管理员检查服务状态与负载。");
        }
        if (findCause(throwable, InterruptedIOException.class) != null
                || findCause(throwable, TimeoutException.class) != null) {
            return new ErrorDescriptor("SEARCH-TIMEOUT-002",
                    "全文检索服务连接或查询超时，请稍后重试；如持续失败，请联系管理员检查网络和服务负载。");
        }
        if (status == 0 && (findCause(throwable, SocketException.class) != null
                || findCause(throwable, UnknownHostException.class) != null
                || findCause(throwable, EOFException.class) != null
                || findCause(throwable, SSLException.class) != null
                || containsAny(messages, "connection reset", "connection refused", "connection closed",
                        "connection is closed", "unknown host", "no route to host", "nohttpresponseexception"))) {
            return new ErrorDescriptor("SEARCH-CONNECTION-001",
                    "全文检索服务无法连接或连接已中断，请稍后重试；如持续失败，请联系管理员检查服务地址、网络及运行状态。");
        }
        if (response != null || clientError != null) {
            return new ErrorDescriptor("SEARCH-QUERY-004",
                    "全文检索查询失败，请联系管理员检查索引状态与查询配置，并提供错误码和跟踪号。");
        }
        return null;
    }

    private static boolean hasSearchClientCause(Throwable throwable) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth++ < MAX_CAUSE_DEPTH) {
            if (isSearchClientClass(current.getClass().getName())) {
                return true;
            }
            for (StackTraceElement frame : current.getStackTrace()) {
                if (isSearchClientClass(frame.getClassName())) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isSearchClientClass(String name) {
        return name.startsWith("org.elasticsearch.")
                || name.startsWith("co.elastic.clients.")
                || name.startsWith("org.springframework.data.elasticsearch.")
                || name.startsWith("org.ssssssss.magicapi.elasticsearch.")
                || name.equals("com.linewell.dataelement.elasticsearch.EsCommonService")
                || name.equals("com.linewell.dataelement.elasticsearch.TenantElasticSearchModule")
                || name.equals("com.linewell.dataelement.elasticsearch.SearchProjectionEsMagicModule");
    }

    public static ApiErrorDetails business(String code, String message, String detail) {
        String resolvedMessage = defaultText(message, "请求处理失败");
        return new ApiErrorDetails(
                defaultText(code, "API-BUSINESS-001"),
                resolvedMessage,
                defaultText(detail, resolvedMessage),
                newTraceId(),
                "BusinessException"
        );
    }

    private static ErrorDescriptor describe(
            Throwable root,
            String combined,
            String backendMessage
    ) {
        if (backendMessage != null) {
            return new ErrorDescriptor(classifyBackendCode(backendMessage), backendMessage);
        }
        if (root instanceof UnknownHostException || containsAny(
                combined,
                "unknown host",
                "unknownhostexception"
        )) {
            return new ErrorDescriptor(
                    "DB-CONNECTION-001",
                    "数据库地址无法解析，请检查主机名或 DNS 配置。"
            );
        }
        if (root instanceof ConnectException || containsAny(
                combined,
                "network adapter could not establish the connection",
                "connection refused",
                "connect timed out",
                "failed to initialize pool",
                "communications link failure",
                "no route to host"
        )) {
            return new ErrorDescriptor(
                    "DB-CONNECTION-001",
                    "数据库连接失败，请检查数据库地址、端口、防火墙、网络连通性和数据库监听服务。"
            );
        }
        if (root instanceof SQLTimeoutException
                || root instanceof SocketTimeoutException
                || root instanceof TimeoutException
                || containsAny(combined, "timeout", "timed out")) {
            return new ErrorDescriptor(
                    "DB-TIMEOUT-002",
                    "数据库连接或查询超时，请检查网络状态、数据库负载和超时配置。"
            );
        }
        if (isAuthenticationFailure(combined)) {
            return new ErrorDescriptor(
                    "DB-AUTH-003",
                    "数据库认证失败，请检查用户名、密码及账号权限。"
            );
        }
        if (root instanceof SQLException || containsAny(
                combined,
                "sqlexception",
                "bad sql grammar"
        )) {
            return new ErrorDescriptor(
                    "DB-SQL-004",
                    "数据库执行失败，请检查 SQL、对象权限及数据库运行状态。"
            );
        }
        if (containsAny(combined, "jsonnull", "type definition error")) {
            return new ErrorDescriptor(
                    "DATA-SERIALIZE-001",
                    "接口数据格式转换失败，请检查空值或字段类型。"
            );
        }
        if (root instanceof IllegalArgumentException || containsAny(
                combined,
                "不能为空",
                "invalid argument"
        )) {
            return new ErrorDescriptor(
                    "API-PARAM-001",
                    defaultText(root.getMessage(), "请求参数不正确，请检查必填项和字段格式。")
            );
        }
        if (containsAny(
                combined,
                "magic-script",
                "magicscriptexception",
                " at row:"
        )) {
            return new ErrorDescriptor(
                    "MAGIC-SCRIPT-001",
                    "接口脚本执行失败，请联系管理员并提供错误码和跟踪号。"
            );
        }
        return new ErrorDescriptor(
                "MAGIC-RUNTIME-001",
                "接口处理失败，请稍后重试；如仍失败，请查看详情并联系管理员。"
        );
    }

    private static String classifyBackendCode(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        if (isAuthenticationFailure(lower)) {
            return "DB-AUTH-003";
        }
        if (containsAny(
                lower,
                "connection refused",
                "connect timed out",
                "unknown host",
                "communications link failure",
                "network adapter could not establish the connection",
                "no route to host"
        )) {
            return "DB-CONNECTION-001";
        }
        if (containsAny(lower, "timeout", "timed out")) {
            return "DB-TIMEOUT-002";
        }
        return "DB-SQL-004";
    }

    private static boolean isAuthenticationFailure(String text) {
        return containsAny(
                text,
                "ora-01017",
                "access denied",
                "authentication failed",
                "password authentication failed",
                "login failed",
                "logon denied",
                "not logged on",
                "permission denied",
                "not sufficient privileges"
        );
    }

    private static String extractBackendMessage(String messages) {
        if (messages == null || messages.isBlank()) {
            return null;
        }
        for (String line : messages.split("\\R")) {
            String text = cleanLine(line);
            if (text == null) {
                continue;
            }
            String lower = text.toLowerCase(Locale.ROOT);
            Matcher codeMatcher = DATABASE_ERROR_CODE.matcher(text);
            if (codeMatcher.find()) {
                return abbreviate(text.substring(codeMatcher.start()).trim());
            }
            if (containsAny(
                    lower,
                    "sqlstate",
                    "access denied",
                    "authentication failed",
                    "password authentication failed",
                    "login failed",
                    "logon denied",
                    "not logged on",
                    "permission denied",
                    "not sufficient privileges",
                    "communications link failure",
                    "network adapter could not establish the connection",
                    "connection refused",
                    "connect timed out",
                    "unknown host",
                    "no route to host",
                    "no suitable driver",
                    "fatal:"
            )) {
                return abbreviate(stripExceptionPrefix(text));
            }
        }
        return null;
    }

    private static String cleanLine(String line) {
        if (line == null) {
            return null;
        }
        String text = line.trim();
        if (text.isEmpty() || text.startsWith("at ") || text.startsWith("... ")) {
            return null;
        }
        int causedBy = text.indexOf("Caused by:");
        if (causedBy >= 0) {
            text = text.substring(causedBy + "Caused by:".length()).trim();
        }
        int nestedException = text.toLowerCase(Locale.ROOT).indexOf("nested exception is");
        if (nestedException >= 0) {
            text = text.substring(nestedException + "nested exception is".length()).trim();
        }
        return text.isEmpty() ? null : text;
    }

    private static String stripExceptionPrefix(String text) {
        String result = text;
        int colon = result.indexOf(':');
        if (colon > 0) {
            String prefix = result.substring(0, colon);
            if (prefix.contains(".") || prefix.endsWith("Exception") || prefix.endsWith("Error")) {
                result = result.substring(colon + 1).trim();
            }
        }
        return result;
    }

    private static String abbreviate(String value) {
        return value.length() <= MAX_MESSAGE_LENGTH
                ? value
                : value.substring(0, MAX_MESSAGE_LENGTH) + "...";
    }

    private static String causalMessages(Throwable throwable) {
        StringBuilder messages = new StringBuilder();
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth++ < MAX_CAUSE_DEPTH) {
            if (current.getMessage() != null) {
                messages.append(current.getClass().getName())
                        .append(": ")
                        .append(current.getMessage())
                        .append('\n');
            }
            current = current.getCause();
        }
        return messages.toString();
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        int depth = 0;
        while (current.getCause() != null
                && current.getCause() != current
                && depth++ < MAX_CAUSE_DEPTH) {
            current = current.getCause();
        }
        return current;
    }

    private static <T extends Throwable> T findCause(
            Throwable throwable,
            Class<T> type
    ) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth++ < MAX_CAUSE_DEPTH) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private static String stackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private static String newTraceId() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16)
                .toUpperCase(Locale.ROOT);
    }

    private static boolean containsAny(String text, String... candidates) {
        if (text == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record ErrorDescriptor(String code, String message) {
    }
}
