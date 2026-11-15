{
  "properties": {},
  "id": "nifi_pipeline_find_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "48.海通读取租户画布",
  "path": "/nifiFindCanvas",
  "description": "流程原生地址、运行状态和处理器分页共用当前行业租户的画布读取。",
  "parameters": [
    {
      "name": "flowId",
      "description": "请求路径中的画布标识",
      "required": true,
      "dataType": "String",
      "type": "java.lang.String"
    },
    {
      "name": "tenantId",
      "description": "服务端当前行业租户",
      "required": true,
      "dataType": "String",
      "type": "java.lang.String"
    }
  ],
  "options": []
}
================================
import '@/common/nifiDecodeCanvas' as decodeCanvas
import tableRead

// @/common/nifiFindCanvas 为流程原生地址、运行状态和处理器分页查询当前行业租户的画布。
// 调用方先校验行业上下文并传入可信租户。已删除、跨租户或无法解析的画布都视为缺失。

var query = tableRead.from('nifi_pipeline_t', 'p', tenantId)
query.where(tableRead.equal(tableRead.column('p', 'id', 'TEXT'), flowId))
var rows = query.rows({
    id: tableRead.column('p', 'id', 'TEXT'),
    name: tableRead.column('p', 'name', 'TEXT'),
    description: tableRead.column('p', 'description', 'TEXT'),
    createdAt: tableRead.column('p', 'created_at', 'NUMBER'),
    updatedAt: tableRead.column('p', 'updated_at', 'NUMBER'),
    dsl: tableRead.column('p', 'dsl_json', 'LOB'),
    processGroupId: tableRead.column('p', 'nifi_process_group_id', 'TEXT'),
    status: tableRead.column('p', 'status', 'TEXT'),
    deployedHash: tableRead.column('p', 'last_deployed_hash', 'TEXT'),
    deployedAt: tableRead.column('p', 'last_deployed_at', 'NUMBER'),
    stoppedAt: tableRead.column('p', 'last_stopped_at', 'NUMBER'),
    mapping: tableRead.column('p', 'node_mapping_json', 'LOB'),
    lastBulletinId: tableRead.column('p', 'last_bulletin_id', 'NUMBER'),
    deleted: tableRead.column('p', 'is_del', 'NUMBER')
}, 1)
return rows.isEmpty() || rows[0].deleted == 1 ? null : decodeCanvas(rows[0])
