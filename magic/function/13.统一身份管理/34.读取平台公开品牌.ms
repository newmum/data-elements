{
  "id": "idaas_public_brand_read_v1",
  "groupId": "5cc0c0d466b0571a9fe288ca74f0c356",
  "name": "34.读取平台公开品牌",
  "path": "/publicbrand",
  "parameters": [],
  "description": "供 GET 和 HEAD /idaas/auth/settings 共用，从控制库读取已启用的全局登录品牌。"
}
================================
import identityRuntime;
import '@/idaas/api-call' as apiCall;
import db;
import cn.hutool.json.JSONUtil;

// GET 和 HEAD 共用登录品牌。仅返回允许匿名读取的三个展示字段。
return identityRuntime.control(()=>apiCall(()=>{
    var setting = db.normal().table('iam_setting_t').columns('value_json').where()
        .eq('scope_kind', 'GLOBAL').eq('scope_key', 'global').eq('category', 'BRANDING')
        .eq('config_key', 'brand').eq('is_del', 0).eq('status', 'ACTIVE').selectOne();
    var value = setting == null ? {} : JSONUtil.parseObj(setting.value_json);
    return {title:value.title, subtitle:value.subtitle, logoUrl:value.logoUrl};
}));
