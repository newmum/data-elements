{
  "properties" : { },
  "id" : "779419e5edd654dd9f851a610cdb6033",
  "script" : null,
  "groupId" : "8b36eff777cc5cc6a8cf7c3048cd8b82",
  "name" : "06.model-validate",
  "createTime" : null,
  "updateTime" : 1791019420687,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/model-validate",
  "description" : null,
  "returnType" : null,
  "mappingPath" : null,
  "parameters" : [ {
    "name" : "input",
    "value" : null,
    "description" : null,
    "required" : false,
    "dataType" : "Object",
    "type" : null,
    "defaultValue" : null,
    "validateType" : null,
    "error" : null,
    "expression" : null,
    "children" : null
  } ]
}
================================
// 04.模型服务端校验。提交者不能以浏览器预检结果替代实体/字段/引用检查。
import tenantRuntime
import '@/resource-center/technical-read' as technicalRead
var tenantId = tenantRuntime.id()
var model = input
var errors = []
var text = v => v == null ? '' : v + ''
var entities = model.entities == null ? [] : model.entities
if (entities.size() == 0 || entities.size() > 300) { errors.add('模型须包含1至300个实体') }
var names = []; var allIds = []; var byId = {}
for (e in entities) {
    if (!text(e.name).matches('[A-Za-z_][A-Za-z0-9_]{0,62}')) { errors.add('实体技术名称不合法：' + text(e.name)) }
    if (names.contains(text(e.name).toLowerCase()) || allIds.contains(e.id)) { errors.add('实体名称或标识重复') }
    names.add(text(e.name).toLowerCase()); allIds.add(e.id); byId[e.id] = e
    var fieldNames = []; var fieldIds = []
    if (e.fields == null || e.fields.size() == 0 || e.fields.size() > 1000) { errors.add('每个实体须包含1至1000个字段'); continue }
    for (f in e.fields) {
        if (!text(f.name).matches('[A-Za-z_][A-Za-z0-9_]{0,62}')) { errors.add('字段技术名称不合法：' + text(f.name)) }
        if (fieldNames.contains(text(f.name).toLowerCase()) || fieldIds.contains(f.id)) { errors.add('字段名称或标识重复：' + e.name) }
        fieldNames.add(text(f.name).toLowerCase()); fieldIds.add(f.id)
        if (!['String','Text','Int32','Int64','Decimal','Boolean','Date','Timestamp','Binary','UUID','JSON','Array','Object'].contains(f.logicalType)) { errors.add('字段逻辑类型无效：' + f.name) }
        if (f.length != null && (f.length < 1 || f.length > 1048576)) { errors.add('字段长度超出允许范围：' + f.name) }
        if (f.precision != null && (f.precision < 1 || f.precision > 38)) { errors.add('数值精度须为1至38：' + f.name) }
        if (f.scale != null && (f.scale < 0 || (f.precision != null && f.scale > f.precision))) { errors.add('数值小数位无效：' + f.name) }
    }
    for (k in e.keys == null ? [] : e.keys) {
        if (k.fieldIds == null || k.fieldIds.size() == 0) { errors.add('键必须包含字段：' + e.name); continue }
        for (fid in k.fieldIds) { if (!fieldIds.contains(fid)) { errors.add('键引用不存在的字段：' + e.name) } }
    }
    e._validatedFields = fieldIds
}
for (r in model.relationships == null ? [] : model.relationships) {
    var source = byId[r.sourceEntityId]; var target = byId[r.targetEntityId]
    if (source == null || target == null) { errors.add('关系端点不属于该模型'); continue }
    if (r.mappings == null || r.mappings.size() == 0) { errors.add('关系缺少字段映射'); continue }
    for (m in r.mappings) {
        if (!source._validatedFields.contains(m.sourceFieldId) || !target._validatedFields.contains(m.targetFieldId)) { errors.add('关系映射引用不存在的字段') }
    }
}
if (text(model.outputEntityId) != '' || model.fusion != null) {
    var output = byId[text(model.outputEntityId)]
    if (output == null || output.role != 'OUTPUT') { errors.add('模型必须指定唯一目标输出表') }
    var sources = []; var outputCount = 0
    for (e in entities) { if (e.role == 'SOURCE') { sources.add(e) }; if (e.role == 'OUTPUT') { outputCount = outputCount + 1 } }
    if (outputCount != 1) { errors.add('模型只能包含一张输出表') }
    var fusion = model.fusion == null ? {joins:[],mappings:[]} : model.fusion
    var joins = fusion.joins == null ? [] : fusion.joins
    var mappings = fusion.mappings == null ? [] : fusion.mappings
    if (sources.size() > 0) {
        var reached = [text(fusion.baseEntityId)]
        if (byId[text(fusion.baseEntityId)] == null || byId[text(fusion.baseEntityId)].role != 'SOURCE') { errors.add('请选择来源驱动表') }
        var joinedRight = []
        for (j in joins) {
            var left = byId[text(j.leftEntityId)]; var right = byId[text(j.rightEntityId)]
            if (left == null || right == null || left.role != 'SOURCE' || right.role != 'SOURCE' || left.id == right.id) { errors.add('融合关联只能连接两张不同的来源表'); continue }
            if (joinedRight.contains(right.id) || right.id == fusion.baseEntityId) { errors.add('每张接入表只能有一条入向关联，驱动表不能作为右表') }; joinedRight.add(right.id)
            if (!['LEFT','INNER'].contains(j.joinType)) { errors.add('关联方式仅支持 LEFT 或 INNER JOIN') }
            if (!['ONE','MANY'].contains(j.rightCardinality) || !['NONE','LATEST'].contains(j.dedupe)) { errors.add('关联基数或重复取值方式无效') }
            if (j.pairs == null || j.pairs.size() == 0) { errors.add('融合关联缺少字段键'); continue }
            var pairKeys=[]
            for (pair in j.pairs) {
                if (!left._validatedFields.contains(pair.leftFieldId) || !right._validatedFields.contains(pair.rightFieldId)) { errors.add('融合关联引用了不存在的字段') }
                var leftField=null;var rightField=null
                for(f in left.fields){if(f.id==pair.leftFieldId){leftField=f}}
                for(f in right.fields){if(f.id==pair.rightFieldId){rightField=f}}
                if(leftField!=null&&rightField!=null&&leftField.logicalType!=rightField.logicalType){errors.add('融合关联字段类型不一致：'+text(leftField.name)+' = '+text(rightField.name))}
                var pairKey=text(pair.leftFieldId)+'/'+text(pair.rightFieldId);if(pairKeys.contains(pairKey)){errors.add('融合关联键重复')};pairKeys.add(pairKey)
            }
            if (j.rightCardinality == 'MANY' && j.dedupe != 'LATEST') { errors.add('右表可能多条时须指定取最新一条，避免结果行数膨胀') }
            if (j.rightCardinality == 'ONE' && j.dedupe != 'NONE') { errors.add('右表关联键唯一时不需要取最新记录') }
            if (j.dedupe == 'LATEST' && !right._validatedFields.contains(j.orderFieldId)) { errors.add('取最新记录需要有效排序字段') }
        }
        var pass = 0
        while (pass < joins.size()) { pass = pass + 1; for (j in joins) {
            if (!reached.contains(text(j.leftEntityId)) || reached.contains(text(j.rightEntityId))) { continue }
            reached.add(text(j.rightEntityId))
        } }
        for (e in sources) { if (!reached.contains(text(e.id))) { errors.add('来源表未通过融合关联接入驱动表：' + text(e.name)) } }
        var mapped = []
        for (mapping in mappings) {
            var from = byId[text(mapping.sourceEntityId)]
            if (from == null || from.role != 'SOURCE' || !from._validatedFields.contains(mapping.sourceFieldId)) { errors.add('输出字段映射来源无效'); continue }
            if (output == null || !output._validatedFields.contains(mapping.targetFieldId)) { errors.add('输出字段映射目标无效'); continue }
            if (mapped.contains(mapping.targetFieldId)) { errors.add('输出字段重复映射：' + text(mapping.targetFieldId)) }
            if (!['DIRECT','TRIM','UPPER','LOWER'].contains(mapping.transform)) { errors.add('输出字段转换方式无效') }
            var sourceField=null;for(f in from.fields){if(f.id==mapping.sourceFieldId){sourceField=f}}
            var targetField=null;for(f in output.fields){if(f.id==mapping.targetFieldId){targetField=f}}
            if(sourceField!=null&&targetField!=null&&sourceField.logicalType!=targetField.logicalType){errors.add('来源与目标字段类型不一致，请先调整目标字段：'+text(targetField.name))}
            if(sourceField!=null&&mapping.transform!='DIRECT'&&!['String','Text'].contains(sourceField.logicalType)){errors.add('文本转换只适用于字符字段：'+text(sourceField.name))}
            mapped.add(mapping.targetFieldId)
        }
        if (output != null) { for (f in output.fields) { if (!mapped.contains(f.id)) { errors.add('输出字段未映射：' + text(f.name)) } } }
    }
}
for (e in entities) { e.remove('_validatedFields') }
return {valid:errors.size() == 0,errors:errors,checks:errors.size() == 0 ? [{level:'INFO',message:'实体、字段、键与关系校验通过'}] : []}
