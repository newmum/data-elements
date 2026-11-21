{
  "properties": {},
  "id": "bf142250e1a35995a1041adb40ea5bf4",
  "script": null,
  "groupId": "5cc0c0d466b0571a9fe288ca74f0c356",
  "name": "09.接口访问与审计封装",
  "createTime": 1790612165860,
  "updateTime": 1790949884838,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/api-call",
  "description": "统一执行接口调用闭包，框架可单独使用相同的来源与配额校验。业务审计由请求采集器记录。",
  "parameters": [
    {
      "name": "callback",
      "value": null,
      "description": "在事务中执行的业务回调",
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
      "name": "checkRequestSource",
      "value": null,
      "description": "由 Bearer 访问策略发起的来源与配额校验",
      "required": false,
      "dataType": "Boolean",
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
import db;
import identityApi;
import cn.hutool.json.JSONUtil;
import cn.hutool.crypto.digest.DigestUtil;

// 通用调用继续执行各自的业务闭包。框架按资源声明先校验接口来源和配额，再处理凭证。
if (checkRequestSource == true) {
    var row = db.normal().table('iam_setting_t').columns('value_json').where()
        .eq('category', 'API_ACCESS').eq('config_key', 'access').eq('scope_key', 'global')
        .eq('is_del', 0).eq('status', 'ACTIVE').selectOne();
    if (row != null) {
        var policy = JSONUtil.parseObj(row.value_json);
        var address = identityApi.remoteAddress();
        var deny = (policy.ipBlacklist || '').split('\n').filter(value => value.trim() != '');
        var allow = (policy.apiWhitelist || '').split('\n').filter(value => value.trim() != '');
        if (deny.filter(value => identityApi.matchesAddress(value.trim(), address)).size() > 0
            || allow.size() > 0 && allow.filter(value => identityApi.matchesAddress(value.trim(), address)).size() == 0) {
            exit 403, '接口来源不在允许范围';
        }
        if (!identityApi.withinRate(DigestUtil.sha256Hex(address), policy.apiRateLimit)) {
            exit 429, '接口请求超过当前配额';
        }
    }
    return true;
}
return callback();
