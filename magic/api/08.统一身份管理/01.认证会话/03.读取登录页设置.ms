{
  "properties": {},
  "id": "c5e8f3f5dc9d52d5b23709f6e3c2ec15",
  "script": null,
  "groupId": "846e66f7a9365dc4a13942533f262c0a",
  "name": "03.读取登录页设置",
  "createTime": 1790612165860,
  "updateTime": 1790948254995,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/settings",
  "description": "匿名读取登录页可公开的品牌与安全设置，仅返回允许公开的字段，不暴露密钥及内部认证策略。",
  "parameters": [],
  "method": "GET",
  "options": [
    {"name": "access", "value": "PUBLIC"}
  ],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
import identityRuntime;
import '@/idaas/api-call' as apiCall;
import db;
import cn.hutool.json.JSONUtil;

// GET /idaas/auth/settings。
// 业务说明：匿名读取登录页可公开的品牌与安全设置，仅返回允许公开的字段，不暴露密钥及内部认证策略。

return identityRuntime.control(()=>apiCall(()=>{
    var setting = db.normal().table('iam_setting_t').columns('value_json').where()
        .eq('scope_kind', 'GLOBAL').eq('scope_key', 'global').eq('category', 'BRANDING')
        .eq('config_key', 'brand').eq('is_del', 0).eq('status', 'ACTIVE').selectOne();
    var v = setting == null ? {} : JSONUtil.parseObj(setting.value_json);
    return {
        title:v.title,subtitle:v.subtitle,logoUrl:v.logoUrl
    };
}));
