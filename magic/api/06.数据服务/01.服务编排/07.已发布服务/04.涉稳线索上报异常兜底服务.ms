{
  "properties": {},
  "id": "a6b04e11333413798cfe2cb213e3388a",
  "script": null,
  "groupId": "e5ce028f1797312d57c628b66ffcf657",
  "name": "04.涉稳线索上报异常兜底服务",
  "createTime": 1781574563683,
  "updateTime": 1781574563634,
  "lock": null,
  "createBy": null,
  "updateBy": "flowserve",
  "path": "/police/report/fallback-guard",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "保护涉稳线索上报链路，主链路异常时进入本地补偿队列和人工通知。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：04.涉稳线索上报异常兜底服务
// 路由：POST /dws/flowserve/published/police/report/fallback-guard
// 业务说明：保护涉稳线索上报链路，主链路异常时进入本地补偿队列和人工通知。
// === 自动生成，请勿手改；flowId: fl_3e4h3p7im6 ===

if (reportType == null) {
    return response.json(400, { code: 400, message: 'reportType 不能为空' });
}

// 节点: try-catch [try_guard] 保护上报主链路
try {
// 节点: script [script_submit] 主链路上报处理
var reportResult = { ok:true, channel:'provincial-data-hub', reportType:reportType };

// 节点: response [response] 标准响应
return { code: 0, message: 'success', data: reportResult };

} catch(e) {
var reportError = e;
// 节点: script [script_fallback] 异常兜底入库
var reportResult = { ok:false, channel:'local-fallback-queue', action:'写入本地补偿队列并通知值班员' };

// 节点: response [response] 标准响应
return { code: 0, message: 'success', data: reportResult };

}
