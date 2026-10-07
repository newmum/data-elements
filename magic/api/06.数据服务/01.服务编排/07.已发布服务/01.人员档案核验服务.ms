{
  "properties": {},
  "id": "064c536fa0d240fc1dbbfee3f03808df",
  "script": null,
  "groupId": "e5ce028f1797312d57c628b66ffcf657",
  "name": "01.人员档案核验服务",
  "createTime": 1781574552339,
  "updateTime": 1781574552278,
  "lock": null,
  "createBy": null,
  "updateBy": "flowserve",
  "path": "/police/person/archive-check",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按人员编号核验基础身份、涉案关联、布控状态和风险标签，支撑执法核查调用。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.人员档案核验服务
// 路由：GET /dws/flowserve/published/police/person/archive-check
// 业务说明：按人员编号核验基础身份、涉案关联、布控状态和风险标签，支撑执法核查调用。
// === 自动生成，请勿手改；flowId: fl_dwzeilf50h ===

if (personNo == null) {
    return response.json(400, { code: 400, message: 'personNo 不能为空' });
}

// 节点: sql-query [sql_person] 查询人员档案库
var person = db.chengtian.selectOne("SELECT 'FZRY202607001' personNo, '霞浦县重点人员档案库' ownerName, '刺网' personType, '有效' annualStatus, '重点监管' tag");

// 节点: transform [map_person] 档案字段标准化
var archiveResult = person;

// 节点: log [log_person] 记录档案核验日志
// log.info: person archive checked

// 节点: response [response] 标准响应
return { code: 0, message: 'success', data: archiveResult };
