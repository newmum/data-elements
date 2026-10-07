{
  "properties": {},
  "id": "f29f06100b6dc0101d5b591b362457f7",
  "script": null,
  "groupId": "962e0e1e86609a165de79f1eb5294c2f",
  "name": "07.新建流程",
  "createTime": 1784826967425,
  "updateTime": 1784826967425,
  "createBy": "flowserve",
  "updateBy": "flowserve",
  "path": "/flows",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"name\":\"订单查询服务\"}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "新建可直接进入设计器编辑的流程草稿。请求示例：{\"name\":\"订单查询服务\"}；涉及 fs_flow 等业务表；执行数据库写入并返回处理结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：07.新建流程
// 路由：POST /dws/flowserve/flows
// 业务说明：新建可直接进入设计器编辑的流程草稿。请求示例：{"name":"订单查询服务"}；涉及 fs_flow 等业务表；执行数据库写入并返回处理结果。
import flowDataSource
import jsons
var flowId = flowDataSource.newId('fl')
var triggerId = flowDataSource.newId('node')
var responseId = flowDataSource.newId('node')
var flowName = body == null || body.name == null || body.name == '' ? '新建编排' : body.name
// 创建与设计器 DSL 完全兼容的最小触发器—响应流程。
var flow = {
  flowId:flowId,name:flowName,description:'',version:1,group:'默认分组',
  nodes:[
    {id:triggerId,type:'trigger',name:'API 触发器',position:{x:80,y:200},
     data:{method:'GET',path:'/api/'+flowId,group:'默认分组',
           auth:{required:false,roles:[]},request:{query:[],path:[],header:[],body:null}}},
    {id:responseId,type:'response',name:'响应',position:{x:520,y:200},
     data:{mode:'wrap',source:'',wrap:true,statusCode:200}}
  ],
  edges:[{id:flowDataSource.newId('e'),source:triggerId,target:responseId,sourcePort:'out',targetPort:'in'}],
  settings:{timeoutMs:10000,transactional:false,errorStrategy:'fail-fast'}
}
db.table('fs_flow').insert({id:flowId,name:flowName,flow_group:'默认分组',status:'draft',
  current_version:1,dsl:jsons.toJSONStr(flow),lock_version:1,deleted:0})
return {flow:flow,status:'draft',lockVersion:1,updatedAt:new Date()+''}
