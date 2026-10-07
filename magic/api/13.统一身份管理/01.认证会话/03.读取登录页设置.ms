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
  "path": "/public-settings",
  "description": "匿名读取登录页可公开的品牌与安全设置，仅返回允许公开的字段，不暴露密钥及内部认证策略。",
  "parameters": [],
  "method": "GET",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：03.读取登录页设置
// 路由：GET /idaas/auth/public-settings
// 业务说明：匿名读取登录页可公开的品牌与安全设置，仅返回允许公开的字段，不暴露密钥及内部认证策略。
import identityRuntime;
import '@/idaas/api-call' as apiCall;
import db;
import cn.hutool.json.JSONUtil;

return identityRuntime.control(()=>apiCall(()=>{
    var r=db.normal().selectOne("""
select value_json
from iam_setting_t
where scope_kind='GLOBAL'
    and scope_key='global'
    and category='BRANDING'
    and config_key='brand'
    and is_del=0
    and status='ACTIVE'
""");
    var v=r==null?{}:JSONUtil.parseObj(r.value_json);
    return {
        title:v.title,subtitle:v.subtitle,logoUrl:v.logoUrl
    };
}));
