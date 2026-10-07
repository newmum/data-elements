{
  "properties": {},
  "id": "dwm_security_summary_01",
  "script": null,
  "groupId": "dwm_security_group_01",
  "name": "01.安全中心概览",
  "path": "/summary",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "description": "读取当前租户数据安全台账概览。"
}
================================
// 接口：安全中心概览
// 业务说明：按当前租户统计安全模型、规则、任务和策略；只查询租户库中的有效记录。
import tenantRuntime

var tenantId = tenantRuntime.id()

// 固定表名与分类，避免用户输入参与 SQL 标识符拼接。
var counts = db.selectOne("""
    SELECT
        (SELECT COUNT(1) FROM sec_classification_model WHERE tenant_id = #{tenantId} AND is_del = 0) AS models,
        (SELECT COUNT(1) FROM sec_classification_rule WHERE tenant_id = #{tenantId} AND is_del = 0) AS rules,
        (SELECT COUNT(1) FROM sec_scan_task WHERE tenant_id = #{tenantId} AND is_del = 0) AS scanTasks,
        (SELECT COUNT(1) FROM sec_scan_result WHERE tenant_id = #{tenantId} AND is_del = 0) AS scanResults,
        (SELECT COUNT(1) FROM sec_review_task WHERE tenant_id = #{tenantId} AND is_del = 0 AND review_status = 'PENDING') AS pendingReviews,
        (SELECT COUNT(1) FROM sec_policy WHERE tenant_id = #{tenantId} AND is_del = 0) AS policies,
        (SELECT COUNT(1) FROM sec_access_grant WHERE tenant_id = #{tenantId} AND is_del = 0) AS accessGrants,
        (SELECT COUNT(1) FROM sec_masking_policy WHERE tenant_id = #{tenantId} AND is_del = 0) AS maskingPolicies,
        (SELECT COUNT(1) FROM sec_encryption_task WHERE tenant_id = #{tenantId} AND is_del = 0) AS encryptionTasks,
        (SELECT COUNT(1) FROM sec_watermark_task WHERE tenant_id = #{tenantId} AND is_del = 0) AS watermarkTasks
""")

return {tenantId: tenantId, metrics: {
    '分类模型': counts.models,
    '识别规则': counts.rules,
    '扫描任务': counts.scanTasks,
    '识别结果': counts.scanResults,
    '待人工复核': counts.pendingReviews,
    '授权策略': counts.policies,
    '访问授权': counts.accessGrants,
    '脱敏策略': counts.maskingPolicies,
    '加解密申请': counts.encryptionTasks,
    '水印申请': counts.watermarkTasks
}}
