{
  "id": "portal_options_v1",
  "groupId": "652292437ed443acba737511e3597522",
  "name": "05.查询登录行业",
  "path": "/options",
  "method": "GET",
  "parameters": [],
  "options": [
    {
      "name": "access",
      "value": "PUBLIC"
    },
    {
      "name": "head",
      "value": "true"
    }
  ],
  "headers": [],
  "paths": [],
  "description": "从控制库读取已启用行业的公开选项。同一 GET 资源提供 HEAD。"
}
================================
import '@/tenant-account/response' as respond
import db

// /portal/sso/options 为门户登录行业选择读取公开资料，不返回账号源和连接配置。
return respond(() => db.normal().table('sym_tenant_t').columns('tid', 'code', 'name').where()
    .eq('status', 1).eq('is_del', 0).orderBy('sort_no', 'asc').orderBy('created_time', 'asc').select())
