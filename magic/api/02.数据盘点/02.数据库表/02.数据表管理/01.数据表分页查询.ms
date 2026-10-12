{
  "properties": {},
  "id": "datapd_db_table_page_01",
  "script": null,
  "groupId": "datapd_db_table_group_01",
  "name": "01.数据表分页查询",
  "createTime": 1784454007655,
  "updateTime": 1790394629431,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"pageNum\": 1,\n    \"pageSize\": 20,\n    \"dbId\": \"可选数据源ID\",\n    \"conditions\": [\n        {\n            \"field\": \"assetType\",\n            \"type\": \"match\",\n            \"value\": \"app\"\n        }\n    ],\n    \"keyword\": \"测试\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "数据表管理 01：分页查询已登记数据表；使用请求参数 tid、dbId、datasourceId、conditions、keyword、pageNum、pageSize；涉及 db_table_t、db_datasource_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.数据表分页查询
// 路由：POST /dst/database/table/page
// 业务说明：数据表管理 01：分页查询已登记数据表；使用请求参数 tid、dbId、datasourceId、conditions、keyword、pageNum、pageSize；涉及 db_table_t、db_datasource_t 等业务表。
import java.util.Map
import tenantRuntime
var tenantId = tenantRuntime.id()

/*
 * 数据表管理 01：分页查询已登记数据表。
 *
 * 主数据及全部表属性直接读取 db_table_t，数据源名称读取 db_datasource_t。
 * 本接口只读，不访问物理业务库、不写 MySQL 或 Elasticsearch。
 */

var textOf = (value) => value == null ? '' : value + ''
var blank = (value) => value == null || textOf(value).trim() == ''
var lower = (value) => textOf(value).trim().toLowerCase()
var rowValue = (row, keys) => {
    if (row == null) {
        // 返回当前业务分支的标准结果。
        return null
    }
    for (key in keys) {
        var value = row instanceof Map ? row.get(key) : row[key]
        if (value != null) {
            // 返回当前业务分支的标准结果。
            return value
        }
    }
    // 返回当前业务分支的标准结果。
    return null
}
var firstValue = (row, keys) => {
    for (key in keys) {
        var value = rowValue(row, [key])
        if (!blank(value)) {
            // 返回当前业务分支的标准结果。
            return value
        }
    }
    // 返回当前业务分支的标准结果。
    return null
}
var putIfPresent = (target, name, value) => {
    if (value != null) {
        target[name] = value
    }
}
var tableData = (row) => {
    var tid = rowValue(row, ['tid'])
    var item = {
        tid: tid,
        id: tid,
        assetType: 'table',
        datasourceId: firstValue(row, ['datasourceId', 'datasource_id']),
        dbId: firstValue(row, ['datasourceId', 'datasource_id']),
        tableName: firstValue(row, ['tableName', 'table_name']),
        tableNameEn: firstValue(row, ['tableNameEn', 'table_name_en', 'tableName', 'table_name']),
        tableNameCn: firstValue(row, ['tableNameCn', 'table_name_cn', 'tableComment', 'table_comment']),
        tableComment: firstValue(row, ['tableComment', 'table_comment', 'tableNameCn', 'table_name_cn']),
        tableType: firstValue(row, ['tableType', 'table_type']),
        businessType: firstValue(row, ['businessType', 'business_type']),
        businessTypeReason: firstValue(row, ['businessTypeReason', 'business_type_reason']),
        annotated: rowValue(row, ['annotated']),
        recordCount: rowValue(row, ['recordCount', 'record_count']),
        fieldCount: rowValue(row, ['fieldCount', 'field_count']),
        storageSize: firstValue(row, ['storageSize', 'storage_size']),
        totalSizeBytes: rowValue(row, ['totalSizeBytes', 'total_size_bytes']),
        totalSizeFormatted: firstValue(row, ['totalSizeFormatted', 'total_size_formatted', 'storageSize', 'storage_size']),
        ddlCreatTime: firstValue(row, ['ddlCreateTime', 'ddl_create_time']),
        ddlUpdateTime: firstValue(row, ['ddlUpdateTime', 'ddl_update_time']),
        orgId: firstValue(row, ['orgId', 'org_id']),
        orgPath: firstValue(row, ['orgPath', 'org_path']),
        relatedDirectory: firstValue(row, ['relatedDirectory', 'related_directory']),
        dataSourceType: firstValue(row, ['dataSourceType', 'data_source_type']),
        timestampField: firstValue(row, ['timestampField', 'timestamp_field']),
        dictionaryStructureType: firstValue(row, ['dictionaryStructureType', 'dictionary_structure_type']),
        dictionaryTableFlag: rowValue(row, ['dictionaryTableFlag', 'dictionary_table_flag']),
        fieldGovernanceConfig: rowValue(row, ['fieldGovernanceConfig', 'field_governance_config']),
        dictionaryProfiles: rowValue(row, ['dictionaryProfiles', 'dictionary_profiles']),
        dictionaryProfile: rowValue(row, ['dictionaryProfile', 'dictionary_profile']),
        dictionaryCategories: rowValue(row, ['dictionaryCategories', 'dictionary_categories']),
        sourceCatalogId: firstValue(row, ['sourceCatalogId', 'source_catalog_id']),
        sourceTableId: firstValue(row, ['sourceTableId', 'source_table_id', 'tid']),
        sourceTableName: firstValue(row, ['sourceTableName', 'source_table_name', 'tableName', 'table_name']),
        manageUnit: firstValue(row, ['manageUnit', 'manage_unit', 'orgId', 'org_id']),
        assetDesc: firstValue(row, ['assetDesc', 'asset_desc', 'tableComment', 'table_comment']),
        catalogName: firstValue(row, ['catalogName', 'catalog_name', 'tableNameCn', 'table_name_cn']),
        catalogNameEn: firstValue(row, ['catalogNameEn', 'catalog_name_en', 'tableName', 'table_name']),
        tenantId: firstValue(row, ['tenantId', 'tenant_id']),
        assetStatus: rowValue(row, ['assetStatus', 'asset_status']),
        flowStatus: rowValue(row, ['flowStatus', 'flow_status']),
        regTime: rowValue(row, ['regTime', 'reg_time']),
        createdTime: rowValue(row, ['createdTime', 'created_time']),
        updatedTime: rowValue(row, ['updatedTime', 'updated_time'])
    }
    item.regTime_date = item.regTime
    item.createdTime_date = item.createdTime
    item.updatedTime_date = item.updatedTime
    // 返回当前业务分支的标准结果。
    return item
}
var matches = (item, condition) => {
    if (condition == null || blank(condition.field)) {
        // 返回当前业务分支的标准结果。
        return true
    }
    var actual = item[condition.field]
    var expected = condition.value
    var type = blank(condition.type) ? 'match' : condition.type
    if (type == 'null') {
        // 返回当前业务分支的标准结果。
        return blank(actual)
    }
    if (type == 'like') {
        // 返回当前业务分支的标准结果。
        return lower(actual).indexOf(lower(expected)) >= 0
    }
    if (type == 'in') {
        if (expected == null) {
            // 返回当前业务分支的标准结果。
            return true
        }
        for (value in expected) {
            if (textOf(actual) == textOf(value)) {
                // 返回当前业务分支的标准结果。
                return true
            }
        }
        // 返回当前业务分支的标准结果。
        return false
    }
    // 返回当前业务分支的标准结果。
    return textOf(actual) == textOf(expected)
}

// Sort and page the authorized, deduplicated source-table set in the database.
import tableSort;
import dataScope;
import java.util.List;
var whereSql=' WHERE t.tenant_id=#{tenantId} AND t.is_del=0 AND t._row_rank=1';
var scope=dataScope.resource('MENU:2081000000000000002');
var scopeUserId=scope.userId;
var scopeOrgIds=scope.organizationIds;
if(scope.scope=='OWNER') {whereSql=whereSql+' AND d.created_by=#{scopeUserId}';}
else if(scope.scope=='ORG') {whereSql=whereSql+(scopeOrgIds.size()==0?' AND 1=0':' AND d.org_id IN (#{scopeOrgIds})');}
else if(scope.scope!='ALL') {exit 403,'数据范围无效';}
var fields={tid:'t.tid',dbId:'t.datasource_id',datasourceId:'t.datasource_id',tableName:'t.table_name',
    tableNameEn:'t.table_name',tableNameCn:'t.table_name_cn',tableComment:'t.table_comment',
    appId:'d.app_id',orgId:'coalesce(t.org_id,d.org_id)',businessType:'t.business_type',
    dataSourceType:'t.data_source_type',assetStatus:'t.asset_status',fieldCount:'t.field_count',
    recordCount:'t.record_count',createdTime:'t.created_time',updatedTime:'t.updated_time',regTime:'t.reg_time'};
var conditions=[];
if(body?.conditions!=null) {conditions.addAll(body.conditions);}
for(field in ['tid','dbId','datasourceId','businessType','dataSourceType','orgId','assetStatus']) {
    if(!blank(body?.get(field))) {conditions.add({field:field,value:body.get(field),type:'match'});}
}
var values={};
for(i,condition in conditions) {
    var field=condition?.field;
    if(field=='assetType') {if(condition.value!='table') {whereSql=whereSql+' AND 1=0';};continue;}
    if(fields.get(field)==null) {exit 400,'不支持的数据表筛选字段：'+field;}
    var key='filter'+i;
    var type=condition.type||'match';
    values.put(key,condition.value);
    if(type=='null') {whereSql=whereSql+' AND '+fields.get(field)+' IS NULL';}
    else if(type=='in') {
        if(!(condition.value instanceof List) || condition.value.size()>1000) {exit 400,'多值筛选最多 1000 项';}
        whereSql=whereSql+(condition.value.size()==0?' AND 1=0':' AND '+fields.get(field)+' IN (#{values.'+key+'})');
    } else if(type=='like') {
        values.put(key,'%'+textOf(condition.value).replace('!','!!').replace('%','!%').replace('_','!_')+'%');
        whereSql=whereSql+' AND '+fields.get(field)+" LIKE #{values."+key+"} ESCAPE '!'";
    } else if(type=='match' || type=='eq') {whereSql=whereSql+' AND '+fields.get(field)+'=#{values.'+key+'}';}
    else {exit 400,'不支持的数据表筛选方式：'+type;}
}
var keyword=body?.keyword;
if(!blank(keyword)) {
    keyword='%'+textOf(keyword).replace('!','!!').replace('%','!%').replace('_','!_')+'%';
    whereSql=whereSql+" AND (t.table_name LIKE #{keyword} ESCAPE '!' OR t.table_name_cn LIKE #{keyword} ESCAPE '!' OR t.table_comment LIKE #{keyword} ESCAPE '!')";
}
var fromSql="""
 FROM (SELECT original.*,ROW_NUMBER() OVER (
    PARTITION BY original.tenant_id,original.datasource_id,
        CASE WHEN original.table_name IS NULL OR trim(original.table_name)='' THEN original.tid ELSE lower(original.table_name) END
    ORDER BY coalesce(original.field_count,0) DESC,coalesce(original.annotated,0) DESC,original.updated_time DESC,original.tid ASC
 ) _row_rank FROM db_table_t original WHERE original.tenant_id=#{tenantId} AND original.is_del=0) t
 JOIN db_datasource_t d ON d.tid=t.datasource_id AND d.tenant_id=t.tenant_id AND d.is_del=0
""";
var pageNum=Math.max(1,body?.pageNum::int(1));
var pageSize=Math.min(100,Math.max(1,body?.pageSize::int(20)));
var offset=(pageNum-1)*pageSize;
var allowed={tableName:"coalesce(nullif(t.table_name_cn,''),nullif(t.table_comment,''),t.table_name)",tableNameEn:'t.table_name',
    tableNameCn:'t.table_name_cn',name:"coalesce(nullif(t.table_name_cn,''),nullif(t.table_comment,''),t.table_name)",
    fieldCount:'t.field_count',recordCount:'t.record_count',totalSizeBytes:'t.total_size_bytes',
    ddlCreatTime:'t.ddl_create_time',ddlCreateTime:'t.ddl_create_time',ddlUpdateTime:'t.ddl_update_time',
    regTime:'t.reg_time',createdTime:'t.created_time',updatedTime:'t.updated_time'};
var field=tableSort.field(body,allowed,'updatedTime');
var direction=tableSort.direction(body);
var total=db.selectOne('SELECT COUNT(*) total'+fromSql+whereSql).total;
var rows=db.select('SELECT t.*,d.db_name,d.app_id'+fromSql+whereSql+' ORDER BY CASE WHEN '+field+' IS NULL THEN 1 ELSE 0 END ASC,'+field+' '+direction+',t.tid ASC LIMIT #{pageSize} OFFSET #{offset}');
return {pageNum:pageNum,pageSize:pageSize,total:total,list:rows.map(row=>{
    var item=tableData(row);item.dbName=rowValue(row,['dbName','db_name']);item.appId=rowValue(row,['appId','app_id']);return item;
})};
