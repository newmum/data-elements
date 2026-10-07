{
  "properties": {},
  "id": "dwm_security_save_01",
  "script": null,
  "groupId": "dwm_security_group_01",
  "name": "03.保存安全台账",
  "path": "/records/save",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "description": "新增或更新当前租户安全台账；后端白名单过滤字段。"
}
================================
// 接口：保存安全台账
// 业务说明：按记录类型的字段白名单校验并保存台账；数据变更与审计日志在同一事务内完成。
import tenantRuntime
import cn.dev33.satoken.stp.StpUtil
import java.util.UUID
import java.util.LinkedHashMap

var tenantId = tenantRuntime.id()
var recordType = body?.recordType == null ? '' : ('' + body.recordType).trim()
if (recordType == '') { exit 400, '记录类型不能为空' }

// 可写字段和新增必填字段集中写在此接口；新增类型时可直接在 Magic 编辑器调整。
var definitions = {
    sec_classification_model: {fields:'model_code,model_name,model_type,description,model_status', required:'model_code,model_name,model_type,model_status'},
    sec_classification_rule: {fields:'rule_code,rule_name,target_type,field_pattern,content_pattern,sensitivity_level,rule_status', required:'rule_code,rule_name,target_type,field_pattern,sensitivity_level,rule_status'},
    sec_scan_task: {fields:'task_code,task_name,model_id,target_scope,task_status,precheck_summary,executor_reference', required:'task_code,task_name,target_scope,task_status'},
    sec_scan_result: {fields:'task_id,asset_type,asset_id,field_name,rule_id,sensitivity_level,match_summary,result_status', required:'task_id,asset_type,asset_id,field_name,sensitivity_level,result_status'},
    sec_review_task: {fields:'scan_result_id,review_status,review_comment', required:'scan_result_id,review_status'},
    sec_policy: {fields:'policy_code,policy_name,policy_type,asset_type,asset_id,policy_content,policy_status', required:'policy_code,policy_name,policy_type,policy_content,policy_status'},
    sec_access_grant: {fields:'policy_id,asset_type,asset_id,subject_type,subject_id,access_scope,grant_status,expire_time', required:'asset_type,asset_id,subject_type,subject_id,access_scope,grant_status'},
    sec_masking_policy: {fields:'policy_code,policy_name,asset_type,asset_id,field_pattern,masking_algorithm,masking_parameter,policy_status', required:'policy_code,policy_name,masking_algorithm,policy_status'},
    sec_key_reference: {fields:'reference_code,reference_name,key_type,algorithm_name,provider_name,reference_status,rotate_time', required:'reference_code,reference_name,key_type,reference_status'},
    sec_encryption_task: {fields:'task_code,task_name,key_reference_id,asset_type,asset_id,field_scope,encryption_algorithm,task_status,request_summary', required:'task_code,task_name,encryption_algorithm,task_status'},
    sec_watermark_policy: {fields:'policy_code,policy_name,watermark_type,trace_template,file_type_scope,policy_status', required:'policy_code,policy_name,watermark_type,trace_template,policy_status'},
    sec_watermark_task: {fields:'task_code,task_name,watermark_policy_id,asset_type,asset_id,file_name,trace_reference,task_status,request_summary', required:'task_code,task_name,task_status'}
}
var definition = definitions[recordType]
if (definition == null) { exit 400, '不支持的数据安全记录类型' }

// 只复制当前类型允许的字段，忽略请求中伪造的租户和系统字段。
var fields = body?.fields == null ? {} : body.fields
var accepted = new LinkedHashMap()
for (name in definition.fields.split(',')) {
    if (fields[name] != null) { accepted.put(name, fields[name]) }
}
var recordId = body?.recordId == null ? '' : ('' + body.recordId).trim()
if (recordId == '' && fields.tid != null) { recordId = ('' + fields.tid).trim() }
var creating = recordId == ''
if (creating) {
    for (name in definition.required.split(',')) {
        if (accepted[name] == null || ('' + accepted[name]).trim() == '') { exit 400, '请填写' + name }
    }
} else if (accepted.isEmpty()) {
    exit 400, '请至少提交一个可修改字段'
}

var actor = StpUtil.isLogin() ? '' + StpUtil.getLoginId() : 'system'
return db.transaction(() => {
    if (creating) {
        recordId = UUID.randomUUID().toString().replace('-', '')
        accepted.put('tid', recordId)
        accepted.put('tenant_id', tenantId)
        accepted.put('is_del', 0)
        db.table(recordType).primary('tid').insert(accepted)
    } else {
        var changed = db.table(recordType).where().eq('tenant_id', tenantId).eq('tid', recordId).eq('is_del', 0).update(accepted)
        if (changed == 0) { exit 400, '记录不存在或不属于当前租户' }
    }

    // 台账变更和审计日志必须一起提交，便于追溯谁修改了什么类型的记录。
    db.table('sec_operation_log').primary('tid').insert({
        tid: UUID.randomUUID().toString().replace('-', ''), tenant_id: tenantId,
        record_type: recordType, record_id: recordId,
        action_code: creating ? 'CREATE' : 'UPDATE', outcome: 'SUCCESS',
        detail_summary: creating ? '已创建安全台账记录' : '已更新安全台账记录', operator_id: actor
    })
    return db.table(recordType).where().eq('tenant_id', tenantId).eq('tid', recordId).eq('is_del', 0).selectOne()
})
