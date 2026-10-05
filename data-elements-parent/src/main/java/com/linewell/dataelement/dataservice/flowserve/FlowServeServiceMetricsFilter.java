package com.linewell.dataelement.dataservice.flowserve;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 记录云梯已发布数据服务的调用结果，记录失败不得影响业务响应。 */
@Component
@Order(20)
public class FlowServeServiceMetricsFilter extends OncePerRequestFilter {
    private static final String PUBLISHED_PREFIX = "/dws/flowserve/published/";
    private final JdbcTemplate jdbc;

    public FlowServeServiceMetricsFilter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PUBLISHED_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long started = System.currentTimeMillis();
        String error = null;
        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException exception) {
            error = exception.getMessage();
            throw exception;
        } finally {
            record(
                    request,
                    response.getStatus(),
                    error,
                    System.currentTimeMillis() - started);
        }
    }

    private void record(
            HttpServletRequest request, int status, String error, long duration) {
        try {
            ServiceRef service = jdbc.query(
                    """
                    select pr.flow_id, f.name, pr.tenant_id
                    from fs_publish_record pr
                    join fs_flow f
                      on f.id = pr.flow_id and f.tenant_id = pr.tenant_id
                    where pr.url = ? and pr.status = 'published'
                    order by pr.publish_time desc limit 1
                    """,
                    result -> result.next()
                            ? new ServiceRef(
                                    result.getString(1),
                                    result.getString(2),
                                    result.getString(3))
                            : new ServiceRef(null, null, null),
                    request.getRequestURL().toString());
            jdbc.update(
                    """
                    insert into fs_call_log(
                        flow_id, service_name, method, path, status, success,
                        duration_ms, error_message, tenant_id)
                    values(?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    service.flowId(),
                    service.name(),
                    request.getMethod(),
                    request.getRequestURI(),
                    status,
                    status < 400 && error == null ? 1 : 0,
                    duration,
                    error,
                    service.tenantId());
        } catch (Exception ignored) {
            // 监控写入是旁路能力，不得改变被调用服务的行为。
        }
    }

    private record ServiceRef(String flowId, String name, String tenantId) {}
}
