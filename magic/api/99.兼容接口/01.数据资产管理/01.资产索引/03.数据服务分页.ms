{
  "properties": {},
  "id": "compat_assets_page_01",
  "script": null,
  "groupId": "compat_dataassets_assets_01",
  "name": "03.数据服务分页",
  "createTime": 1784765423548,
  "updateTime": 1784765423548,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"sortField\": \"updatedTime\",\n  \"sortDir\": \"desc\",\n  \"pageNum\": 1,\n  \"pageSize\": 20,\n  \"conditions\": [\n    {\"field\": \"assetType\", \"value\": \"api\", \"type\": \"match\"},\n    {\"field\": \"name\", \"value\": \"接口\", \"type\": \"like\"}\n  ]\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"pageNum\": 1,\n  \"pageSize\": 20,\n  \"total\": 1,\n  \"list\": [{\"id\": \"api-id\", \"assetType\": \"api\", \"name\": \"示例服务接口\", \"publishAddressFull\": \"/api/example\"}]\n}",
  "description": "数据服务分页兼容接口，供 deal/data-server/list 调用；从 /dws/gateway/api/list 读取真实服务数据并转换为旧页面分页结构；使用请求参数 pageNum、pageSize、conditions。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.数据服务分页
// 路由：POST /dataassets/manage/assets/page
// 业务说明：数据服务分页兼容接口，供 deal/data-server/list 调用；从 /dws/gateway/api/list 读取真实服务数据并转换为旧页面分页结构；使用请求参数 pageNum、pageSize、conditions。
import '@post:/dws/gateway/api/list' as gatewayApiList
import java.util.List
import java.util.Map

/*
 * 数据服务分页兼容接口（POST /dataassets/manage/assets/page）。
 * 旧数据服务页面仍调用 dataassets/manage/assets/page；当前真实接口列表来自 /dws/gateway/api/list。
 * 本接口负责做地址兼容、分页、名称/分组筛选和字段别名补齐，不写入任何业务数据。
 */
var text = (value) => value == null ? '' : value + ''
var blank = (value) => value == null || text(value).trim() == ''
var lower = (value) => text(value).toLowerCase()
var first = (row, keys) => {
    if (row == null) {
        return null
    }
    for (key in keys) {
        var value = row instanceof Map ? row.get(key) : row[key]
        if (value != null) {
            return value
        }
    }
    return null
}

var pageNum = body == null || body.pageNum == null ? 1 : body.pageNum::int
var pageSize = body == null || body.pageSize == null ? 20 : body.pageSize::int
var keyword = ''
var groupId = ''
for (condition in (body == null || body.conditions == null ? [] : body.conditions)) {
    if (condition == null || blank(condition.field) || blank(condition.value)) {
        continue
    }
    if (condition.field == 'name') {
        keyword = text(condition.value)
    }
    if (condition.field == 'groupId') {
        groupId = text(condition.value)
    }
}

// 调用当前网关接口列表作为真实数据来源，避免继续依赖旧资产索引模型。
var oldBody = body
body = { pageNum: 1, pageSize: 10000 }
var raw = gatewayApiList()
body = oldBody
var payload = raw == null ? {} : (raw.data == null ? raw : raw.data)
var source = []
if (payload instanceof List) {
    source = payload
} else if (payload.list != null) {
    source = payload.list
} else if (raw != null && raw.list != null) {
    source = raw.list
}

var filtered = []
for (row in source) {
    if (row == null) {
        continue
    }
    var id = first(row, ['id', 'tid'])
    var name = first(row, ['name', 'apiName', 'serviceName'])
    var rowGroupId = first(row, ['groupId', 'group_id', 'categoryId'])
    if (!blank(keyword) && !lower(name).contains(lower(keyword))) {
        continue
    }
    if (!blank(groupId) && text(rowGroupId) != groupId) {
        continue
    }
    // 统一成旧数据服务页面所需字段，详情、发布地址和状态列都从这里读取。
    filtered.add({
        id: id,
        tid: id,
        assetType: 'api',
        name: name,
        description: first(row, ['description', 'desc', 'apiDesc']),
        groupId: rowGroupId,
        publishAddress: first(row, ['publishAddress', 'url', 'path']),
        publishAddressFull: first(row, ['publishAddressFull', 'publishAddress', 'url', 'path']),
        requestCount: first(row, ['requestCount', 'callCount']) == null ? 0 : first(row, ['requestCount', 'callCount']),
        successPercent: first(row, ['successPercent', 'successRate']) == null ? 0 : first(row, ['successPercent', 'successRate']),
        status: first(row, ['status', 'publishStatus']) == null ? 1 : first(row, ['status', 'publishStatus']),
        createdTime: first(row, ['createdTime', 'createTime']),
        updatedTime: first(row, ['updatedTime', 'updateTime']),
        createTime: first(row, ['createTime', 'createdTime']),
        updateTime: first(row, ['updateTime', 'updatedTime']),
        serviceCreateType: first(row, ['serviceCreateType', 'createType']),
        buildApiInfo: first(row, ['buildApiInfo'])
    })
}

var total = filtered.size()
var start = pageNum <= 1 ? 0 : (pageNum - 1) * pageSize
var end = start + pageSize
var list = []
var index = 0
for (item in filtered) {
    if (index >= start && index < end) {
        list.add(item)
    }
    index = index + 1
}
return { pageNum: pageNum, pageSize: pageSize, total: total, list: list }
