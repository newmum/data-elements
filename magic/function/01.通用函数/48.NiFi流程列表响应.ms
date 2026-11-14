{
  "properties": {},
  "id": "nifi_pipe_list_http_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "48.NiFi流程列表响应",
  "path": "/nifiPipelineListHttp",
  "description": "为流程列表 GET、HEAD 保留原有摘要字段和空数组，按可信行业会话只读数据库。",
  "parameters": [],
  "options": []
}
================================
import '@/common/nifiSavedCanvasRows' as loadSavedCanvases
import '@/common/nifiPipelineListResponse' as respond

// @/common/nifiPipelineListHttp 为普通流程列表 GET、HEAD 共享摘要投影。
// 字段沿用已保存画布，无分页信封、写入或 NiFi 请求。
return respond(() => loadSavedCanvases(row => {
    var nodes = row.dsl?.nodes()
    return {
        id: row.id,
        name: row.name,
        description: row.description,
        createdAt: row.createdAt,
        updatedAt: row.updatedAt,
        nodeCount: nodes == null ? 0 : nodes.size(),
        nifiProcessGroupId: row.processGroupId
    }
}))
