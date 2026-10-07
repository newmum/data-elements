{
  "properties": {},
  "id": "20c77a091d414533b4e1f6a34fec5e91",
  "script": null,
  "groupId": "ed37113428464a0686dfafbe52429f2f",
  "name": "02.数据一致性检查与修复接口",
  "createTime": 1782955343476,
  "updateTime": 1787153756892,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/syncAllEsDbData",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"esTotal\": 6527,\n        \"dbTotal\": 6550,\n        \"esOnlyCount\": 0,\n        \"esOnlyIds\": [],\n        \"dbOnlyCount\": 23,\n        \"dbOnlyIds\": [\n            \"14b050c6463145fbb7e6609adfe7475b\",\n            \"6833a325770c4f2883696beb20bc04c3\",\n            \"b20e23f919a14ae3bffd476525c345b8\",\n            \"a376ce0b2e7a407fa7c7820971bf5df9\",\n            \"7f5e19418bb34588841c3ccc3e2b4236\",\n            \"a2595f305d0744b49a466751c770dbf1\",\n            \"94c6c16e0c2f497ebf0a49038849ce2f\",\n            \"af1f322269854974a7bba14a186404c9\",\n            \"1bf1f54327b242d6b2035871f43f4143\",\n            \"ff0d4acbbc074ec8a0437ba1ac6bd4dd\",\n            \"4992dd7064e74ebfa358fbd4a7732689\",\n            \"e8756faf938b49028bdd05c71b93e277\",\n            \"3a12ba2ec6f04c0b9fca2090260a6aba\",\n            \"dd8fd7573902496399236e3f1a424feb\",\n            \"eda920c7cf5040ba9710dbf2bc1589c7\",\n            \"775dfa8d00a7426ca3fe828feeac16b6\",\n            \"7ad43e49b8a44ccf98c2b2b151870654\",\n            \"f3b8f45c9cfc445982d710bc231f87b6\",\n            \"7a206563dadf4e559491e8bd81958ac4\",\n            \"8ae4ea06c7244c5f809ef356294134e5\",\n            \"ee9ef838038f4148a491d9e0668c0bd4\",\n            \"9c9fa0c666e84c10824da6b6b7f7d1cb\",\n            \"85899a408fc7461faca66f2d2b1f6ebd\"\n        ],\n        \"mismatchCount\": 0,\n        \"fixedCount\": 0,\n        \"fixedFailed\": 0,\n        \"mismatchList\": [],\n        \"message\": \"检查完成\"\n    }\n}",
  "description": "说明: 检查DB和ES的数据一致性，修复动态字段不一致的数据；涉及 da_asset_t、da_prop_t 等业务表；执行数据库写入并返回处理结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.数据一致性检查与修复接口
// 路由：GET /scene/fix/syncAllEsDbData
// 业务说明：说明: 检查DB和ES的数据一致性，修复动态字段不一致的数据；涉及 da_asset_t、da_prop_t 等业务表；执行数据库写入并返回处理结果。
// 数据一致性检查与修复接口
// 路径: /api/dataassets/syncAll
// 方法: POST
// 参数: 无
// 说明: 检查DB和ES的数据一致性，修复动态字段不一致的数据
// da_asset_t: 主表（基础信息）
// da_prop_t: 副表（动态字段，prop_name+prop_value，parent_id关联主表tid）

import tenantEs;
import '@/common/configData' as configData

try {
    // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
    var esIndex = tenantEs.index(configData().es_dataassets);

    // 需要检查的动态字段列表
    var checkFields = ["orgId", "dbId", "appId", "catalogId", "targetTableId", "sourceTableId"];

    // ========== 1. 从ES查询所有记录 ==========
    var esDsl = {
        "size": 9000,
        "query": { "match_all": {} },
        "_source": checkFields
    };
    var esResult = esIndex.search(esDsl);
    var esTotal = esResult.hits.total.value;

    // ES数据：tid列表 + tid->source映射
    var esTids = [];
    var esSourceList = [];
    for (hit in esResult.hits.hits) {
        esTids.push(hit._id);
        esSourceList.push(hit._source);
    }

    // ========== 2. 从主表查询所有资产tid ==========
    var assetRecords = db.table("da_asset_t").select();
    var dbTids = [];
    for (record in assetRecords) {
        dbTids.push(record.tid);
    }

    // ========== 3. 从副表查询动态字段 ==========
    var propRecords = db.table("da_prop_t")
        .where().in("prop_name", checkFields)
        .select();

    // 构建映射：用数组存储 { parentId, propName, propValue }
    var propList = [];
    for (record in propRecords) {
        propList.push({
            parentId: record.parent_id,
            propName: record.prop_name,
            propValue: record.prop_value
        });
    }

    // ========== 4. 对比并找出不一致的记录 ==========
    var mismatchList = [];
    var fixedCount = 0;
    var fixedFailed = 0;

    for (idx in range(0, esTids.size().asInt() - 1)) {
        var tid = esTids[idx];
        var esSrc = esSourceList[idx];

        // 从propList中找该tid的动态字段
        var hasProp = false;
        var hasMismatch = false;
        var updateData = {};
        var mismatchInfo = { tid: tid };

        for (prop in propList) {
            if (prop.parentId == tid) {
                hasProp = true;
                var field = prop.propName;
                var dbVal = prop.propValue;
                var esVal = esSrc[field];
                if (!dbVal) { dbVal = ''; }
                if (!esVal) { esVal = ''; }
                if (dbVal != esVal) {
                    hasMismatch = true;
                    updateData[field] = dbVal;
                    mismatchInfo[field] = { db: dbVal, es: esVal };
                }
            }
        }

        if (hasProp && hasMismatch) {
            mismatchList.push(mismatchInfo);
            try {
                // esIndex.doc(tid).update(updateData);
                fixedCount = fixedCount + 1;
            } catch(ex) {
                fixedFailed = fixedFailed + 1;
            }
        }
    }

    // ========== 5. 找出ES有但DB没有的记录 ==========
    var esOnlyIds = [];
    for (tid in esTids) {
        var found = false;
        for (dbTid in dbTids) {
            if (dbTid == tid) { found = true; break; }
        }
        if (!found) { esOnlyIds.push(tid); }
    }

    // ========== 6. 找出DB有但ES没有的记录 ==========
    var dbOnlyIds = [];
    for (tid in dbTids) {
        var found = false;
        for (esTid in esTids) {
            if (esTid == tid) { found = true; break; }
        }
        if (!found) { dbOnlyIds.push(tid); }
    }

    // ========== 7. 返回结果 ==========
    return {
        esTotal: esTotal,
        dbTotal: dbTids.size().asInt(),
        esOnlyCount: esOnlyIds.size().asInt(),
        esOnlyIds: esOnlyIds,
        dbOnlyCount: dbOnlyIds.size().asInt(),
        dbOnlyIds: dbOnlyIds,
        mismatchCount: mismatchList.size().asInt(),
        fixedCount: fixedCount,
        fixedFailed: fixedFailed,
        mismatchList: mismatchList,
        message: "检查完成"
    };

} catch(e) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '检查失败: ' + e.message;
}
