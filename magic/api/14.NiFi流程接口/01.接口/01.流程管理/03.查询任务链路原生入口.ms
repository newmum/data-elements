{
  "properties": {},
  "id": "0dc8254bb39040ebad3a1ad704fb1a31",
  "script": null,
  "groupId": "40ba1da5548648fdad7c449eb97fc033",
  "name": "03.查询任务链路原生入口",
  "path": "/task-chain/native",
  "method": "GET",
  "description": "按 accessTaskId 查询当前认证行业租户的任务链路及可访问的原生 NiFi 入口。需要时读取一次根组状态，不改变流程。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiAccessTaskChainHttp' as taskChain
import java.lang.String
import request

// GET /nifi/api/pipelines/task-chain/native 接收 accessTaskId，查询当前行业租户可访问的原生流程组入口。
// 仅在有多个已部署任务时核验 NiFi 根组层级，无写入。

// 保留 Servlet 查询参数的缺省、空值和重复值语义。
var values = request.getValues('accessTaskId')
return taskChain(values == null ? null : String.join(',', values), false, true)
