package com.linewell.dataelement.shared.error;

import com.linewell.dataelement.platform.tenant.domain.TenantAccessException;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

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
}
