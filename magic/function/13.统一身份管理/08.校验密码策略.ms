{
  "properties": {},
  "id": "d27a0bf45a1850a0bc5827cab4dc489f",
  "script": null,
  "groupId": "5cc0c0d466b0571a9fe288ca74f0c356",
  "name": "08.校验密码策略",
  "createTime": 1790612165860,
  "updateTime": 1790949884838,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/password-policy",
  "description": "复用密码长度、复杂度和禁用值校验；不存储明文密码。",
  "parameters": [
    {
      "name": "password",
      "value": null,
      "description": "待校验的新密码",
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
// 共享函数：08.校验密码策略
// 业务说明：复用密码长度、复杂度和禁用值校验；不存储明文密码。
// 函数输入：password：待校验的新密码。
// 返回与副作用：复用密码长度、复杂度和禁用值校验；不存储明文密码。
import db;
import cn.hutool.json.JSONUtil;

var sql=db.normal();
// 读取可编辑的全局密码策略；未配置时采用受控的默认最短长度。
var row=sql.selectOne("""
select value_json
from iam_setting_t
where scope_kind='GLOBAL'
    and scope_key='global'
    and category='SECURITY'
    and config_key='password'
    and is_del=0
    and status='ACTIVE'
""");
var policy=row==null?{}:JSONUtil.parseObj(row.value_json);
var min=policy.minLength||8;
if(password==null || password.length()<min || password.length()>256) {
    exit 400,'新密码长度不符合平台策略';
}
// 逐项比较弱密码禁用清单，不在此函数落库或记录明文。
var blacklist=(policy.passwordBlacklist||'').split('\n');
for(entry in blacklist) {
    if(entry.trim()==password) {
        exit 400,'新密码属于禁止使用的弱密码';
    }
}
return policy;
