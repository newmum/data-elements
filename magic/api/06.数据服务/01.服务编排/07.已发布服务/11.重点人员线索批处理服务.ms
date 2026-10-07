{
  "properties": {},
  "id": "134d48c35915e997ffe8e723be82dfed",
  "script": null,
  "groupId": "e5ce028f1797312d57c628b66ffcf657",
  "name": "11.重点人员线索批处理服务",
  "createTime": 1781574561306,
  "updateTime": 1781574561258,
  "lock": null,
  "createBy": null,
  "updateBy": "flowserve",
  "path": "/police/person/clue-batch",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "批量处理重点人员轨迹、案件和布控线索，循环生成核查对象并聚合风险概况。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：11.重点人员线索批处理服务
// 路由：POST /dws/flowserve/published/police/person/clue-batch
// 业务说明：批量处理重点人员轨迹、案件和布控线索，循环生成核查对象并聚合风险概况。
// === 自动生成，请勿手改；flowId: fl_ujze9ez5oe ===

if (batchNo == null) {
    return response.json(400, { code: 400, message: 'batchNo 不能为空' });
}

// 节点: script [script_clues] 生成线索批次
var clues = [{ clueId:'C001', risk:92, source:'AIS' }, { clueId:'C002', risk:61, source:'举报' }, { clueId:'C003', risk:87, source:'雷达' }];

// 节点: foreach [loop_clues] 逐条生成核查任务
var tasks = [];
for (clue in clues) {
  tasks.add(clue);
}

// 节点: filter [filter_high_risk] 筛选高风险线索
var highRiskClues = tasks;
// filter predicate: item.risk >= 80

// 节点: response [response] 标准响应
return { code: 0, message: 'success', data: highRiskClues };
