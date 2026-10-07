{
  "properties": {},
  "id": "3f620a5ff088578cac61376a5a48bf70",
  "script": null,
  "groupId": "5cc0c0d466b0571a9fe288ca74f0c356",
  "name": "28.统一认证会话策略",
  "createTime": 1790641170352,
  "updateTime": 1790949884838,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/auth-session-policy",
  "description": "按统一认证账号读取会话时长、并发数量和撤销策略，供登录、续期和会话管理共用；仅返回策略，不直接创建会话。",
  "parameters": [
    {
      "name": "operation",
      "value": null,
      "description": "本次执行的业务操作",
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
      "name": "accountId",
      "value": null,
      "description": "统一认证账号标识",
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
// 共享函数：28.统一认证会话策略
// 业务说明：按统一认证账号读取会话时长、并发数量和撤销策略，供登录、续期和会话管理共用；仅返回策略，不直接创建会话。
// 函数输入：operation：本次执行的业务操作；accountId：统一认证账号标识。
// 返回与副作用：按统一认证账号读取会话时长、并发数量和撤销策略，供登录、续期和会话管理共用；仅返回策略，不直接创建会话。
import db;
import cn.hutool.json.JSONUtil;

// 可编辑会话规则在Magic；Java只执行传入的空闲秒数与并发会话参数。
var sql=db.normal();
var row=sql.selectOne("""
select value_json
from iam_setting_t
where scope_kind='GLOBAL'
    and scope_key='global'
    and category='SSO'
    and config_key='session'
    and status='ACTIVE'
    and is_del=0
""");
var value=row==null?{}:JSONUtil.parseObj(row.value_json);
var policy={
    accessTtl:value.accessTtl||600,refreshTtl:value.refreshTtl||86400,idleSeconds:(value.idleMinutes||30)*60,allowConcurrent:value.allowConcurrent!=false
};
// 签发新会话且禁止并发时，先撤销同账号未撤销的旧会话。
if(operation=='issue' && policy.allowConcurrent==false) {
    sql.update("""
update iam_auth_session_t
set revoked_at=utc_timestamp(6),revoke_reason='CONCURRENT_LOGIN',version=version+1
where principal_kind='AUTH_ACCOUNT'
    and principal_id=#{accountId}
    and revoked_at is null
""");
}
return policy;
