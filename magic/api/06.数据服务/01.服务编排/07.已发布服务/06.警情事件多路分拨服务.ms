{
  "properties": {},
  "id": "484e4cd5e10840f28143712d9325a9b0",
  "script": null,
  "groupId": "e5ce028f1797312d57c628b66ffcf657",
  "name": "06.警情事件多路分拨服务",
  "createTime": 1781574564856,
  "updateTime": 1781574564804,
  "lock": null,
  "createBy": null,
  "updateBy": "flowserve",
  "path": "/police/event/multi-branch-dispatch",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按警情类型分拨到治安、交管、出入境、反恐和人工复核链路，并配置不同处置时限。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：06.警情事件多路分拨服务
// 路由：POST /dws/flowserve/published/police/event/multi-branch-dispatch
// 业务说明：按警情类型分拨到治安、交管、出入境、反恐和人工复核链路，并配置不同处置时限。
// === 自动生成，请勿手改；flowId: fl_9goq3dw5ec ===

if (eventType == null) {
    return response.json(400, { code: 400, message: 'eventType 不能为空' });
}
if (areaCode == null) {
    return response.json(400, { code: 400, message: 'areaCode 不能为空' });
}

// 节点: validate [validate_hazard] 事件参数校验
// validate
if (!(eventType != null && areaCode != null && eventType != '' && areaCode != '')) {
  return response.json(400, { code: 400, message: '参数校验失败' });
}

// 节点: switch [switch_hazard] 按事件类型多路分支
var __switch_switch_hazard = eventType;
if (__switch_switch_hazard == 'alarm_bulletin') {
// 节点: script [script_red] 警情处置链路
var dispatchResult = { route:'警情监测组', action:'加密核查+属地派出所通知+县区联动', sla:'2小时', areaCode:areaCode, level:level };

}
else if (__switch_switch_hazard == 'major_alarm') {
// 节点: http-call [http_storm] 重大警情预案联动
var stormHealth = httpx.request({ method: 'GET', url: 'http://127.0.0.1:9999/api/v1/runtime/health', datasource: '', path: '', timeoutMs: 3000 });

// 节点: script [script_storm] 重大警情响应方案
var dispatchResult = { route:'重大警情处置专班', action:'联动情指中心+通知属地派出所处置准备', sla:'1小时', areaCode:areaCode, level:level };

}
else if (__switch_switch_hazard == 'person_distress') {
// 节点: script [script_distress] 人员险情派单
var dispatchResult = { route:'应急指挥协调组', action:'定位人员+联动巡特警+通知属地派出所', sla:'15分钟', areaCode:areaCode };

}
else if (__switch_switch_hazard == 'mass_incident') {
// 节点: script [script_disease] 群体事件处置
var dispatchResult = { route:'水产技术推广站', action:'抽样检测+处方建议+跟踪复测', sla:'4小时', areaCode:areaCode };

}
else if (__switch_switch_hazard == 'illegal_fishing') {
// 节点: log [log_illegal] 重点人员执法日志
// log.warn: illegal fishing clue routed to enforcement

// 节点: script [script_illegal] 重点人员执法派单
var dispatchResult = { route:'派出所执法总队', action:'核验AIS轨迹+派发巡航任务+证据留存', sla:'30分钟', areaCode:areaCode };

}
else {
// 节点: script [script_default] 默认人工复核
var dispatchResult = { route:'省级值班室', action:'人工研判后转派对应处室', sla:'30分钟', areaCode:areaCode, eventType:eventType };

}

// 节点: response [response] 标准响应
return { code: 0, message: 'success', data: dispatchResult };
