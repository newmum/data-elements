{
  "properties": {},
  "id": "dwm_metadata_governance_save_relation_01",
  "script": null,
  "groupId": "dwm_metadata_governance_group_01",
  "name": "03.保存逻辑关系",
  "createTime": null,
  "updateTime": 1790614324411,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/relations/save",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "校验当前租户的表、字段映射及条件，新增或按版本修改 ER 逻辑关系；已建立物理外键时不允许悄悄改映射。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：保存逻辑关系
// 业务说明：校验当前租户的表、字段映射及条件，新增或按版本修改 ER 逻辑关系；已建立物理外键时不允许悄悄改映射。
// 此接口只修改租户库中的关系台账，不执行源数据库 DDL。
import tenantRuntime
import metadataGovernance
import cn.hutool.json.JSONUtil
import java.util.UUID

var tenantId = tenantRuntime.id()
var sourceTableId = body?.sourceTableId == null ? '' : ('' + body.sourceTableId).trim()
var targetTableId = body?.targetTableId == null ? '' : ('' + body.targetTableId).trim()
if (sourceTableId == '' || targetTableId == '') { exit 400, '请选择来源数据表和目标数据表' }

// 字段映射和条件校验同时供物理外键预检查复用，检查每个字段都属于本租户对应的表。
var definition = metadataGovernance.prepareDefinition(body)
var mappings = definition.mappings
var sourceColumnId = mappings[0].sourceColumnId
var targetColumnId = mappings[0].targetColumnId
var definitionJson = JSONUtil.toJsonStr(definition)

var relationId = body?.tid == null ? '' : ('' + body.tid).trim()
var name = body?.relationName == null || ('' + body.relationName).trim() == '' ? '逻辑关联' : ('' + body.relationName).trim()
var type = body?.relationType == null ? 'REFERENCE' : ('' + body.relationType).trim().toUpperCase()
var origin = body?.origin == null ? 'MANUAL' : ('' + body.origin).trim().toUpperCase()
var status = body?.status == null ? 'CONFIRMED' : ('' + body.status).trim().toUpperCase()
if (!['REFERENCE','JOIN'].contains(type) || !['MANUAL','INFERENCE'].contains(origin)
    || !['CONFIRMED','SUGGESTED','REJECTED','ARCHIVED'].contains(status)) {
    exit 400, '关系类型、来源或状态不正确'
}
if (name.length() > 160) { exit 400, '关系名称过长' }
var description = body?.description == null ? null : ('' + body.description).trim()
if (description == '') { description = null }
if (description != null && description.length() > 2000) { exit 400, '业务说明最多 2000 字符' }
if (type == 'JOIN' && description == null) { exit 400, '一般连接关系必须填写业务说明' }
var confidence = body?.confidence == null ? 0 : body.confidence::int
if (confidence < 0) { confidence = 0 }
if (confidence > 100) { confidence = 100 }

return db.transaction(() => {
    if (relationId == '') {
        relationId = UUID.randomUUID().toString().replace('-', '')
        db.insert("""
            INSERT INTO dwm_metadata_relation_t
                (tid, tenant_id, source_table_id, source_column_id,
                 target_table_id, target_column_id, relation_name, relation_type,
                 relation_origin, relation_status, confidence, description,
                 created_time, updated_time, is_del, definition_json, version_no)
            VALUES
                (#{relationId}, #{tenantId}, #{sourceTableId}, #{sourceColumnId},
                 #{targetTableId}, #{targetColumnId}, #{name}, #{type},
                 #{origin}, #{status}, #{confidence}, #{description},
                 NOW(6), NOW(6), 0, #{definitionJson}, 1)
        """)
    } else {
        // 锁住现有版本；一旦映射已经生成物理外键，就只能先撤销约束再另建逻辑关系。
        var before = db.selectOne("""
            SELECT version_no, definition_json, physical_fk_json, validation_json
              FROM dwm_metadata_relation_t
             WHERE tenant_id = #{tenantId} AND tid = #{relationId} AND is_del = 0
             FOR UPDATE
        """)
        if (before == null) { exit 404, '关系不存在或无权访问' }
        var version = before.versionNo::long
        if (body?.expectedVersion != null && body.expectedVersion::long != version) {
            exit 409, '关系已被其他操作更新，请刷新后重试'
        }
        var previousDefinition = before.definitionJson == null ? {} : JSONUtil.parseObj(before.definitionJson)
        var sameDefinition = previousDefinition.equals(JSONUtil.parseObj(definitionJson))
        var hasPhysicalKey = before.physicalFkJson != null && ('' + before.physicalFkJson).trim() != ''
            && !JSONUtil.parseObj(before.physicalFkJson).isEmpty()
        if (!sameDefinition && hasPhysicalKey) {
            exit 409, '此关系已创建物理外键；请先撤销该约束，再新建不同映射的逻辑关系'
        }
        var validationJson = sameDefinition ? before.validationJson : null
        var changed = db.update("""
            UPDATE dwm_metadata_relation_t
               SET source_table_id = #{sourceTableId},
                   source_column_id = #{sourceColumnId},
                   target_table_id = #{targetTableId},
                   target_column_id = #{targetColumnId},
                   relation_name = #{name}, relation_type = #{type},
                   relation_origin = #{origin}, relation_status = #{status},
                   confidence = #{confidence}, description = #{description},
                   definition_json = #{definitionJson}, validation_json = #{validationJson},
                   updated_time = NOW(6), version_no = version_no + 1
             WHERE tenant_id = #{tenantId} AND tid = #{relationId}
               AND is_del = 0 AND version_no = #{version}
        """)
        if (changed != 1) { exit 409, '关系不存在或已被其他操作更新' }
    }

    // 保持原接口返回结构，附上两侧表和字段名称供页面直接展示。
    return db.selectOne("""
        SELECT r.*,
               st.table_name AS source_table_name,
               sc.column_name AS source_column_name,
               tt.table_name AS target_table_name,
               tc.column_name AS target_column_name
          FROM dwm_metadata_relation_t r
          JOIN db_table_t st ON st.tid = r.source_table_id AND st.tenant_id = r.tenant_id AND st.is_del = 0
          JOIN db_table_column_t sc ON sc.tid = r.source_column_id AND sc.table_id = st.tid
                                    AND sc.tenant_id = r.tenant_id AND sc.is_del = 0
          JOIN db_table_t tt ON tt.tid = r.target_table_id AND tt.tenant_id = r.tenant_id AND tt.is_del = 0
          JOIN db_table_column_t tc ON tc.tid = r.target_column_id AND tc.table_id = tt.tid
                                    AND tc.tenant_id = r.tenant_id AND tc.is_del = 0
         WHERE r.tenant_id = #{tenantId} AND r.tid = #{relationId} AND r.is_del = 0
    """)
})
