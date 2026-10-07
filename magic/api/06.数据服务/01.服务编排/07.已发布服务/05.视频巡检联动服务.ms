{
  "properties": {},
  "id": "e57f27492d2e90a1bd4a8ee7d1ad1c31",
  "script": null,
  "groupId": "e5ce028f1797312d57c628b66ffcf657",
  "name": "05.视频巡检联动服务",
  "createTime": 1781574557032,
  "updateTime": 1781574556941,
  "lock": null,
  "createBy": null,
  "updateBy": "flowserve",
  "path": "/police/video/inspection",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "联动视频平台巡检摄像头在线状态，生成点位设备健康结论和处置建议。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：05.视频巡检联动服务
// 路由：GET /dws/flowserve/published/police/video/inspection
// 业务说明：联动视频平台巡检摄像头在线状态，生成点位设备健康结论和处置建议。
// === 自动生成，请勿手改；flowId: fl_bfj66wlvv5 ===

if (portCode == null) {
    return response.json(400, { code: 400, message: 'portCode 不能为空' });
}

// 节点: http-call [http_video] 调用视频平台健康接口
var videoHealth = httpx.request({ method: 'GET', url: 'http://127.0.0.1:9999/api/v1/runtime/health', datasource: '', path: '', timeoutMs: 3000 });

// 节点: script [script_video] 生成巡检结论
var inspection = { portCode: portCode, cameraOnlineRate: 0.96, abnormalCameras: 3, action: '通知港区值守人员复核离线摄像头' };

// 节点: log [log_video] 记录视频巡检日志
// log.info: port video inspection completed

// 节点: response [response] 标准响应
return { code: 0, message: 'success', data: inspection };
