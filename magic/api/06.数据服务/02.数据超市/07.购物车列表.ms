{
  "properties": {},
  "id": "9fc031d411354d8baff7006b11f5ad0a",
  "script": null,
  "groupId": "dataservice_resource_group_01",
  "name": "07.购物车列表",
  "createTime": 1782955344119,
  "updateTime": 1787153757165,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/shoppingCardList",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"keyword\": \"测试\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": []\n}",
  "description": "按当前登录人员和关键词读取数据超市购物车记录，补充目录展示资料后返回列表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：07.购物车列表
// 路由：POST /dws/market/shoppingCardList
// 业务说明：按当前登录人员和关键词读取数据超市购物车记录，补充目录展示资料后返回列表。
import "@post:/sym/user/me" as me;
import "@post:/dws/market/list" as marketList;
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService


var userInfo = me()
var applyUserId    = userInfo.id
var applyOrgId    = userInfo.orgId

// 从 MySQL 业务表读取当前有效数据。
var cars = db.table('data_market_shopping_cart_t')
                .where()
                .eq('apply_org_id', applyOrgId)
                .eq('is_del', 0)
                .select();

var query = [
        {
            "terms": {
                "tid": cars.map(el=>el.catalogId)
            }
        }
    ]
// 解析请求体并为可选参数设置默认值。
if (body.keyword){
    query.push({
        "wildcard": {
            "catalogName": `*${body.keyword}*`
        }
    })
}
body = {
    query
}

// 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
existInxex = esCommonService.indexExists("dataassets");
if(!existInxex){
    // 返回当前业务分支的标准结果。
    return []
}

var {list} = marketList()

list = list.map(el => {
    var item = cars.find(c => c.catalogId === el.tid)
    // 返回当前业务分支的标准结果。
    return {
        ...el,
        shoppingCarId: item.tid, // 加购记录tid
        addTime: item.createdTime, // 加购时间
    }
})

// 返回当前业务分支的标准结果。
return list
