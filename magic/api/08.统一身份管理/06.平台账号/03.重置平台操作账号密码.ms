{
  "properties": {},
  "id": "2049857ef4b552d5a47490851cb25d97",
  "script": null,
  "groupId": "64e411148c13506897632e1f5bbe2c15",
  "name": "03.重置平台操作账号密码",
  "createTime": 1790612165860,
  "updateTime": 1791205192024,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/reset",
  "description": "核验平台账号管理范围与密码策略，更新独立安全凭据并提升安全版本，使旧会话失效并记录审计。",
  "parameters": [],
  "method": "POST",
  "options": [
    {
      "name": "access",
      "value": "IDAAS_PLATFORM_SESSION"
    }
  ],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
import cn.hutool.core.util.IdUtil
import db
import iamSecurity
import identityRuntime
import '@/idaas/api-call' as apiCall
import '@/idaas/audit' as audit
import '@/idaas/guard' as guard
import '@/idaas/mutate' as mutate
import '@/idaas/password-policy' as passwordPolicy

// POST /idaas/operators/reset。重置可管理的平台操作账号密码，废止旧凭据和旧会话。
return identityRuntime.control(() => apiCall(() => {
    var payload = body || {}
    var input = payload.record || payload
    var context = guard('operators:write', null, 'workforce')
    var id = (input.id || '') + ''
    var sql = db.normal()
    return mutate(context, 'operators:reset-password', payload, () => {
        // 锁定账号后核对版本，避免并发重置同时使用相同凭据修订号。
        var account = sql.selectOne("""
            SELECT version FROM iam_operator_t WHERE tid=#{id} AND is_del=0 FOR UPDATE
        """)
        if (account == null) { exit 404, '平台账号不存在' }
        if (input.version != account.version) { exit 409, '账号已被修改，请刷新' }
        if (id == context.userId) { exit 400, '请在个人中心修改自己的密码' }
        passwordPolicy(input.password)
        var credentialId = IdUtil.fastSimpleUUID()
        var encoded = iamSecurity.hash(input.password)
        var current = sql.selectOne("""
            SELECT COALESCE(MAX(revision), 0)+1 AS revision
            FROM iam_credential_t WHERE owner_kind='OPERATOR' AND owner_id=#{id} AND credential_type='PASSWORD'
        """)
        sql.update("""
            UPDATE iam_credential_t SET status='RETIRED', updated_time=utc_timestamp(6)
            WHERE owner_kind='OPERATOR' AND owner_id=#{id} AND credential_type='PASSWORD' AND status='ACTIVE'
        """)
        sql.update("""
            INSERT INTO iam_credential_t (tid, owner_kind, owner_id, credential_type, slot, revision,
                                          algorithm, secret_hash, status)
            VALUES (#{credentialId}, 'OPERATOR', #{id}, 'PASSWORD', 'primary', #{current.revision},
                    'PBKDF2_SHA256', #{encoded}, 'ACTIVE')
        """)
        sql.update("""
            UPDATE iam_operator_t SET must_change_password=1, version=version+1, updated_time=utc_timestamp(6)
            WHERE tid=#{id} AND is_del=0
        """)
        sql.update("""
            UPDATE iam_account_security_t
            SET security_version=security_version+1, failed_count=0, locked_until=null, password_changed_time=utc_timestamp(6)
            WHERE principal_kind='OPERATOR' AND principal_id=#{id}
        """)
        audit(context, 'SUCCESS', 'operators:reset-password', id, null, {id: id})
        return {id: id}
    })
}))
