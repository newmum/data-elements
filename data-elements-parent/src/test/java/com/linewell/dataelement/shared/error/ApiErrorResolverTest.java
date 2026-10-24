package com.linewell.dataelement.shared.error;

import com.linewell.dataelement.platform.tenant.domain.TenantAccessException;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLException;
import org.apache.http.HttpHost;
import org.apache.http.ProtocolVersion;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicRequestLine;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiErrorResolverTest {

    @Test
    void exposesOracleAuthenticationMessageAndKeepsFullStack() {
        SQLException oracle = new SQLException(
                "ORA-01017: invalid username/password; logon denied"
        );
        RuntimeException wrapper = new RuntimeException("连接池初始化失败", oracle);

        ApiErrorDetails error = ApiErrorResolver.resolve(wrapper);

        assertThat(error.code()).isEqualTo("DB-AUTH-003");
        assertThat(error.message())
                .isEqualTo("ORA-01017: invalid username/password; logon denied");
        assertThat(error.detail())
                .contains("java.sql.SQLException")
                .contains("ORA-01017");
        assertThat(error.traceId()).hasSize(16);
    }

    @Test
    void classifiesNetworkFailureWithoutLosingCause() {
        RuntimeException wrapper = new RuntimeException(
                "连接失败",
                new ConnectException("Connection refused")
        );

        ApiErrorDetails error = ApiErrorResolver.resolve(wrapper);

        assertThat(error.code()).isEqualTo("DB-CONNECTION-001");
        assertThat(error.message()).isEqualTo("Connection refused");
        assertThat(error.detail()).contains("ConnectException");
    }

    @Test
    void doesNotMisclassifyOrdinaryMagicExceptionAsDatabaseError() {
        IllegalStateException exception = new IllegalStateException(
                "MAGIC-SCRIPT-001 resource invocation failed"
        );

        ApiErrorDetails error = ApiErrorResolver.resolve(exception);

        assertThat(error.code()).isEqualTo("MAGIC-SCRIPT-001");
        assertThat(error.message()).contains("接口脚本执行失败");
    }

    @Test
    void preservesTenantErrorThroughMagicWrapper() {
        RuntimeException wrapper = new RuntimeException(
                "Magic invocation failed",
                new TenantAccessException(
                        "RECONCILE-NOT-FOUND",
                        "对账数据不存在或无权访问"
                )
        );

        ApiErrorDetails error = ApiErrorResolver.resolve(wrapper);

        assertThat(error.code()).isEqualTo("RECONCILE-NOT-FOUND");
        assertThat(error.message()).isEqualTo("对账数据不存在或无权访问");
        assertThat(error.detail()).contains("TenantAccessException");
    }

    @Test
    void identifiesSearchConnectionResetThroughMagicWrapper() {
        var cause = searchFailure(new SocketException("Connection reset"));
        var wrapper = new RuntimeException("MagicScriptException at Row:50~63", cause);

        ApiErrorDetails error = ApiErrorResolver.resolve(wrapper);

        assertThat(error.code()).isEqualTo("SEARCH-CONNECTION-001");
        assertThat(error.message()).contains("全文检索服务", "连接已中断", "稍后重试");
        assertThat(error.detail()).contains("Connection reset", "ElasticSearchIndex.search");
        assertThat(error.traceId()).hasSize(16);
    }

    @Test
    void distinguishesSearchTimeoutAndUnresolvableHostFromDatabaseFailures() {
        var timeout = ApiErrorResolver.resolve(searchFailure(new SocketTimeoutException("Read timed out")));
        var dns = ApiErrorResolver.resolve(searchFailure(new UnknownHostException("search.internal")));

        assertThat(timeout.code()).isEqualTo("SEARCH-TIMEOUT-002");
        assertThat(timeout.message()).contains("全文检索服务连接或查询超时");
        assertThat(dns.code()).isEqualTo("SEARCH-CONNECTION-001");
    }

    @Test
    void distinguishesSearchHttpFailuresWithoutExposingRawAuthenticationResponse() throws Exception {
        assertThat(ApiErrorResolver.resolve(searchResponse(401)).code()).isEqualTo("SEARCH-AUTH-003");
        assertThat(ApiErrorResolver.resolve(searchResponse(403)).message()).contains("认证或授权失败");
        assertThat(ApiErrorResolver.resolve(searchResponse(503)).code()).isEqualTo("SEARCH-UNAVAILABLE-001");
        assertThat(ApiErrorResolver.resolve(searchResponse(429)).message()).contains("负载");
        assertThat(ApiErrorResolver.resolve(searchResponse(400)).code()).isEqualTo("SEARCH-QUERY-004");
    }

    @Test
    void doesNotInferSearchFailureFromScriptTextOrOrdinaryDatabaseStack() {
        var database = new SQLException("Connection reset", new SocketException("Connection reset"));
        var wrapper = new RuntimeException("MagicScriptException tenantEs.index(...) at Row:50", database);
        assertThat(ApiErrorResolver.resolve(wrapper).code()).isEqualTo("DB-SQL-004");

        var argument = searchFailure(new IllegalArgumentException("indexName 不能为空"));
        assertThat(ApiErrorResolver.resolve(argument).code()).isEqualTo("API-PARAM-001");
    }

    private <T extends Throwable> T searchFailure(T cause) {
        cause.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("org.elasticsearch.client.RestClient", "performRequest", "RestClient.java", 304),
                new StackTraceElement("org.ssssssss.magicapi.elasticsearch.ElasticSearchIndex", "search", "ElasticSearchIndex.java", 70)
        });
        return cause;
    }

    private ResponseException searchResponse(int status) throws Exception {
        var response = mock(Response.class);
        when(response.getRequestLine()).thenReturn(new BasicRequestLine("POST", "/assets/_search", new ProtocolVersion("HTTP", 1, 1)));
        when(response.getHost()).thenReturn(new HttpHost("search.internal", 9200));
        when(response.getStatusLine()).thenReturn(new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1), status, "Search failure").getStatusLine());
        return new ResponseException(response);
    }
}
