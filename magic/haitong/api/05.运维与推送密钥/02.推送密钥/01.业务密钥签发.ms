{
  "id": "haitong-credential-issue",
  "groupId": "haitong-credential-api",
  "name": "01.业务密钥签发",
  "path": "/issue",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "平台服务或管理员签发或显式轮换推送密钥。锁定有效记录后提交摘要，首次签发或轮换返回一次明文，重复请求仅返回标识。"
}
================================
import '@/haitong/common' as common
import db
import java.nio.charset.StandardCharsets
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.ResponseEntity
import request

// POST /internal/push-credentials/issue 由平台服务或管理员签发或显式轮换推送密钥。
// 已有密钥重复获取仅返回标识。首次签发或轮换时返回一次明文，库中保存 SHA-256 摘要。

var context = common()
var req = request.get().getRequest()
var caller = context.caller(req)
var media = context.media
media(req, 'application/json')
var input = context.http.object(req, 65536)
if (!caller.admin()) {
    return ResponseEntity.status(403).header('Cache-Control', 'no-store')
        .body({code: 'FORBIDDEN', message: '仅平台服务或管理员可签发密钥'})
}
var clientId = context.text(input.clientId).trim()
if (!clientId.matches('push-[A-Za-z0-9_-]{1,123}')) {
    return ResponseEntity.status(400).header('Cache-Control', 'no-store')
        .body({code: 'INVALID_REQUEST', message: 'clientId必须为数据源推送调用方编号'})
}
var rotate = input.rotate == true
var store = context.bean('pushCredentialStore')
var result
try {
    result = context.tx.execute(status => {
        // 锁定当前有效行。锁与结构化写入使用同一事务，避免轮换覆盖并发操作。
        var existing = store.lockEnabledClient(clientId)
        if (existing && !rotate) {
            return {clientId: clientId, revealed: false}
        }
        var key = context.http.randomKey()
        var digest = context.hash(key.getBytes(StandardCharsets.UTF_8))
        var now = context.now()
        if (existing) {
            db.table('ht_push_api_client').where()
                .eq('client_id', clientId).eq('is_enable', 1).eq('is_del', 0)
                .update({api_key_sha256: digest, updated_at: now})
        } else {
            db.table('ht_push_api_client').insert({
                client_id: clientId, api_key_sha256: digest, is_enable: 1, is_del: 0,
                created_at: now, updated_at: now
            })
        }
        return {clientId: clientId, revealed: true, apiKey: key}
    })
} catch (failure) {
    // 并发首发命中唯一键时沿用幂等结果，另一请求生成的密钥只返回给其调用方。
    var cause = failure
    while (cause != null && !(cause instanceof DuplicateKeyException)) {
        cause = cause.getCause()
    }
    if (cause == null) {
        throw failure
    }
    result = {clientId: clientId, revealed: false}
}
return context.http.reply(200, {code: 0, message: 'success', data: result})
