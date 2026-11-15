{
  "properties": {},
  "id": "flowserve_canvas_flow_get_v1",
  "script": null,
  "groupId": "e5ce028f1797312d57c628b66ffcf657",
  "name": "06.查询画布流程详情",
  "path": "/flows/{id}",
  "method": "GET",
  "description": "按流程标识读取当前行业租户的云梯画布详情。校验关联服务及当前角色的数据范围，返回已保存的流程、状态、锁版本和更新时间。沿用原业务响应和错误格式，只读业务库。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/flowServeCanvasReadHttp' as readCanvas

// GET /dws/flowserve/flows/{id} 读取当前用户有权查看的已保存流程。
// 返回流程编排、状态、锁版本及更新时间。权限来自当前租户的关联服务，不修改业务数据。

return readCanvas(true, path.id)
