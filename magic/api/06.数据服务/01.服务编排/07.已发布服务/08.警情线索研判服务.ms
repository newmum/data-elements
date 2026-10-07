{
  "properties": {},
  "id": "e22a49ce6f0e3c64a14ab7e74af85eba",
  "script": null,
  "groupId": "e5ce028f1797312d57c628b66ffcf657",
  "name": "08.警情线索研判服务",
  "createTime": 1781574558755,
  "updateTime": 1781574558518,
  "lock": null,
  "createBy": null,
  "updateBy": "flowserve",
  "path": "/police/clue/analysis",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "汇聚警情线索、案件要素和处置记录，判断线索紧急程度并输出研判建议。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：08.警情线索研判服务
// 路由：GET /dws/flowserve/published/police/clue/analysis
// 业务说明：汇聚警情线索、案件要素和处置记录，判断线索紧急程度并输出研判建议。
// === 自动生成，请勿手改；flowId: fl_8tdpx19wtv ===

if (personNo == null) {
    return response.json(400, { code: 400, message: 'personNo 不能为空' });
}
if (expireDays == null) {
    return response.json(400, { code: 400, message: 'expireDays 不能为空' });
}

// 节点: sql-query [sql_clue] 查询许可证基础信息
var clue = db.chengtian.selectOne("SELECT 'FZJQ202607001' personNo, '涉稳警情线索' clueType, 12 expireDays, '正常' status");

// 节点: condition [cond_expire] 是否临近到期
if (expireDays <= 30) {
// 节点: script [script_urgent] 生成续期提醒
var renewalDecision = { level:'urgent', action:'发送短信并推送县级窗口', personNo:personNo, expireDays:expireDays };

} else {
// 节点: script [script_normal] 生成常规状态
var renewalDecision = { level:'normal', action:'纳入常规月度巡检', personNo:personNo, expireDays:expireDays };

}

// 节点: response [response] 标准响应
return { code: 0, message: 'success', data: renewalDecision };
