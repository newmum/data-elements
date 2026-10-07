{
  "properties": {},
  "id": "b51895b7229f4638b70233a29567ccd7",
  "script": null,
  "groupId": "fb9192d12472469f82f204f1a4310a28",
  "name": "01.保存服务分组",
  "createTime": 1782955363365,
  "updateTime": 1785200000000,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/save",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"id\": \"api_service_group_marine\",\n  \"name\": \"福建省海洋与渔业局\",\n  \"code\": \"marine\",\n  \"sortNo\": 2\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"id\": \"api_service_group_marine\",\n  \"name\": \"福建省海洋与渔业局\",\n  \"code\": \"marine\"\n}",
  "description": "保存服务分组（POST /dws/flowserve/gateway/group/save）。写入 sym_dict_t 中 biz_type=apiServiceGroup 的服务分组字典；用于服务编排管理左侧分组新增和编辑；使用请求参数 id、name、dictName、code、dictCode、sortNo、sort_no。"
}
================================
// 接口：01.保存服务分组
// 路由：POST /dws/flowserve/gateway/group/save
// 业务说明：保存服务分组（POST /dws/flowserve/gateway/group/save）。写入 sym_dict_t 中 biz_type=apiServiceGroup 的服务分组字典；用于服务编排管理左侧分组新增和编辑；使用请求参数 id、name、dictName、code、dictCode、sortNo、sort_no。
import cn.hutool.core.util.IdUtil;

/*
 * 服务分组保存。
 * 分组不再写旧网关 g_service_group，也不新建 api_service_group_t；
 * 统一落在 sym_dict_t，biz_type=apiServiceGroup，右侧 api_info_t.group_id 关联该字典 tid。
 */
var blank = value => value == null || (value + '').trim() == ''
var clean = value => blank(value) ? null : (value + '').trim()

var id = clean(body?.id)
var name = clean(body?.name || body?.dictName)
if (blank(name)) {
    return {
        success: false,
        code: 'PARAM_REQUIRED',
        msg: '分组名称不能为空'
    }
}

var code = clean(body?.code || body?.dictCode)
var sortNo = body?.sortNo == null ? body?.sort_no : body?.sortNo
sortNo = sortNo == null ? 999 : sortNo::int(999)
var now = new Date()

if (blank(id)) {
    id = 'api_service_group_' + IdUtil.getSnowflakeNextIdStr()
}
if (blank(code)) {
    code = id
}

// 同名或同编码校验，避免左侧树出现两个含义相同的分组。
var exists = db.select("""
select tid
  from sym_dict_t
 where coalesce(is_del, 0) = 0
   and biz_type = 'apiServiceGroup'
   and tid <> #{id}
   and (dict_name = #{name} or dict_code = #{code})
 limit 1
""")
if (exists != null && exists.size() > 0) {
    return {
        success: false,
        code: 'GROUP_EXISTS',
        msg: '服务分组名称或编码已存在'
    }
}

var row = {
    tid: id,
    dict_name: name,
    dict_code: code,
    biz_type: 'apiServiceGroup',
    sort_no: sortNo,
    is_del: 0,
    updated_time: now
}

var old = db.table('sym_dict_t').where().eq('tid', id).selectOne()
if (old == null) {
    row.created_time = now
    db.table('sym_dict_t').primary('tid').insert(row)
} else {
    db.table('sym_dict_t').primary('tid').update(row, true)
}

return {
    id: id,
    name: name,
    code: code,
    sortNo: sortNo
}
