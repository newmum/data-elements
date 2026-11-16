{
  "properties": {},
  "id": "062a1e8f5b825797baf08ddc8654daa8",
  "script": null,
  "groupId": "b3202123a27e56198aed6211ec0f770f",
  "name": "06.批量停用中央人员",
  "createTime": 1791193400395,
  "updateTime": 1791193400395,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/disable",
  "description": "单次最多停用 100 人；在同一事务中逐 ID 校验身份域、版本及机构范围，批量写入人员与应用期望状态。",
  "parameters": [],
  "method": "POST",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
import db
import identityRuntime
import java.util.List
import java.util.Map
import '@/idaas/api-call' as apiCall
import '@/idaas/audit' as audit
import '@/idaas/directory-scope' as directoryScope
import '@/idaas/mutate' as mutate

// POST /idaas/users/disable。按身份域和管理范围批量停用人员，并标记其应用分配的目标状态。
return identityRuntime.control(() => apiCall(() => {
    var payload = body || {}
    var domain = payload.domain || 'workforce'
    var rights = directoryScope('users:write', domain)
    var entries = payload.entries
    if (!(entries instanceof List) || entries.isEmpty() || entries.size() > 100) {
        exit 400, '每次请选择 1 至 100 人'
    }
    var ids = []
    var editById = {}
    for (entry in entries) {
        if (!(entry instanceof Map) || entry.id == null) { exit 400, '人员标识重复或不正确' }
        var id = entry.id + ''
        if (!id.matches('[A-Za-z0-9_-]{1,64}') || editById.containsKey(id)) {
            exit 400, '人员标识重复或不正确'
        }
        ids.add(id)
        editById[id] = entry
    }
    var sql = db.normal()
    return mutate(rights.ctx, 'users:batch-disable', payload, () => {
        var fresh = directoryScope('users:write', domain)
        // 统一锁定顺序，之后在任何写入前核验整批版本、身份域和任职范围。
        var people = sql.select("""
            SELECT tid AS id, version FROM iam_subject_t
            WHERE identity_domain=#{domain} AND is_del=0
              AND tid IN <foreach collection="ids" item="id" open="(" close=")" separator=",">#{id}</foreach>
            ORDER BY tid FOR UPDATE
        """)
        if (people.size() != ids.size()) { exit 404, '人员不存在或不属于当前身份域' }
        var appointments = sql.select("""
            SELECT subject_id, org_id FROM iam_appointment_t
            WHERE subject_id IN <foreach collection="ids" item="id" open="(" close=")" separator=",">#{id}</foreach>
              AND is_del=0 AND status='ACTIVE' AND valid_from<=utc_timestamp(6)
              AND (valid_until IS NULL OR valid_until>utc_timestamp(6))
        """)
        var organizations = {}
        for (appointment in appointments) {
            var subject = appointment.subject_id
            if (organizations[subject] == null) { organizations[subject] = [] }
            organizations[subject].add(appointment.org_id)
        }
        for (person in people) {
            var edit = editById[person.id]
            if (edit == null || person.version != edit.version) { exit 409, '人员版本已变更，请刷新后重试' }
            var orgs = organizations[person.id] || []
            if (!fresh.all && (orgs.isEmpty() || orgs.filter(id => !fresh.orgIds.contains(id)).size() > 0)) {
                exit 403, '选中的人员超出当前管理范围'
            }
        }
        sql.update("""
            UPDATE iam_subject_t SET status='DISABLED', version=version+1,
                updated_by=#{fresh.ctx.userId}, updated_time=utc_timestamp(6)
            WHERE identity_domain=#{domain} AND is_del=0
              AND tid IN <foreach collection="ids" item="id" open="(" close=")" separator=",">#{id}</foreach>
        """)
        sql.update("""
            UPDATE iam_subject_app_t SET desired_enabled=0, desired_version=desired_version+1,
                version=version+1, updated_time=utc_timestamp(6)
            WHERE subject_id IN <foreach collection="ids" item="id" open="(" close=")" separator=",">#{id}</foreach>
        """)
        audit(fresh.ctx, 'SUCCESS', 'users:batch-disable', null, null, {count: ids.size()})
        return {count: ids.size()}
    })
}))
