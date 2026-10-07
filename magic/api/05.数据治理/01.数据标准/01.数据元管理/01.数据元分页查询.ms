{
  "properties" : { },
  "id" : "5b83c2702e9b4c9687392a291ca9162a",
  "script" : null,
  "groupId" : "55c792d1ba0f4cb79b01a6d5c42dacea",
  "name" : "01.数据元分页查询",
  "createTime" : 1782955344950,
  "updateTime" : 1791107424921,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/page",
  "method" : "POST",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : "{\n    \"pageNum\": \"1\",\n    \"pageSize\": \"10\",\n    \"categoryPath\": \"0,1891200000000000003,1891200000000000301\"\n}",
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : null,
  "description" : "根据名称、编码、标准编码、分级、分类及发布状态筛选数据元，返回分页记录和总数。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : {
    "name" : "",
    "value" : "",
    "description" : "",
    "required" : false,
    "dataType" : "Object",
    "type" : null,
    "defaultValue" : null,
    "validateType" : "",
    "error" : "",
    "expression" : "",
    "children" : [ {
      "name" : "code",
      "value" : "100120",
      "description" : "",
      "required" : false,
      "dataType" : "Integer",
      "type" : null,
      "defaultValue" : null,
      "validateType" : "",
      "error" : "",
      "expression" : "",
      "children" : [ ]
    }, {
      "name" : "msg",
      "value" : "user_no_login",
      "description" : "",
      "required" : false,
      "dataType" : "String",
      "type" : null,
      "defaultValue" : null,
      "validateType" : "",
      "error" : "",
      "expression" : "",
      "children" : [ ]
    }, {
      "name" : "success",
      "value" : "false",
      "description" : "",
      "required" : false,
      "dataType" : "Boolean",
      "type" : null,
      "defaultValue" : null,
      "validateType" : "",
      "error" : "",
      "expression" : "",
      "children" : [ ]
    }, {
      "name" : "data",
      "value" : "null",
      "description" : "",
      "required" : false,
      "dataType" : "Object",
      "type" : null,
      "defaultValue" : null,
      "validateType" : "",
      "error" : "",
      "expression" : "",
      "children" : [ ]
    } ]
  }
}
================================
// 接口：01.数据元分页查询
// 路由：POST /dwm/standard/element/page
// 业务说明：根据名称、编码、标准编码、分级、分类及发布状态筛选数据元，返回分页记录和总数。
// 核心业务逻辑：按接口约定编排当前请求并返回业务处理结果。
import log
import tenantRuntime
import cn.hutool.json.JSONUtil

/**
 * 分页查询数据元管理
 * POST /api/gov/data/meta/page
 * 
 * 支持分页查询数据元管理列表
 */

// 获取分页参数
var pageNum = body?.pageNum::long(0)
var pageSize = body?.pageSize::long(20)
var offsetLimit = pageNum == 0? 0 : (pageNum - 1)* pageSize
var tenantId = tenantRuntime.id()
if (tenantId == null || (tenantId + '').trim() == '') {
    exit 401, '登录状态已失效，请重新登录'
}

// 获取查询条件
var metaName = body?.metaName
var metaCode = body?.metaCode
var standardEncode = body?.standardEncode
var dataLevel = body?.dataLevel
var dataCategoryId = body?.dataCategoryId
var publishStatus = body?.publishStatus
var categoryPath = body?.categoryPath

// 构建查询条件
var query = db.table('da_metadata_t')
    .where()
    .eq('tenant_id', tenantId)
    .eq('is_del', 0)

if (metaName != null && metaName != '') {
    query = query.like('meta_name', '%' + metaName + '%')
}

if (metaCode != null && metaCode != '') {
    query = query.like('meta_code', '%' + metaCode + '%')
}

if (standardEncode != null && standardEncode != '') {
    query = query.like('standard_encode', '%' + standardEncode + '%')
}

if (dataLevel != null && dataLevel != '') {
    query = query.eq('data_level', dataLevel)
}

if (dataCategoryId != null && dataCategoryId != '') {
    query = query.eq('data_category_id', dataCategoryId)
}

if (publishStatus != null) {
    query = query.eq('publish_status', publishStatus)
}

if (categoryPath != null) {
    query = query.like('data_category_path', categoryPath + "%")
}

// ========== 查询总数 ==========
var total = query.count()

// ========== 查询列表 ==========
var list = query
    .orderBy('created_time', 'desc')
    .page(pageSize, offsetLimit)

// 当前页需要展示数据字典项数。先批量取代码记录，再按原详情接口的
// tid -> dict_code -> code_set 优先级回填，不能对每个数据元发一次详情查询。
var pageRows = list?.list == null ? [] : list.list
var domainIds = []
var seenDomainIds = {}
for (row in pageRows) {
    var domainId = row?.valueDomainId == null ? '' : (row.valueDomainId + '').trim()
    if (domainId != '' && seenDomainIds[domainId] == null) {
        domainIds.add(domainId)
        seenDomainIds[domainId] = true
    }
}

var codeByDomainId = {}
if (domainIds.size() > 0) {
    var directCodes = db.table('da_metadata_code_t').where()
        .in('tid', domainIds).eq('tenant_id', tenantId).eq('is_del', 0).select()
    for (code in (directCodes == null ? [] : directCodes)) {
        codeByDomainId[code.tid + ''] = code
    }

    var unresolvedIds = []
    for (domainId in domainIds) {
        if (codeByDomainId[domainId] == null) {
            unresolvedIds.add(domainId)
        }
    }
    if (unresolvedIds.size() > 0) {
        var dictCodeRows = db.table('da_metadata_code_t').where()
            .in('dict_code', unresolvedIds).eq('code_value', '__HEADER__')
            .eq('tenant_id', tenantId).eq('is_del', 0).select()
        for (code in (dictCodeRows == null ? [] : dictCodeRows)) {
            var codeKey = (code.dictCode == null ? code.dict_code : code.dictCode) + ''
            if (codeByDomainId[codeKey] == null) {
                codeByDomainId[codeKey] = code
            }
        }

        var stillUnresolvedIds = []
        for (domainId in unresolvedIds) {
            if (codeByDomainId[domainId] == null) {
                stillUnresolvedIds.add(domainId)
            }
        }
        if (stillUnresolvedIds.size() > 0) {
            var codeSetRows = db.table('da_metadata_code_t').where()
                .in('code_set', stillUnresolvedIds).eq('code_value', '__HEADER__')
                .eq('tenant_id', tenantId).eq('is_del', 0).select()
            for (code in (codeSetRows == null ? [] : codeSetRows)) {
                var codeKey = (code.codeSet == null ? code.code_set : code.codeSet) + ''
                if (codeByDomainId[codeKey] == null) {
                    codeByDomainId[codeKey] = code
                }
            }
        }
    }
}

for (row in pageRows) {
    var domainId = row?.valueDomainId == null ? '' : (row.valueDomainId + '').trim()
    var code = codeByDomainId[domainId]
    var rawValues = code == null ? null : (code.dictItemValue == null ? code.dict_item_value : code.dictItemValue)
    var dictionaryCount = 0
    if (rawValues != null) {
        try {
            var jsonText = (rawValues + '').trim()
            if (JSONUtil.isTypeJSONArray(jsonText)) {
                dictionaryCount = JSONUtil.parseArray(jsonText).size()
            }
        } catch (e) {
            dictionaryCount = 0
        }
    }
    row.dictionaryCount = dictionaryCount
}

log.info('分页查询数据元管理成功, pageNum: {}, pageSize: {}, total: {}', pageNum, pageSize, total)

// ========== 返回结果 ==========
return list
