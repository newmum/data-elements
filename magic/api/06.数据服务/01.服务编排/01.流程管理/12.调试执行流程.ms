{
  "properties": {},
  "id": "4c2f0f135762a126d404b61a2110f69f",
  "script": null,
  "groupId": "962e0e1e86609a165de79f1eb5294c2f",
  "name": "12.调试执行流程",
  "createTime": 1784826967644,
  "updateTime": 1784826967644,
  "createBy": "flowserve",
  "updateBy": "flowserve",
  "path": "/flows/{id}/debug",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"flow\":{\"flowId\":\"fl_demo\",\"nodes\":[]},\"script\":\"return {ok:true}\"}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "临时注册并执行前端编译的 Magic 脚本，执行结束立即注销。请求示例含 flow 与 script。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：12.调试执行流程
// 路由：POST /dws/flowserve/flows/{id}/debug
// 业务说明：临时注册并执行前端编译的 Magic 脚本，执行结束立即注销。请求示例含 flow 与 script。
import flowRuntime
var result = flowRuntime.debug(body.flow,body.script)
// 转换为云梯前端既有的调试结果字段。
return {
  success:result.ok,totalMs:result.durationMs,trace:result.trace,
  response:result.response,error:result.error
}
