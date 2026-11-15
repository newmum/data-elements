{
  "properties": {},
  "id": "28ee5d9e32e04dfa8438756b9302b5de",
  "script": null,
  "groupId": "99c8dd7189af40939e5110b76bcd7160",
  "name": "06.获取etl任务分组",
  "createTime": 1782955344758,
  "updateTime": 1784437771801,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/groups",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"rootId\": \"root\",\n        \"tree\": [\n            {\n                \"id\": \"8dba3ae7-019c-1000-ffff-ffff8762908e\",\n                \"name\": \"1111\",\n                \"label\": \"1111\",\n                \"value\": \"8dba3ae7-019c-1000-ffff-ffff8762908e\",\n                \"parentId\": \"21a49dad-019c-1000-e2ca-0d4d12775733\",\n                \"children\": []\n            },\n            {\n                \"id\": \"925ce836-019c-1000-ffff-ffffd3d25742\",\n                \"name\": \"test\",\n                \"label\": \"test\",\n                \"value\": \"925ce836-019c-1000-ffff-ffffd3d25742\",\n                \"parentId\": \"21a49dad-019c-1000-e2ca-0d4d12775733\",\n                \"children\": [\n                    {\n                        \"id\": \"9dca35d6-019c-1000-ffff-fffffe895a4f\",\n                        \"name\": \"123\",\n                        \"label\": \"123\",\n                        \"value\": \"9dca35d6-019c-1000-ffff-fffffe895a4f\",\n                        \"parentId\": \"925ce836-019c-1000-ffff-ffffd3d25742\",\n                        \"children\": [\n                            {\n                                \"id\": \"9dca5b07-019c-1000-0000-00005b404356\",\n                                \"name\": \"123456\",\n                                \"label\": \"123456\",\n                                \"value\": \"9dca5b07-019c-1000-0000-00005b404356\",\n                                \"parentId\": \"9dca35d6-019c-1000-ffff-fffffe895a4f\",\n                                \"children\": []\n                            }\n                        ]\n                    }\n                ]\n            }\n        ]\n    }\n}",
  "description": "使用当前配置的 NiFi 客户端读取一次根流程组的递归状态快照，返回分组标识、名称及父子树。顶层父标识使用现有 NiFi 分组配置，缺省为 root。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient as nifiClient
import '@/common/configData' as configData

// GET /ods/job/groups 读取 NiFi 接入任务分组树，供表单按分组 ID 选择。

// 顶层选项的父标识沿用当前配置，节点层级取自同一份 NiFi 根组快照。
var configuredRoot = configData().nifi_gourpId
var rootId = configuredRoot == null || configuredRoot == '' ? 'root' : configuredRoot
var snapshot = nifiClient.getProcessGroupStatus('root')
    .path('processGroupStatus').path('aggregateSnapshot')

// 遍历内存快照组装返回树，分组展开无需向 NiFi 补查。
var loadChildren
loadChildren = (parent, parentId) => {
    var result = []
    for (entry in parent.path('processGroupStatusSnapshots')) {
        var child = entry.path('processGroupStatusSnapshot')
        if (child.isObject()) {
            var id = child.path('id').asText()
            var name = child.path('name').asText()
            result.add({id: id, name: name, label: name, value: id,
                        parentId: parentId, children: loadChildren(child, id)})
        }
    }
    return result
}

return loadChildren(snapshot, rootId)
