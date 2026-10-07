{
  "properties": {},
  "id": "07ef9bbe176b43dcb275f479e1de839c",
  "script": null,
  "groupId": "2baeb325734a4f348df335661b205952",
  "name": "01.删除网关资产",
  "createTime": 1784437770688,
  "updateTime": 1785000000000,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/delete",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"apiId\": \"2361887157562377213\",\n  \"dryRun\": true\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"apiId\": \"2361887157562377213\",\n  \"paramCount\": 1,\n  \"fileCount\": 0,\n  \"dryRun\": true\n}",
  "description": "删除服务编排服务（POST /dws/flowserve/gateway/delete）。写入 api_info_t 软删除状态，并递归软删除 api_param_t 参数及 api_file_t 脚本关联；dryRun=true 时只返回影响范围；使用请求参数 apiId、api_id、id、tid、dryRun。"
}
================================
// 接口：01.删除网关资产
// 路由：POST /dws/flowserve/gateway/delete
// 业务说明：删除服务编排服务（POST /dws/flowserve/gateway/delete）。写入 api_info_t 软删除状态，并递归软删除 api_param_t 参数及 api_file_t 脚本关联；dryRun=true 时只返回影响范围；使用请求参数 apiId、api_id、id、tid、dryRun。
import magicResourceLink

/*
 * 删除服务主记录，并清理参数表与脚本文件关联。
 * 这里只做业务软删除，不删除物理 Magic 文件内容，避免误删可复用脚本。
 */
var apiId = body?.apiId || body?.api_id || body?.id || body?.tid
if (apiId == null || (apiId + '').trim() == '') {
    exit 400, 'apiId不能为空'
}

var apiCount = db.table('api_info_t').where().eq('tid', apiId).eq('is_del', 0).count()
var paramCount = db.table('api_param_t').where().eq('api_id', apiId).eq('is_del', 0).count()
var fileCount = magicResourceLink.countByApiId(apiId + '')
var result = {apiId: apiId, apiCount: apiCount, paramCount: paramCount, fileCount: fileCount, dryRun: body?.dryRun == true}
if (body?.dryRun == true) {
    return result
}

var now = new Date()
db.table('api_info_t').where().eq('tid', apiId).eq('is_del', 0).update({is_del: 1, updated_time: now})
db.table('api_param_t').where().eq('api_id', apiId).eq('is_del', 0).update({is_del: 1, updated_time: now})
magicResourceLink.unlinkByApiId(apiId + '')
return result
