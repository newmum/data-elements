package com.linewell.dataelement.platform.tenant.protocol;

import cn.hutool.crypto.digest.DigestUtil;
import com.linewell.dataelement.magic.runtime.security.MagicAccessPolicy;
import com.linewell.dataelement.model.common.BizException;
import com.linewell.dataelement.platform.magic.StructuredTableReadMagicModule;
import com.linewell.dataelement.platform.tenant.api.IdentityRuntimeMagicModule;
import com.linewell.dataelement.platform.tenant.api.IdentityTransportMagicModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.web.security.GlobalInterceptor;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.ssssssss.magicapi.core.annotation.MagicModule;

/** Checks the receiver's protocol credentials before Magic opens its business transaction. */
@Component
@MagicModule("identityReceiver")
public class IdentityReceiverAccess {
    private static final String REJECTED_SIGNATURE = "接收签名、有效期或接入状态无效";
    private final StructuredTableReadMagicModule reads;
    private final IdentityRuntimeMagicModule runtime;
    private final IdentityTransportMagicModule transport;

    public IdentityReceiverAccess(StructuredTableReadMagicModule reads, IdentityRuntimeMagicModule runtime,
                                  IdentityTransportMagicModule transport) {
        this.reads = reads;
        this.runtime = runtime;
        this.transport = transport;
    }

    public Map<String, Object> authorize(Object body) throws Exception {
        if (!(body instanceof Map<?, ?> input) || !"IDAAS/2".equals(input.get("protocol")) || input.get("keyId") == null) {
            throw new BizException(400, "只接受IDAAS/2标准报文");
        }
        var keyId = input.get("keyId") instanceof String key ? key : null;
        var route = runtime.receiverRoute(keyId);
        if (!route.get("appId").equals(input.get("targetAppId")) || !route.get("instanceId").equals(input.get("receiverInstanceId"))) {
            throw new BizException(403, "接收应用或实例不一致");
        }
        String tenantId = route.get("tenantId");
        var binding = runtime.inTenant(tenantId, () -> readBinding(tenantId, keyId));
        if (!active(binding, route)) {
            throw new BizException(403, REJECTED_SIGNATURE);
        }

        long timestamp = credentialTimestamp(input);
        var nonce = (String) input.get("nonce");
        var payload = (String) input.get("payload");
        var signature = (String) input.get("signature");
        String cipher = (String) binding.get("secret_cipher");
        if (!transport.verify2(cipher, keyId, route.get("appId"), route.get("instanceId"), timestamp, nonce, payload, signature)) {
            throw new BizException(403, REJECTED_SIGNATURE);
        }
        var request = Map.of("keyId", keyId, "route", route, "nonce", nonce, "payload", payload);
        var proof = new BindingProof(keyId, route, timestamp, DigestUtil.sha256(cipher), text(binding, "local_permission_app_id"),
                text(binding, "version"), text(binding, "credential_version"));
        return Map.of("request", request, "binding", proof);
    }

    private Map<String, Object> readBinding(String tenantId, String keyId) {
        var query = reads.from("idaas_receiver_binding_t", "binding", tenantId);
        query.where(reads.equal(reads.column("binding", "key_id", "TEXT"), keyId));
        var rows = query.rows(Map.of(
                "tenant_id", reads.column("binding", "tenant_id", "TEXT"),
                "app_id", reads.column("binding", "app_id", "TEXT"),
                "instance_id", reads.column("binding", "instance_id", "TEXT"),
                "local_permission_app_id", reads.column("binding", "local_permission_app_id", "TEXT"),
                "secret_cipher", reads.column("binding", "secret_cipher", "TEXT"),
                "enabled", reads.column("binding", "enabled", "NUMBER"),
                "version", reads.column("binding", "version", "NUMBER"),
                "credential_version", reads.column("binding", "credential_version", "NUMBER")), 1);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public Map<?, ?> verifiedRequest() {
        return (Map<?, ?>) proof().get("request");
    }

    public boolean matchesLockedBinding(Map<?, ?> binding) {
        var expected = (BindingProof) proof().get("binding");
        return !TenantContext.usesControlDatabase() && expected.route().get("tenantId").equals(TenantContext.getTenantId())
                && active(binding, expected.route()) && expected.keyId().equals(binding.get("key_id"))
                && IdentityTransportMagicModule.acceptsTimestamp(expected.timestamp())
                && expected.permissionApp().equals(text(binding, "local_permission_app_id"))
                && expected.version().equals(text(binding, "version"))
                && expected.credentialVersion().equals(text(binding, "credential_version"))
                && MessageDigest.isEqual(expected.cipherDigest(), DigestUtil.sha256(text(binding, "secret_cipher")));
    }

    private static boolean active(Map<?, ?> binding, Map<String, String> route) {
        return binding != null && "1".equals(text(binding, "enabled")) && binding.get("secret_cipher") != null
                && route.get("tenantId").equals(binding.get("tenant_id")) && route.get("appId").equals(binding.get("app_id"))
                && route.get("instanceId").equals(binding.get("instance_id"));
    }

    private static long credentialTimestamp(Map<?, ?> input) {
        Object timestamp = input.get("timestamp");
        Object nonce = input.get("nonce"), payload = input.get("payload"), signature = input.get("signature");
        if (timestamp instanceof Number number && textOrNull(nonce) && textOrNull(payload) && textOrNull(signature)) {
            return new BigDecimal(number.toString()).longValueExact();
        }
        // Keep the existing Magic response for malformed argument types used by older clients.
        throw new IllegalArgumentException("在" + IdentityTransportMagicModule.class.getName()
                + "中找不到方法verify2(String,String,String,String," + typeOf(timestamp) + "," + typeOf(nonce)
                + "," + typeOf(payload) + "," + typeOf(signature) + ")");
    }

    private static boolean textOrNull(Object value) {
        return value == null || value instanceof String;
    }

    private static String typeOf(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }

    private static String text(Map<?, ?> row, String column) {
        return Objects.toString(row.get(column), "");
    }

    private static Map<?, ?> proof() {
        var attributes = RequestContextHolder.getRequestAttributes();
        return GlobalInterceptor.verifiedIdentityProof(attributes instanceof ServletRequestAttributes servlet
                ? servlet.getRequest() : null, MagicAccessPolicy.IDAAS_RECEIVER_SIGNATURE);
    }

    private record BindingProof(String keyId, Map<String, String> route, long timestamp, byte[] cipherDigest, String permissionApp,
                                String version, String credentialVersion) { }
}
