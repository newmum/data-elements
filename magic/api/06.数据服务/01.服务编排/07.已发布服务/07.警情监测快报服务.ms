{
  "properties": {},
  "id": "eab7ec2cb2e39d8a600370675adeb594",
  "script": null,
  "groupId": "e5ce028f1797312d57c628b66ffcf657",
  "name": "07.警情监测快报服务",
  "createTime": 1781574554863,
  "updateTime": 1781574554810,
  "lock": null,
  "createBy": null,
  "updateBy": "flowserve",
  "path": "/police/alarm/bulletin",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "汇总多个辖区警情数量、风险等级和处置建议，形成面向值班室的快报结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：07.警情监测快报服务
// 路由：GET /dws/flowserve/published/police/alarm/bulletin
// 业务说明：汇总多个辖区警情数量、风险等级和处置建议，形成面向值班室的快报结果。
// === 自动生成，请勿手改；flowId: fl_q1fkq8ckqx ===


// 节点: script [script_alarm_bulletin] 生成警情站点明细
var alarmBulletinRows = [{ seaArea:'鼓楼辖区', alarmCount:8600, level:'orange' }, { seaArea:'罗源湾', alarmCount:3200, level:'yellow' }, { seaArea:'鼓楼辖区', alarmCount:9200, level:'orange' }];

// 节点: aggregate [agg_alarm_bulletin] 按辖区聚合快报
var bulletin = alarmBulletinRows;
// aggregate: 建议在 SQL/ES 侧下推；当前保留源数据供后续节点处理。

// 节点: set-var [set_advice] 补充处置建议
var bulletinWithAdvice = { summary: bulletin, advice: '加密核查，通知属地派出所，联动情指中心值班室' };

// 节点: response [response] 标准响应
return { code: 0, message: 'success', data: bulletinWithAdvice };
