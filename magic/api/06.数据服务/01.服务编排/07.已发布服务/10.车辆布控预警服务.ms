{
  "properties": {},
  "id": "b4b18ecf6bc3b596107e7b7d9a011747",
  "script": null,
  "groupId": "e5ce028f1797312d57c628b66ffcf657",
  "name": "10.车辆布控预警服务",
  "createTime": 1781574553650,
  "updateTime": 1781574553596,
  "lock": null,
  "createBy": null,
  "updateBy": "flowserve",
  "path": "/police/vehicle/control-alert",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "接收车辆号牌和辖区编码，筛选异常通行记录并按风险等级输出预警。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：10.车辆布控预警服务
// 路由：GET /dws/flowserve/published/police/vehicle/control-alert
// 业务说明：接收车辆号牌和辖区编码，筛选异常通行记录并按风险等级输出预警。
// === 自动生成，请勿手改；flowId: fl_af2eddodue ===

if (areaCode == null) {
    return response.json(400, { code: 400, message: 'areaCode 不能为空' });
}

// 节点: validate [validate_area] 辖区参数校验
// validate
if (!(areaCode != null && areaCode != '')) {
  return response.json(400, { code: 400, message: '参数校验失败' });
}

// 节点: script [script_samples] 生成水质监测样本
var samples = [{ station:'鼓楼辖区1号', ph:8.1, oxygen:5.6, risk:1 }, { station:'鼓楼辖区2号', ph:9.2, oxygen:3.1, risk:4 }, { station:'东吾洋3号', ph:8.8, oxygen:3.8, risk:3 }];

// 节点: filter [filter_alert] 异常指标过滤
var alerts = samples;
// filter predicate: item.risk >= 3

// 节点: sort [sort_alert] 按风险等级排序
var rankedAlerts = alerts;
// sort: risk desc

// 节点: response [response] 标准响应
return { code: 0, message: 'success', data: rankedAlerts };
