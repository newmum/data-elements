{
  "properties": {},
  "id": "fd9b1a6ef9195f87a4f9461c37272e11",
  "script": null,
  "groupId": "5cc0c0d466b0571a9fe288ca74f0c356",
  "name": "31.平台账号姓名唯一来源",
  "createTime": 1791205192024,
  "updateTime": 1791205192024,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/operator-name",
  "description": "关联人员只更新中央人员姓名；独立平台账号只更新平台显示名，保留版本与目录范围校验。",
  "parameters": [
    {
      "name": "operatorId",
      "value": null,
      "description": "平台账号 ID",
      "required": false,
      "dataType": "Object",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "subjectId",
      "value": null,
      "description": "关联人员 ID，可空",
      "required": false,
      "dataType": "Object",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "displayName",
      "value": null,
      "description": "目标姓名",
      "required": false,
      "dataType": "Object",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "subjectVersion",
      "value": null,
      "description": "提交时的人员档案版本",
      "required": false,
      "dataType": "Object",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "actorId",
      "value": null,
      "description": "当前操作人 ID",
      "required": false,
      "dataType": "Object",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "selfEdit",
      "value": null,
      "description": "是否本人维护个人资料",
      "required": false,
      "dataType": "Object",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ],
  "returnType": null,
  "mappingPath": null
}
================================
// 共享函数：平台账号显示名写入唯一人员来源。
// 关联人员的姓名只写 iam_subject_t；独立平台账号只写 iam_operator_t。
// 调用者必须在 mutate 事务内校验平台账号版本与 operators:write 或本人身份。
import db;
import '@/idaas/directory-scope' as directoryScope;

var sql=db.normal();
var value=(displayName||'').trim();
if(value=='' || value.length()>100) {
    exit 400,'姓名不正确';
}
if(subjectId==null) {
    sql.update("""
update iam_operator_t
set display_name=#{value}
where tid=#{operatorId} and subject_id is null and is_del=0
""");
    return {name:value};
}
var subject=sql.selectOne("""
select tid as id,name,version
from iam_subject_t
where tid=#{subjectId} and identity_domain='workforce' and is_del=0
for update
""");
if(subject==null) {
    exit 409,'关联人员档案不存在或身份域已变更';
}
if(subjectVersion==null || subject.version!=subjectVersion) {
    exit 409,'人员姓名已变更，请刷新后再保存';
}
if(subject.name==value) {
    return {name:value};
}
if(selfEdit!=true) {
    var scope=directoryScope('users:write','workforce');
    var orgs=sql.select("""
select distinct org_id
from iam_appointment_t
where subject_id=#{subjectId} and is_del=0 and status='ACTIVE'
  and valid_from<=utc_timestamp(6)
  and (valid_until is null or valid_until>utc_timestamp(6))
""");
    if(!scope.all && (orgs.size()==0 || orgs.filter(o=>!scope.orgIds.contains(o.org_id)).size()>0)) {
        exit 403,'人员超出中央资料管理范围';
    }
}
sql.update("""
update iam_subject_t
set name=#{value},version=version+1,updated_by=#{actorId},updated_time=utc_timestamp(6)
where tid=#{subjectId} and version=#{subjectVersion}
""");
// 姓名属于人工下发资料；只标记期望版本，不在保存时自动下发。
sql.update("""
update iam_subject_app_t
set desired_version=desired_version+1,version=version+1,updated_time=utc_timestamp(6)
where subject_id=#{subjectId}
""");
return {name:value};
