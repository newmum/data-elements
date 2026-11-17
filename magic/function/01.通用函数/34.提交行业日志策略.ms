{
  "id": "log_tenant_policy_save_v1",
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "34.提交行业日志策略",
  "path": "/logpolicysave",
  "parameters": [
    {
      "name": "submitted",
      "required": false,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": [],
  "description": "策略 PUT 和历史 POST 共用类别、留存及采样校验，事务中保存所选版本并写操作审计。"
}
================================
import '@/common/loggrants' as grants
import cn.dev33.satoken.stp.StpUtil
import cn.hutool.extra.spring.SpringUtil
import com.linewell.dataelement.platform.audit.AuditTrace
import com.linewell.dataelement.platform.audit.LogTimes
import java.lang.Double
import java.lang.Long
import java.lang.SecurityException
import java.math.BigDecimal
import java.time.Instant
import java.util.Map
import auditEvents
import tenantRuntime

// PUT 与历史 POST 共享同一版本校验和事务。行业、账号及权限都来自当前可信会话。
var tenant = tenantRuntime.id()
var actor = StpUtil.getLoginIdAsString()
var application = StpUtil.getTokenSession().get('appId')
var mode = grants(actor, application == null ? '' : application + '', 'policies').modes.get('policies')
if (mode == null) { throw new SecurityException('没有此日志模块的访问权限') }
if (mode != 'ALL') { throw new SecurityException('此操作需要日志模块的完整范围授权') }
if (!(submitted instanceof Map)) { throw new IllegalArgumentException('请求体必须是 JSON 对象') }

var category = submitted.category == null ? '' : submitted.category + ''
if (category.length() > 24) { throw new IllegalArgumentException('日志查询内容过长') }
if (!['AUTH', 'OPERATION', 'REQUEST', 'SECURITY', 'TASK'].contains(category)) {
    throw new IllegalArgumentException('日志类别无效')
}
var number = (name, fallback, minimum, maximum) => {
    var value = submitted.get(name)
    try {
        var parsed = value == null ? fallback : Long.parseLong(value + '')
        if (parsed >= minimum && parsed <= maximum) { return parsed }
    } catch (error) { /* 沿用客户端已识别的参数错误。 */ }
    throw new IllegalArgumentException('分页参数无效')
}
var hot = number('hotDays', 180, category == 'REQUEST' ? 1 : 180, 3650)
var retention = number('retentionDays', 365, hot, 3650)
var version = number('version', 0, 0, Long.MAX_VALUE)
var sample = 1
try { sample = Double.parseDouble(submitted.getOrDefault('sampleRate', 1) + '') } catch (error) {
    throw new IllegalArgumentException('采样比例无效')
}
if (!Double.isFinite(sample) || sample <= 0 || sample > 1 || (category != 'REQUEST' && sample != 1)) {
    throw new IllegalArgumentException('强制审计类别必须全量记录')
}

var identity = SpringUtil.getBean('auditRecorder').current()
var now = LogTimes.utc(Instant.now())
var settings = {hot_days: hot, total_retention_days: retention, sample_rate: sample,
    actor_name: identity.name(), actor_account: identity.account(), actor_org_name: identity.orgName(),
    updated_by: actor, updated_time: now}
db.transaction(() => {
    if (version == 0) {
        // 唯一键约束保护并发首次创建，已有类别不会被版本 0 覆盖。
        settings.putAll({tid: AuditTrace.random(), scope_kind: 'TENANT', scope_id: tenant, tenant_id: tenant,
            identity_domain: 'global', category: category, created_time: now, version: 1,
            detail_limit_bytes: 32768, enabled: 1, archive_enabled: 1})
        db.table('log_policy_t').primary('tid').insert(settings)
    } else {
        settings.put('version', BigDecimal.valueOf(version).add(BigDecimal.ONE))
        var changed = db.table('log_policy_t').where().eq('tenant_id', tenant).eq('scope_kind', 'TENANT')
            .eq('scope_id', tenant).eq('identity_domain', 'global').eq('category', category).eq('version', version)
            .update(settings, true)
        if (changed != 1) { throw new IllegalStateException('策略已被其他人修改，请刷新后重试') }
    }
    var event = auditEvents.prepare('OPERATION', 'LOG_POLICY_UPDATED', 'SUCCESS', 'LOG_POLICY', category, category,
        {category: category, hotDays: hot, retentionDays: retention})
    db.table(event.table).primary('tid').insert(event.row)
})
// 只在策略与审计均已提交后失效该行业的采样缓存。
SpringUtil.getBean('auditPolicyCache').invalidate(tenant, 'global')
return {saved: true}
