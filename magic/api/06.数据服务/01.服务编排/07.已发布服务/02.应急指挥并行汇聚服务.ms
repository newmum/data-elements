{
  "properties": {},
  "id": "6f0c068c614dad26b88b20cc1b7c0246",
  "script": null,
  "groupId": "e5ce028f1797312d57c628b66ffcf657",
  "name": "02.应急指挥并行汇聚服务",
  "createTime": 1781574562482,
  "updateTime": 1781574562432,
  "lock": null,
  "createBy": null,
  "updateBy": "flowserve",
  "path": "/police/command/dashboard",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "并行汇聚警情、视频、警力和车辆资源，形成应急指挥态势看板。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.应急指挥并行汇聚服务
// 路由：GET /dws/flowserve/published/police/command/dashboard
// 业务说明：并行汇聚警情、视频、警力和车辆资源，形成应急指挥态势看板。
// === 自动生成，请勿手改；flowId: fl_nbpxp19kbl ===


// 节点: parallel [parallel_fetch] 并行汇聚指挥资源
var parallelResult = {};
try {
// 节点: script [script_weather] 气象预警分支
var weather = { weatherLevel:'yellow', windScale:8 };

// 节点: script [script_dashboard] 生成指挥看板
var dashboard = { weatherLevel:'yellow', personsOnline:3862, riskyPersons:27, patrolTeams:12, responseTeams:4, action:'启动重点区域巡控' };

// 节点: response [response] 标准响应
return { code: 0, message: 'success', data: dashboard };

  parallelResult['weather'] = { ok: true };
} catch(e) {
  parallelResult['weather'] = { ok: false, error: e };
  throw e;
}
try {
// 节点: script [script_persons] 船位在线分支
var persons = { personsOnline:3862, riskyPersons:27 };

// 节点: script [script_dashboard] 生成指挥看板
var dashboard = { weatherLevel:'yellow', personsOnline:3862, riskyPersons:27, patrolTeams:12, responseTeams:4, action:'启动重点区域巡控' };

// 节点: response [response] 标准响应
return { code: 0, message: 'success', data: dashboard };

  parallelResult['persons'] = { ok: true };
} catch(e) {
  parallelResult['persons'] = { ok: false, error: e };
  throw e;
}
try {
// 节点: script [script_resources] 执法资源分支
var resources = { patrolTeams:12, responseTeams:4 };

// 节点: script [script_dashboard] 生成指挥看板
var dashboard = { weatherLevel:'yellow', personsOnline:3862, riskyPersons:27, patrolTeams:12, responseTeams:4, action:'启动重点区域巡控' };

// 节点: response [response] 标准响应
return { code: 0, message: 'success', data: dashboard };

  parallelResult['resources'] = { ok: true };
} catch(e) {
  parallelResult['resources'] = { ok: false, error: e };
  throw e;
}
