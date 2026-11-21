{
  "properties": {},
  "id": "c5e8f3f5dc9d52d5b23709f6e3c2ec15",
  "script": null,
  "groupId": "846e66f7a9365dc4a13942533f262c0a",
  "name": "03.读取登录页设置",
  "createTime": 1790612165860,
  "updateTime": 1794837792880,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/settings",
  "method": "GET",
  "parameters": [],
  "options": [
    {
      "name": "access",
      "value": "PUBLIC",
      "description": null,
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "head",
      "value": "true"
    }
  ],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "匿名读取登录页可公开的品牌与安全设置，仅返回允许公开的字段，不暴露密钥及内部认证策略。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import identityRuntime;
import '@/idaas/api-call' as apiCall;
import db;
import cn.hutool.json.JSONUtil;

// GET 与 HEAD /idaas/auth/settings 读取控制库当前启用的全局登录品牌。仅返回允许匿名读取的三个展示字段。
return identityRuntime.control(()=>apiCall(()=>{
    var setting = db.normal().table('iam_setting_t').columns('value_json').where()
        .eq('scope_kind', 'GLOBAL').eq('scope_key', 'global').eq('category', 'BRANDING')
        .eq('config_key', 'brand').eq('is_del', 0).eq('status', 'ACTIVE').selectOne();
    var value = setting == null ? {} : JSONUtil.parseObj(setting.value_json);
    return {title:value.title, subtitle:value.subtitle, logoUrl:value.logoUrl};
}));
