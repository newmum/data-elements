{
  "properties": {},
  "id": "f12fde8d078541abb7083a4cfc6fb3b4",
  "script": null,
  "groupId": "dataservice_resource_group_01",
  "name": "04.浏览接口",
  "createTime": 1782955344092,
  "updateTime": 1784437771759,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/view",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"85368b91972c4a35b83b8fa73e34e740\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": [\n        {\n            \"id\": \"85368b91972c4a35b83b8fa73e34e740\",\n            \"tid\": \"85368b91972c4a35b83b8fa73e34e740\",\n            \"tenantId\": null,\n            \"assetType\": \"catalog\",\n            \"assetStatus\": \"2\",\n            \"flowStatus\": \"2\",\n            \"regTime\": \"2026-02-25 13:36:23\",\n            \"createdTime\": \"2026-02-25 13:36:23\",\n            \"updatedTime\": \"2026-03-13 11:54:35\",\n            \"regTime_date\": \"2026-02-25 13:36:23\",\n            \"createdTime_date\": \"2026-02-25 13:36:23\",\n            \"updatedTime_date\": \"2026-03-16 09:30:54\",\n            \"dataCatalogNum\": null,\n            \"dataRegionScope\": \"1\",\n            \"businessGuideName\": \"广电新媒体用户数据统计分析办事指南（2025版）\",\n            \"targetTableName\": \"生成表信息\",\n            \"sourceBusinessName\": \"国产电视剧片审查\",\n            \"targetDbType\": \"mysql\",\n            \"policeType\": \"public_security\",\n            \"usage\": \"用于广电总局及省级广电机构开展新媒体运营效果评估、内容监管、用户需求分析，为新媒体内容生产、分发策略优化提供数据支撑，仅向各级广电管理部门授权开放使用。\",\n            \"targetTableId\": \"f370414d9ff64565a9fa06cbaa13d861\",\n            \"catalogName\": \"生成目录信息(sys_gen_info)\",\n            \"recordCount\": \"1\",\n            \"concatName\": \"1\",\n            \"dataDomain\": \"11\",\n            \"dataProcessLevel\": \"4\",\n            \"manageUnit\": \"2026505033717514242\",\n            \"sourceBusinessItem\": \"节目审核-初审\",\n            \"targetDbId\": \"62d25cc8bf1344d6b9f41fed23af4eaf\",\n            \"catalogNameEn\": \"sys_gen_info\",\n            \"dataSourceType\": \"sensitive\",\n            \"sourceTableId\": \"fb64c63470c14ee286a29fcea82a259c\",\n            \"appId\": \"837fa16f795444b29c89ae7973cdd87f\",\n            \"dbId\": \"38ef1b566eb4477ebfc07b4c0c8aa973\",\n            \"orgId\": \"2026505347749249025\",\n            \"concatPhone\": \"13222222222\",\n            \"dataUpdateFreq\": \"2\",\n            \"tableLevel\": \"special\",\n            \"dataLevel\": \"1\",\n            \"updateCycle\": \"monthly\",\n            \"assetDesc\": \"该数据目录归集全国广电新媒体平台用户行为核心数据，涵盖用户基础信息、内容访问记录、互动行为（点赞/评论/分享）、收视时长等维度，数据覆盖国家级新媒体平台及31个省级分平台，支撑广电新媒体运营效果分析、内容精准推送、用户画像构建等核心业务场景。\",\n            \"dataTheme\": \"item,vehicle,track\",\n            \"dataSecurityLevel\": \"1\",\n            \"dataTimeScope\": \"2026-02-10 00:00:00,2026-03-18 00:00:00\",\n            \"shareType\": \"1\",\n            \"openType\": \"是\",\n            \"dataCategory\": \"01020201\",\n            \"businessBasicCatalogName\": \"广电网络视听数据资源基本目录\",\n            \"resourceSource\": \"video\",\n            \"tableType\": \"数据表\",\n            \"orgPath\": \"000600020001\",\n            \"publishStatus\": \"1\",\n            \"catalogType\": \"部门目录\",\n            \"viewCount\": 2\n        }\n    ]\n}",
  "description": "读取目标数据目录并更新浏览计数，返回最新的目录展示资料。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：04.浏览接口
// 路由：POST /dws/market/view
// 业务说明：读取目标数据目录并更新浏览计数，返回最新的目录展示资料。
import tenantEs;
import "@post:/dst/catalog/list" as assetList;
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import jsons

var list = assetList();
if (list.size() === 1){
    var data = list.each(item=>item.put('viewCount',item.viewCount ? item.viewCount + 1 : 1))[0]
   // 解析请求体并为可选参数设置默认值。
   esCommonService.saveOrUpdate("dataassets", body.tid ,jsons.toJSONStr(data), false);
}
// 返回当前业务分支的标准结果。
return list;
