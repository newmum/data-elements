{
  "properties": {},
  "id": "nifi_pipe_list_get_v1",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "01.查询流程列表",
  "path": "/list",
  "description": "读取本租户完整的已保存流程摘要，无 NiFi 请求。",
  "method": "GET",
  "parameters": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    },
    {
      "name": "head",
      "value": "true"
    }
  ],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiSavedCanvasRows' as loadSavedCanvases
import '@/common/nifiPipelineListResponse' as respond

// 读取当前行业租户已保存流程的摘要，无分页信封、写入或 NiFi 请求。

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
