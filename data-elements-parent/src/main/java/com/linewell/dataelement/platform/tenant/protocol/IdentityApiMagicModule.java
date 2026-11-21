package com.linewell.dataelement.platform.tenant.protocol;

import com.linewell.dataelement.magic.runtime.security.MagicAccessPolicy;
import com.linewell.dataelement.platform.web.security.GlobalInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.ssssssss.magicapi.core.annotation.MagicModule;

/** Supplies JWT verification, request network primitives and the verified API caller. */
@Component
@MagicModule("identityApi")
public class IdentityApiMagicModule {
    private static final Logger LOG = LoggerFactory.getLogger(IdentityApiMagicModule.class);

    private final ObjectProvider<JwtDecoder> decoders;
    private final IdentityProtocolBridge bridge;
    private final StringRedisTemplate redis;

    public IdentityApiMagicModule(ObjectProvider<JwtDecoder> decoders, IdentityProtocolBridge bridge,
                                  StringRedisTemplate redis) {
        this.decoders = decoders;
        this.bridge = bridge;
        this.redis = redis;
    }

    public String remoteAddress() {
        return currentRequest().getRemoteAddr();
    }

    public boolean matchesAddress(String rule, String address) {
        if (rule == null || !rule.matches("[0-9a-fA-F:.]+(?:/[0-9]{1,3})?")) {
            throw new IllegalArgumentException("请填写IP或CIDR，不接受主机名");
        }
        return new IpAddressMatcher(rule).matches(address);
    }

    public boolean withinRate(String bucket, int limit) {
        if (limit < 1 || limit > 100000) {
            throw new IllegalArgumentException("请求配额不正确");
        }
        var script = new DefaultRedisScript<>("""
                local n=redis.call('INCR',KEYS[1]);if n==1 then redis.call('EXPIRE',KEYS[1],60) end;return n
                """, Long.class);
        Long count = redis.execute(script, List.of("idaas:api:rate:" + bucket));
        return count != null && count <= limit;
    }

    public Map<?, ?> verifiedCaller() {
        return GlobalInterceptor.verifiedIdentityProof(currentRequest(), MagicAccessPolicy.IDAAS_API_BEARER);
    }

    public Map<String, Object> authorize(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new AccessDeniedException("请使用应用访问令牌");
        }
        String stage = "signature";
        try {
            String raw = authorization.substring(7);
            var decoder = decoders.getIfAvailable();
            if (decoder == null) {
                throw new IllegalStateException("统一身份认证协议未启用");
            }
            var jwt = decoder.decode(raw);
            stage = "audience";
            String[] applications = request.getParameterValues("appId");
            if (applications == null || applications.length != 1 || !jwt.getAudience().equals(List.of(applications[0]))) {
                throw new IllegalArgumentException("令牌未限定当前应用");
            }
            stage = "session";
            var state = bridge.store(Map.of("operation", "find", "token", raw, "type", "access_token"));
            if (state == null || state.get("state") == null) {
                throw new IllegalArgumentException("访问令牌已撤销或授权失效");
            }
            stage = "current-permission";
            boolean machine = "CLIENT".equals(jwt.getClaimAsString("principal_kind"));
            String clientId = jwt.getClaimAsString("iam_client_id");
            var input = new LinkedHashMap<String, Object>();
            input.put("operation", "api");
            input.put("clientId", clientId);
            input.put("principalName", machine ? clientId : jwt.getClaimAsString("auth_account_id"));
            input.put("machine", machine);
            input.put("appId", applications[0]);
            input.put("path", request.getRequestURI());
            input.put("method", request.getMethod());
            var result = bridge.access(input);
            if (result == null || !Boolean.TRUE.equals(result.get("allowed"))) {
                throw new IllegalArgumentException("当前主体没有此接口的有效授权");
            }
            return result;
        } catch (RuntimeException error) {
            LOG.warn("IDAAS_API_REJECT stage={} cause={}", stage, error.getClass().getSimpleName());
            throw new AccessDeniedException("访问令牌或当前接口授权无效");
        }
    }

    private static HttpServletRequest currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servlet)) {
            throw new IllegalStateException("接口请求上下文缺失");
        }
        return servlet.getRequest();
    }
}
