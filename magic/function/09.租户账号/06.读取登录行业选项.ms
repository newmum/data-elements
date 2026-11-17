{
  "id": "portal_fn_options_v1",
  "groupId": "ga_local_accounts_functions_01",
  "name": "06.读取登录行业选项",
  "path": "/options",
  "parameters": [],
  "description": "GET 和 HEAD 登录行业选项共用，从控制库读取启用租户的公开标识、编码和名称。"
}
================================
import '@/tenant-account/response' as respond
import db

// /tenant-account/options 为门户登录行业选择读取公开资料，不返回账号源和连接配置。
return respond(() => db.normal().table('sym_tenant_t').columns('tid', 'code', 'name').where()
    .eq('status', 1).eq('is_del', 0).orderBy('sort_no', 'asc').orderBy('created_time', 'asc').select())
