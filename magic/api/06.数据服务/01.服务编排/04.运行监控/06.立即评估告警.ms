{
  "properties" : { },
  "id" : "e6a6c819e1ea60c97feb5e4988d49bab",
  "script" : null,
  "groupId" : "252018678f71bd2962b4ed7e5c2b6b56",
  "name" : "06.立即评估告警",
  "createTime" : 1784826968340,
  "updateTime" : 1791111417322,
  "lock" : null,
  "createBy" : "flowserve",
  "updateBy" : null,
  "path" : "/monitor/alert-rules/evaluate",
  "method" : "POST",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : "{}",
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : null,
  "description" : "立即按当前近五分钟错误率和最大延迟评估启用规则。示例：POST /dws/flowserve/monitor/alert-rules/evaluate；涉及 fs_alert_rule、fs_alert_event 等业务表；执行数据库写入并返回处理结果。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
// 接口：06.立即评估告警
// 路由：POST /dws/flowserve/monitor/alert-rules/evaluate
// 业务说明：一次计算近五分钟的错误率和最大延迟，再评估当前租户全部启用规则。
import tenantRuntime
import java.util.Map

var tenantId = tenantRuntime.id()
var since5Minutes = new Date(new Date().getTime() - 5 * 60 * 1000)
var dryRun = body?.dryRun == true
var metricValue = (row, key) => {
    if (row == null) { return 0 }
    var value = row instanceof Map ? row.get(key) : row[key]
    if (value == null) {
        var upperKey = key.toUpperCase()
        value = row instanceof Map ? row.get(upperKey) : row[upperKey]
    }
    return value == null ? 0 : value
}

// 规则数可变，但指标来自同一五分钟窗口：只执行一次聚合查询。
var metrics = db.selectOne("""
    select case when count(*) = 0 then 0
                else sum(case when success = 0 then 1 else 0 end) * 100.0 / count(*)
           end as errorRate,
           coalesce(max(duration_ms), 0) as maxDuration
      from fs_call_log
     where tenant_id = #{tenantId}
       and created_at >= #{since5Minutes}
""")
var errorRate = metricValue(metrics, 'errorRate')
var maxDuration = metricValue(metrics, 'maxDuration')
var rules = db.table('fs_alert_rule').where().eq('tenant_id', tenantId).eq('enabled', 1).select()
for (rule in rules) {
    var actual = rule.metric == 'errorRate' ? errorRate : maxDuration
    if (actual >= rule.thresholdValue && !dryRun) {
        db.table('fs_alert_event').insert({
            tenant_id: tenantId,
            rule_id: rule.id,
            rule_name: rule.name,
            metric: rule.metric,
            actual_value: actual,
            threshold_value: rule.thresholdValue,
            message: rule.name + ' 当前值 ' + actual + ' 已达到阈值 ' + rule.thresholdValue,
            notify_target: rule.notifyTarget,
            status: 'triggered'
        })
    }
}
return db.select("""
    select id, rule_id rule_id, rule_name rule_name, metric, actual_value actual_value,
           threshold_value threshold_value, message, notify_target notify_target, status,
           date_format(triggered_at, '%Y-%m-%d %H:%i:%s') triggered_at
      from fs_alert_event
     where tenant_id = #{tenantId}
     order by triggered_at desc
     limit 20
""")
