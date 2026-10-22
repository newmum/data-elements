package com.haitong.magic;

import com.haitong.api.AuthenticationFilter;
import com.haitong.api.RelayController;
import com.haitong.core.RelayException;
import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.springframework.stereotype.Component;

/**
 * Thin, safe bridge for Magic API operational endpoints.  It intentionally
 * delegates to RelayController so that Magic scripts do not reimplement
 * authentication, idempotency, registration validation or durable delivery.
 */
@Component("pushOperations")
@MagicModule("pushOperations")
public final class PushOperationsApi {
    private final RelayController relay;

    public PushOperationsApi(RelayController relay) { this.relay=relay; }

    public Map<String,Object> dataPush(Map<String,Object> body,String idempotencyKey,HttpServletRequest request) throws IOException {
        String key=requiredKey(idempotencyKey);
        return relay.magicDataPush(key,request,body).getBody();
    }

    public Map<String,Object> messageStatus(String messageId,HttpServletRequest request) {
        String id=messageId==null?"":messageId.trim();
        if(id.isBlank() || id.length()>128) throw RelayException.invalid("messageId 必须为 1 到 128 字符");
        return relay.messageStatus(id,AuthenticationFilter.identity(request));
    }

    private static String requiredKey(String value) {
        String key=value==null?"":value.trim();
        if(key.isBlank()) throw RelayException.invalid("缺少 Idempotency-Key 请求头");
        return key;
    }
}
