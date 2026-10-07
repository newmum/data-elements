{
  "properties": {},
  "id": "28ee5d9e32e04dfa8438756b9302b5de",
  "script": null,
  "groupId": "1f4028f20aaa4f379b5e37a343b99fa9",
  "name": "14.获取etl任务分组",
  "createTime": 1782955344758,
  "updateTime": 1784437771801,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "getEtlGroup",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"rootId\": \"root\",\n        \"tree\": [\n            {\n                \"id\": \"8dba3ae7-019c-1000-ffff-ffff8762908e\",\n                \"name\": \"1111\",\n                \"label\": \"1111\",\n                \"value\": \"8dba3ae7-019c-1000-ffff-ffff8762908e\",\n                \"parentId\": \"21a49dad-019c-1000-e2ca-0d4d12775733\",\n                \"children\": []\n            },\n            {\n                \"id\": \"925ce836-019c-1000-ffff-ffffd3d25742\",\n                \"name\": \"test\",\n                \"label\": \"test\",\n                \"value\": \"925ce836-019c-1000-ffff-ffffd3d25742\",\n                \"parentId\": \"21a49dad-019c-1000-e2ca-0d4d12775733\",\n                \"children\": [\n                    {\n                        \"id\": \"9dca35d6-019c-1000-ffff-fffffe895a4f\",\n                        \"name\": \"123\",\n                        \"label\": \"123\",\n                        \"value\": \"9dca35d6-019c-1000-ffff-fffffe895a4f\",\n                        \"parentId\": \"925ce836-019c-1000-ffff-ffffd3d25742\",\n                        \"children\": [\n                            {\n                                \"id\": \"9dca5b07-019c-1000-0000-00005b404356\",\n                                \"name\": \"123456\",\n                                \"label\": \"123456\",\n                                \"value\": \"9dca5b07-019c-1000-0000-00005b404356\",\n                                \"parentId\": \"9dca35d6-019c-1000-ffff-fffffe895a4f\",\n                                \"children\": []\n                            }\n                        ]\n                    }\n                ]\n            }\n        ]\n    }\n}",
  "description": "获取 NiFi 接入任务分组树。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：14.获取etl任务分组
// 路由：GET /ods/getEtlGroup
// 业务说明：获取 NiFi 接入任务分组树。
/*
 * 获取 NiFi 接入任务分组树
 *
 * Magic 负责递归编排和页面结构组装，NifiClient 只负责认证后的运行时调用。
 * 根分组读取现有配置，未配置时按 NiFi root 处理。
 */
import com.fasterxml.jackson.databind.JsonNode
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient as nifiClient
import '@/common/configData' as configData

var configuredRoot = configData().nifi_gourpId
var rootId = configuredRoot == null || configuredRoot == '' ? 'root' : configuredRoot
var loadChildren
loadChildren = (processGroupId, parentId) => {
    var response = nifiClient.get('/process-groups/' + processGroupId + '/process-groups', JsonNode.class)
    var result = []
    var groups = response.path('processGroups')
    if (groups.isArray()) {
        for (item in groups) {
            var id = item.path('id').asText()
            var name = item.path('component').path('name').asText()
            result.add({
                id: id,
                name: name,
                label: name,
                value: id,
                parentId: parentId,
                children: loadChildren(id, id)
            })
        }
    }
    // 返回当前业务分支的标准结果。
    return result
}

// 返回当前业务分支的标准结果。
return loadChildren('root', rootId)
