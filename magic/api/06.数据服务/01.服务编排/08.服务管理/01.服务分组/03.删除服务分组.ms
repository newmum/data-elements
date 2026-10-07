{
  "properties": {},
  "id": "gateway_group_delete_20260726",
  "script": null,
  "groupId": "fb9192d12472469f82f204f1a4310a28",
  "name": "03.删除服务分组",
  "createTime": 1785200000000,
  "updateTime": 1785200000000,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/delete",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"id\": \"api_service_group_marine\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"deleted\": true,\n  \"id\": \"api_service_group_marine\"\n}",
  "description": "删除服务分组（POST /dws/flowserve/gateway/group/delete）。软删除 sym_dict_t 中 biz_type=apiServiceGroup 的分组；若 api_info_t 仍有关联服务则拒绝删除；使用请求参数 id、tid。"
}
================================
// 接口：03.删除服务分组
// 路由：POST /dws/flowserve/gateway/group/delete
// 业务说明：删除服务分组（POST /dws/flowserve/gateway/group/delete）。软删除 sym_dict_t 中 biz_type=apiServiceGroup 的分组；若 api_info_t 仍有关联服务则拒绝删除；使用请求参数 id、tid。
/*
 * 服务分组删除。
 * 分组作为 api_info_t.group_id 的字典，不做级联删除服务；
 * 有服务占用时先提示用户调整或删除服务，避免右侧列表出现孤儿分组。
 */
var blank = value => value == null || (value + '').trim() == ''
var id = body?.id || body?.tid
if (blank(id)) {
    return {
        success: false,
        code: 'PARAM_REQUIRED',
        msg: '分组ID不能为空'
    }
}
if ((id + '') == 'api_service_group_default') {
    return {
        success: false,
        code: 'DEFAULT_GROUP_LOCKED',
        msg: '默认分组不能删除'
    }
}

var usingCount = db.table('api_info_t').where().eq('group_id', id).eq('is_del', 0).count()
if (usingCount > 0) {
    return {
        success: false,
        code: 'GROUP_IN_USE',
        msg: '分组下仍有服务，请先调整服务分组或删除服务'
    }
}

db.table('sym_dict_t').where()
  .eq('tid', id)
  .eq('biz_type', 'apiServiceGroup')
  .update({is_del: 1, updated_time: new Date()})

return {
    deleted: true,
    id: id
}
