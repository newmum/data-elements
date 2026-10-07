{
  "properties": {},
  "id": "0c053297830b26c02f91d85f678914d5",
  "script": null,
  "groupId": "962e0e1e86609a165de79f1eb5294c2f",
  "name": "11.校验编译结果",
  "createTime": 1784826967609,
  "updateTime": 1784826967609,
  "createBy": "flowserve",
  "updateBy": "flowserve",
  "path": "/flows/{id}/compile",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"flow\":{\"flowId\":\"fl_demo\"},\"script\":\"return {ok:true}\"}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "接收前端编译器生成的 Magic 脚本并返回校验结果。请求示例：{\"script\":\"return {}\",\"flow\":{}}。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：11.校验编译结果
// 路由：POST /dws/flowserve/flows/{id}/compile
// 业务说明：接收前端编译器生成的 Magic 脚本并返回校验结果。请求示例：{"script":"return {}","flow":{}}。
// 校验编译结果：处理 POST /flows/{id}/compile 的核心查询和组装返回数据逻辑。
var script = body == null ? null : body.script
if (script == null || script.trim() == '') {
  return {ok:false,script:'',errors:[{message:'编译脚本不能为空'}],warnings:[],meta:{}}
}
return {ok:true,script:script,errors:[],warnings:[],meta:{}}
