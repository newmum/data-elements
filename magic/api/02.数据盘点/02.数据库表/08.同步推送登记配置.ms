{
  "properties": {},
  "id": "c78db2b6d3b04a2ba5d0854d8171a106",
  "script": null,
  "groupId": "datapd_database_group_01",
  "name": "08.同步推送登记配置",
  "createTime": 1792650432292,
  "updateTime": null,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/push/sync",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"all\":true,\"nodeId\":\"当前租户推送节点ID\",\"preview\":true}",
  "headers": [],
  "paths": [],
  "responseBody": "{}",
  "description": "同步当前可信租户内已完成登记的推送数据源配置。all=true、nodeId、preview=true 返回数据源/表清单、节点镜像状态、planHash 和调用地址；只读预览以服务间密钥调用节点一次 STATUS。确认传同一 planHash，清单变化返回409，成功返回逐表结果。原 dryRun 保持本地只读、不调用节点。上限100源/1000表/10000字段/1MiB；逐ID校验当前租户，不接受外部地址或凭据，不签发业务密钥。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tenantRuntime
import cn.hutool.extra.spring.SpringUtil
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.util.LinkedHashMap
import java.util.Map
import cn.hutool.crypto.digest.DigestUtil
import java.lang.Math
import cn.hutool.http.HttpRequest
import cn.hutool.core.util.IdUtil

import java.util.ArrayList
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate

// 权威配置来自当前租户业务库；请求不能指定租户、表字段、地址、调用方或密钥。
var tenantId = tenantRuntime.id()
var audit = LoggerFactory.getLogger('data.push.registry.sync')
if (body == null || !(body instanceof Map)) { exit 400, '请求体必须为对象' }
for (key in body.keySet()) {
    if (!['datasourceId','dryRun','nodeId','all','preview','planHash'].contains(key)) { exit 400, '请求包含未支持的同步参数' }
}
if (body.all != null && body.all != true && body.all != false) { exit 400, 'all必须为布尔值' }
if (body.dryRun != null && body.dryRun != true && body.dryRun != false) { exit 400, 'dryRun必须为布尔值' }
if (body.preview != null && body.preview != true && body.preview != false) { exit 400, 'preview必须为布尔值' }
if ((body.preview==true || body.planHash!=null) && body.all!=true) { exit 400, '清单预览和校验仅用于全部同步' }
if(body.planHash!=null && (!(body.planHash instanceof String) || !body.planHash.matches('[a-f0-9]{64}'))){exit 400,'同步清单标识不合法'}
var nodeId = body.nodeId == null?'':(body.nodeId+'').trim()
var pushNode = null
if (nodeId!='') {
    if (!nodeId.matches('[A-Za-z0-9][A-Za-z0-9_-]{0,63}')) { exit 400, '推送节点标识不合法' }
    pushNode = db.table('push_node_t').where().eq('tenant_id',tenantId).eq('tid',nodeId).eq('is_del',0).selectOne()
    if (pushNode==null) { exit 403, '推送节点不存在或无权访问' }
    if (pushNode.enabled!=1 && pushNode.enabled!=true) { exit 409, '推送节点已停用，请先配置并启用节点' }
}
// all=true is a bounded batch operation. Every source is tenant-filtered and
// passed through the same role policy before metadata is read in sets.
if (body.all == true) {
    if (pushNode == null) { exit 400, '同步全部配置必须选择推送节点' }
    if (body.datasourceId != null) { exit 400, '全部同步不能同时指定单个数据源' }
    var bm = new ObjectMapper()
    var str = value => value == null ? '' : (value+'').trim()
    var value = (row,camel,snake) => row[camel] == null ? row[snake] : row[camel]
    var sourcePage = db.table('db_datasource_t').where().eq('tenant_id',tenantId).eq('is_del',0).orderBy('tid').page(1001,0)
    if (sourcePage.total > 1000) { exit 413, '租户数据源超过1000项，请先缩小同步范围' }
    var visible = (sourcePage.list)
    var pending = []
    var skipped = []
    for (row in visible) {
        var cfg = {}
        try { cfg = bm.readValue(str(value(row,'poolCfg','pool_cfg')) || '{}',Map) } catch(e) { continue }
        var mode = str(cfg.accessMode || cfg.access_mode || cfg.dataAccessMode || cfg.data_access_mode).toLowerCase()
        if (!['receive','push'].contains(mode)) { continue }
        var sourceName = str(value(row,'dbName','db_name')) || str(row.tid)
        var reason = ''
        if (![1,2].contains(value(row,'assetStatus','asset_status')::int)) { reason = '数据源登记尚未完成' }
        else if (cfg.pushDelivery?.enabled == false || cfg.push_delivery?.enabled == false) { reason = '推送功能已停用' }
        if (reason != '') { skipped.add({datasourceId:row.tid,datasourceName:sourceName,reason:reason});continue }
        pending.add({row:row,config:cfg,name:sourceName})
    }
    if (pending.size()+skipped.size()>100) { exit 413, '单次全部同步最多100个推送数据源，请调整配置规模' }
    var ids = pending.map(item => item.row.tid)
    var tables = []
    var columns = []
    var schemas = []
    var apps = []
    if (ids.size()>0) {
        var tp = db.table('db_table_t').where().eq('tenant_id',tenantId).in('datasource_id',ids).eq('asset_status',2).and(w => w.ne('table_type','FILE').or().isNull('table_type')).and(w => w.eq('is_del',0).or().isNull('is_del')).orderBy('table_name').orderBy('tid').page(1001,0)
        if (tp.total>1000) { exit 413, '单次全部同步最多1000张表' }
        tables = tp.list
        var tableIds = tables.map(item => item.tid)
        if (tableIds.size()>0) {
            var cp = db.table('db_table_column_t').where().eq('tenant_id',tenantId).in('table_id',tableIds).and(w => w.eq('is_del',0).or().isNull('is_del')).orderBy('ordinal_position').orderBy('tid').page(10001,0)
            if (cp.total>10000) { exit 413, '单次全部同步最多10000个字段' }
            columns = cp.list
            schemas = db.table('data_push_schema_t').where().eq('tenant_id',tenantId).in('datasource_id',ids).in('table_id',tableIds).eq('is_del',0).select()
        }
        var appIds = pending.map(item => value(item.row,'appId','app_id')).filter(id => id != null && str(id) != '').distinct()
        if (appIds.size()>0) { apps = db.table('sym_application_t').where().eq('tenant_id',tenantId).in('tid',appIds).eq('is_del',0).select() }
    }
    var columnsByTable = {}
    var tablesBySource = {}
    var oldByTable = {}
    var appsById = {}
    for (col in columns) { var id = str(value(col,'tableId','table_id'));if (columnsByTable[id]==null) { columnsByTable[id]=[] };columnsByTable[id].add(col) }
    for (table in tables) { var id=str(value(table,'datasourceId','datasource_id'));if(tablesBySource[id]==null){tablesBySource[id]=[]};tablesBySource[id].add(table) }
    for (schema in schemas) { oldByTable[str(value(schema,'datasourceId','datasource_id'))+':'+str(value(schema,'tableId','table_id'))]=schema }
    for (app in apps) { appsById[str(app.tid)]=str(value(app,'appName','app_name')) }
    var snapshots = []
    var publications = []
    var sourceIds = []
    var totalTables = 0
    var totalColumns = 0
    for (item in pending) {
        var id = str(item.row.tid)
        var sourceTables = tablesBySource[id] || []
        var normalized = []
        var sourcePublications = []
        var count = 0
        var reason = ''
        if (sourceTables.size()==0 && str(item.config.pushDataType).toUpperCase()!='UNSTRUCTURED') { reason='尚无已完成登记的数据表' }
        if (sourceTables.size()>100) { reason='数据表超过单数据源100张上限' }
        var seenTables = []
        for (table in sourceTables) {
            var tableId = str(table.tid)
            var tableName = str(value(table,'tableName','table_name'))
            if (!tableName.matches('[A-Za-z0-9_.-]{1,200}') || seenTables.contains(tableName.toLowerCase())) { reason='数据表名称不合法或重复' }
            seenTables.add(tableName.toLowerCase())
            var fields = columnsByTable[tableId] || []
            if (fields.size()==0) { reason='存在已登记但未配置有效字段的数据表' }
            var normalizedFields = []
            var canonical = []
            var attachmentPolicies = []
            var attachmentByName = {}
            var governanceRaw = value(table,'fieldGovernanceConfig','field_governance_config')
            var governance = governanceRaw instanceof Map ? governanceRaw : (str(governanceRaw)=='' ? {} : bm.readValue(str(governanceRaw),Map))
            for (attachment in governance.attachmentFields || []) { attachmentByName[str(attachment.columnName)] = attachment }
            var seenFields = []
            for (field in fields) {
                var fieldName = str(value(field,'columnName','column_name'))
                var type = str(value(field,'columnType','column_type')) || str(value(field,'dataType','data_type'))
                var ordinal = (value(field,'ordinalPosition','ordinal_position') || 0)::int
                var required = (field.nullable == null?1:field.nullable)::int == 0 || (value(field,'primaryKey','primary_key') || 0)::int == 1
                if (!fieldName.matches('[A-Za-z0-9_.-]{1,200}') || seenFields.contains(fieldName.toLowerCase()) || ordinal<1 || ordinal>10000 || type.length()>100) { reason='字段名称、类型或顺序不符合推送契约要求' }
                seenFields.add(fieldName.toLowerCase())
                normalizedFields.add({columnName:fieldName,columnComment:str(value(field,'columnComment','column_comment')).substring(0,Math.min(str(value(field,'columnComment','column_comment')).length(),255)),dataType:type,ordinal:ordinal,required:required,enabled:true})
                // Keep the canonical property order identical to DataPushSchemaService.
                var canonicalField = {name:fieldName,type:type,required:required,ordinal:ordinal}
                var attachment = attachmentByName[fieldName]
                if (attachment != null) {
                    var policy = {columnName:fieldName,maxFileBytes:attachment.maxFileBytes::long,allowedExtensions:attachment.allowedExtensions}
                    canonicalField.attachment = policy
                    attachmentPolicies.add(policy)
                }
                canonical.add(canonicalField)
            }
            var hash = DigestUtil.sha256Hex(bm.writeValueAsString(canonical))
            var old = oldByTable[id+':'+tableId]
            var version = old == null ? 1L : value(old,'schemaVersion','schema_version')::long + (str(value(old,'schemaHash','schema_hash')) == hash ? 0L : 1L)
            var schemaId = old == null ? IdUtil.getSnowflakeNextIdStr() : str(old.tid)
            var delivery = new LinkedHashMap(item.config.pushDelivery || {})
            delivery.attachmentFields = attachmentPolicies
            sourcePublications.add({old:old,schemaId:schemaId,tableId:tableId,tableName:tableName,version:version,hash:hash,sourceId:id,delivery:bm.writeValueAsString(delivery),fields:canonical})
            var comment = str(value(table,'tableNameCn','table_name_cn')) || str(value(table,'tableComment','table_comment')) || tableName
            normalized.add({tableId:tableId,tableName:tableName,tableComment:comment.substring(0,Math.min(comment.length(),255)),schemaVersion:version+'',enabled:true,columns:normalizedFields})
            count += fields.size()
        }
        if (reason != '') { skipped.add({datasourceId:id,datasourceName:item.name,reason:reason});continue }
        var appId = str(value(item.row,'appId','app_id'))
        snapshots.add({datasourceId:id,datasourceName:item.name,sourceSystemId:appId,sourceSystemName:appsById[appId] || '',contractVersion:new Date().getTime()+'',enabled:true,clientIds:['push-'+id],tables:normalized})
        publications.addAll(sourcePublications);sourceIds.add(id);totalTables += normalized.size();totalColumns += count
    }
    var targetUrl=str(pushNode.baseUrl || pushNode.base_url).replaceAll('/+$','')+'/api/v1/data-push'
    var planSources=snapshots.map(snapshot=>{datasourceId:snapshot.datasourceId,datasourceName:snapshot.datasourceName,sourceSystemId:snapshot.sourceSystemId,sourceSystemName:snapshot.sourceSystemName,enabled:snapshot.enabled,clientIds:snapshot.clientIds,tables:snapshot.tables})
    var planHash=DigestUtil.sha256Hex(bm.writeValueAsString({tenantId:tenantId,nodeId:nodeId,targetUrl:targetUrl,datasources:planSources}))
    if(body.planHash!=null && str(body.planHash)!=planHash){exit 409, '同步清单已变化，请刷新清单后重新确认'}
    var rows=[]
    for(snapshot in snapshots){for(table in snapshot.tables){rows.add({datasourceId:snapshot.datasourceId,datasourceName:snapshot.datasourceName,tableId:table.tableId,tableName:table.tableName,tableComment:table.tableComment,columnCount:table.columns.size(),schemaVersion:table.schemaVersion,definitionHash:DigestUtil.sha256Hex(bm.writeValueAsString([table.tableId,table.tableName,table.tableComment,table.schemaVersion,table.enabled,table.columns.map(field=>[field.columnName,field.columnComment,field.dataType,field.ordinal,field.required,field.enabled])])),syncStatus:'UNKNOWN',syncedAt:null})}}
    var result = {synced:true,all:true,nodeId:nodeId,datasourceCount:snapshots.size(),tableCount:totalTables,columnCount:totalColumns,skipped:skipped,sources:snapshots.map(snapshot=>{datasourceId:snapshot.datasourceId,datasourceName:snapshot.datasourceName,tableCount:snapshot.tables.size()}),rows:rows,planHash:planHash,requestPath:'/dst/database/push/sync',targetUrl:targetUrl}
    var envelope = {deliveryType:'REGISTRY_SYNC',datasources:snapshots}
    var encoded = bm.writeValueAsString(envelope)
    if (bm.writeValueAsBytes(envelope).length>1048576) { exit 413, '全部推送配置超过1MiB，请调整配置规模' }
    if(body.preview==true){
        result.synced=false;result.valid=true;result.dryRun=true
        var statusResponse=null
        try{
            if(rows.size()>0){
                var statusSettings=SpringUtil.getBean('data-element.push-registry-com.linewell.dataelement.dataservice.push.DataPushRegistryProperties')
                if(statusSettings.getStandaloneSyncKey()==''){throw new IllegalStateException('missing server configuration')}
                var statusBody=bm.writeValueAsString({deliveryType:'REGISTRY_SYNC',registryAction:'STATUS',tableRefs:rows.map(row=>{datasourceId:row.datasourceId,tableName:row.tableName})})
                statusResponse=HttpRequest.post(targetUrl).timeout(10000).setFollowRedirects(false).header('Content-Type','application/json').header('X-Standalone-Registry-Key',statusSettings.getStandaloneSyncKey()).body(statusBody).execute()
                if(statusResponse.getStatus()!=200){throw new IllegalStateException('status unavailable')}
                var status=bm.readValue(statusResponse.body(),Map)
                if(status.code!='REGISTRY_STATUS' || status.readOnly!=true || status.tables==null || status.tables.size()>rows.size()){throw new IllegalStateException('status receipt mismatch')}
                var wanted=rows.map(row=>str(row.datasourceId)+':'+str(row.tableName))
                var mirrors={}
                for(mirror in status.tables){var key=str(mirror.datasourceId)+':'+str(mirror.tableName);if(!wanted.contains(key) || mirrors[key]!=null){throw new IllegalStateException('foreign mirror')};mirrors[key]=mirror}
                for(row in rows){var mirror=mirrors[str(row.datasourceId)+':'+str(row.tableName)];row.syncStatus=mirror==null?'NEW':(mirror.active==true && mirror.definitionHash==row.definitionHash?'SYNCED':'CHANGED');row.syncedAt=mirror==null?null:mirror.syncedAt}
            }
            result.statusConfirmed=true
        }catch(e){result.statusConfirmed=false;result.statusMessage='节点暂未返回同步记录，本次仍将同步清单中的全部有效表'}finally{if(statusResponse!=null){statusResponse.close()}}
        return result
    }
    if (body.dryRun == true) { result.synced=false;result.valid=true;result.dryRun=true;return result }
    if (snapshots.size()==0) { return result }
    var settings = SpringUtil.getBean('data-element.push-registry-com.linewell.dataelement.dataservice.push.DataPushRegistryProperties')
    if (settings.getStandaloneSyncKey()=='') { exit 503, '服务端尚未配置同步密钥' }
    // Publish from the batch prefetch only. No reads or single-source service
    // calls are made inside source/table/field iteration.
    var tenantRegistry = SpringUtil.getBean('tenantDataSourceRegistry')
    var batchSource = tenantRegistry.dataSourceForTenant(tenantId)
    var jdbc = new JdbcTemplate(batchSource)
    var tx = new TransactionTemplate(new DataSourceTransactionManager(batchSource))
    var schemaIds = publications.map(pub => pub.schemaId)
    var existingFields = db.table('data_push_schema_field_t').where().eq('tenant_id',tenantId).in('schema_id',schemaIds).select()
    var fieldsByKey = {}
    for (field in existingFields) { fieldsByKey[str(value(field,'schemaId','schema_id')) + ':' + str(value(field,'fieldName','field_name'))] = field }
    // Parameterized multi-row INSERT and CASE UPDATE use one statement per
    // bounded chunk. This also works when MySQL batch rewriting is disabled.
    var insertRows = (table,names,rows) => {
        var offset=0
        while(offset<rows.size()) {
            var chunk=rows.subList(offset,Math.min(offset+500,rows.size()))
            var tuples=[];var args=new ArrayList()
            for (row in chunk) { tuples.add('('+names.map(n => '?').join(',')+')');for(name in names){args.add(row[name])} }
            jdbc.update('INSERT INTO '+table+' ('+names.join(',')+') VALUES '+tuples.join(','),args.toArray())
            offset+=500
        }
    }
    var updateRows = (table,names,rows) => {
        var offset=0
        while(offset<rows.size()) {
            var chunk=rows.subList(offset,Math.min(offset+500,rows.size()))
            var sets=[];var args=new ArrayList()
            for (name in names) {
                var cases=[]
                for (row in chunk) { cases.add('WHEN tid=? THEN ?');args.add(row.tid);args.add(row[name]) }
                sets.add(name+'=CASE '+cases.join(' ')+' ELSE '+name+' END')
            }
            args.add(tenantId);for(row in chunk){args.add(row.tid)}
            jdbc.update('UPDATE '+table+' SET '+sets.join(',')+' WHERE tenant_id=? AND tid IN ('+chunk.map(row => '?').join(',')+')',args.toArray())
            offset+=500
        }
    }
    tx.execute(status => {
        var now = new Date()
        var newSchemas=[];var changedSchemas=[];var newFields=[];var changedFields=[]
        for (pub in publications) {
            var record={tid:pub.schemaId,table_name:pub.tableName,schema_version:pub.version,schema_hash:pub.hash,status:'PUBLISHED',delivery_config:pub.delivery,published_time:now,updated_time:now,datasource_id:pub.sourceId,table_id:pub.tableId,tenant_id:tenantId,is_del:0}
            if (pub.old==null) {newSchemas.add(record)} else {changedSchemas.add(record)}
            for (field in pub.fields) {
                var old=fieldsByKey[pub.schemaId+':'+field.name]
                var row={tid:old==null?IdUtil.getSnowflakeNextIdStr():old.tid,schema_id:pub.schemaId,field_name:field.name,data_type:field.type,required_flag:field.required?1:0,ordinal_position:field.ordinal,tenant_id:tenantId,is_del:0}
                if(old==null){newFields.add(row)}else{changedFields.add(row)}
            }
        }
        insertRows('data_push_schema_t',['tid','datasource_id','table_id','table_name','schema_version','schema_hash','status','delivery_config','tenant_id','published_time','updated_time','is_del'],newSchemas)
        updateRows('data_push_schema_t',['table_name','schema_version','schema_hash','status','delivery_config','published_time','updated_time'],changedSchemas)
        var marks=schemaIds.map(id => '?').join(',')
        var args=new ArrayList();args.add(tenantId);args.addAll(schemaIds)
        jdbc.update('UPDATE data_push_schema_field_t SET is_del=1 WHERE tenant_id=? AND schema_id IN ('+marks+')',args.toArray())
        insertRows('data_push_schema_field_t',['tid','schema_id','field_name','data_type','required_flag','ordinal_position','tenant_id','is_del'],newFields)
        updateRows('data_push_schema_field_t',['data_type','required_flag','ordinal_position','is_del'],changedFields)
        return null
    })
    var response = null
    try {
        response = HttpRequest.post(str(pushNode.baseUrl || pushNode.base_url).replaceAll('/+$','')+'/api/v1/data-push').timeout(30000).setFollowRedirects(false)
            .header('Content-Type','application/json').header('X-Standalone-Registry-Key',settings.getStandaloneSyncKey())
            .header('Idempotency-Key','node-batch-'+nodeId+'-'+IdUtil.fastSimpleUUID()).body(encoded).execute()
        if (response.getStatus()<200 || response.getStatus()>=300) { throw new IllegalStateException('节点未确认保存') }
        var received = bm.readValue(response.body(),Map)
        if (received.datasourceIds==null || received.datasourceIds.size()!=sourceIds.size() || !sourceIds.containsAll(received.datasourceIds) || !received.datasourceIds.containsAll(sourceIds) || received.tableCount!=totalTables || received.columnCount!=totalColumns) { throw new IllegalStateException('节点返回的配置范围不匹配') }
        db.table('push_node_t').where().eq('tenant_id',tenantId).eq('tid',nodeId).eq('is_del',0).update({last_sync_time:new Date(),last_sync_status:'SUCCESS',last_sync_source_id:null})
    } catch(e) {
        db.table('push_node_t').where().eq('tenant_id',tenantId).eq('tid',nodeId).eq('is_del',0).update({last_sync_time:new Date(),last_sync_status:'FAILED',last_sync_source_id:null})
        audit.warn('批量推送登记配置同步未完成，nodeId={}，errorClass={}',nodeId,e.getClass().getName())
        exit 503, '全部配置未能确认保存到海通，请检查节点服务后重试'
    } finally { if(response!=null){response.close()} }
    for(row in rows){row.syncStatus='SUCCESS';row.syncedAt=new Date().getTime()}
    return result
}

var datasourceId = body.datasourceId == null ? '' : (body.datasourceId + '').trim()
if (!datasourceId.matches('[A-Za-z0-9][A-Za-z0-9_-]{0,63}')) { exit 400, 'datasourceId格式不正确' }
if (body.dryRun != null && body.dryRun != true && body.dryRun != false) { exit 400, 'dryRun必须为布尔值' }
var sources = db.select("""
    SELECT d.tid,d.org_id,d.created_by,d.asset_status,d.pool_cfg,d.app_id,
           a.app_name AS source_system_name
      FROM db_datasource_t d
      LEFT JOIN sym_application_t a ON a.tid=d.app_id AND a.tenant_id=d.tenant_id AND a.is_del=0
     WHERE d.tenant_id=#{tenantId} AND d.tid=#{datasourceId} AND d.is_del=0
""")
if (sources == null || sources.size() != 1) { exit 403, '数据源不存在或已删除' }
var source = sources[0]
var mapper = new ObjectMapper()
var rawConfig = source.poolCfg || source.pool_cfg
var config = rawConfig == null || (rawConfig + '').trim() == '' ? {} : mapper.readValue(rawConfig + '',Map)
var mode = (config.accessMode || config.access_mode || config.dataAccessMode || config.data_access_mode || '') + ''
if (!['receive','push'].contains(mode.trim().toLowerCase())) { exit 422, '仅支持接入方式为数据推送的数据源' }
var sourceStatus = source.assetStatus == null ? source.asset_status : source.assetStatus
if (sourceStatus == null || ![1,2].contains(sourceStatus::int)) { exit 409, '请先结束数据源登记并完成需要推送的表登记' }
if (config.pushDelivery?.enabled == config.push_delivery?.enabled == false) { exit 409, '该数据源的推送功能已停用' }

// 一次聚合获取规模；不按表读取字段，也不接受客户端省略需要同步的表。
var counts = db.selectOne("""
    SELECT COUNT(DISTINCT t.tid) AS table_count,COUNT(c.tid) AS column_count,
           COUNT(DISTINCT CASE WHEN c.tid IS NOT NULL THEN t.tid ELSE NULL END) AS tables_with_columns
      FROM db_table_t t
      LEFT JOIN db_table_column_t c ON c.table_id=t.tid AND c.tenant_id=t.tenant_id AND (c.is_del=0 OR c.is_del IS NULL)
     WHERE t.tenant_id=#{tenantId} AND t.datasource_id=#{datasourceId} AND t.asset_status=2 AND (t.is_del=0 OR t.is_del IS NULL)
""")
var tableCount = (counts.tableCount == null ? counts.table_count : counts.tableCount)::int
var columnCount = (counts.columnCount == null ? counts.column_count : counts.columnCount)::int
if (tableCount == 0 || columnCount == 0) { exit 409, '数据源尚无已完成登记且含字段的数据表' }
var tablesWithColumns = (counts.tablesWithColumns == null ? counts.tables_with_columns : counts.tablesWithColumns)::int
if (tablesWithColumns != tableCount) { exit 409, '存在已登记但没有有效字段的数据表，请补齐后同步' }
if (tableCount > 100 || columnCount > 10000) { exit 413, '单次配置同步最多100张已登记表和10000个字段' }
var clientId = 'push-' + datasourceId
if (body.dryRun == true) {
    return {valid:true,dryRun:true,nodeId:nodeId,datasourceId:datasourceId,clientId:clientId,tableCount:tableCount,columnCount:columnCount}
}

var settings = SpringUtil.getBean('data-element.push-registry-com.linewell.dataelement.dataservice.push.DataPushRegistryProperties')
if ((pushNode==null && settings.getRelayBaseUrl() == '') || settings.getStandaloneSyncKey() == '') { exit 503, '海通推送配置同步地址或服务间密钥尚未配置' }
var snapshot = null
try {
    // 复用既有服务：批量预取表/字段，发布当前已登记表的权威契约快照。
    var schemas = SpringUtil.getBean('dataPushSchemaService')
    snapshot = schemas.standaloneSnapshot(tenantId,datasourceId,clientId)
} catch (e) {
    audit.warn('推送契约准备未完成，datasourceId={}，errorClass={}',datasourceId,e.getClass().getName())
    exit 409, '推送契约未能生成，请核对已登记表字段后重试'
}
snapshot.sourceSystemId = (source.appId || source.app_id || '') + ''
snapshot.sourceSystemName = (source.sourceSystemName || source.source_system_name || '') + ''
var envelope = {deliveryType:'REGISTRY_SYNC',datasource:snapshot}
if (mapper.writeValueAsBytes(envelope).length > 1048576) { exit 413, '推送配置超过1MiB，请精简表字段说明后重试' }
try {
    if (pushNode==null) {
        var relay = SpringUtil.getBean('haitongStandaloneRegistryClient')
        relay.synchronize(datasourceId,snapshot)
    } else {
        // The destination comes only from the current tenant's saved node. A
        // browser cannot provide an address or a service credential here.
        var response = null
        try {
            var target = (pushNode.baseUrl || pushNode.base_url) + '/api/v1/data-push'
            response = HttpRequest.post(target).timeout(10000).setFollowRedirects(false)
                .header('Content-Type','application/json').header('X-Standalone-Registry-Key',settings.getStandaloneSyncKey())
                .header('Idempotency-Key','node-registry-'+nodeId+'-'+datasourceId+'-'+IdUtil.fastSimpleUUID())
                .body(mapper.writeValueAsString(envelope)).execute()
            if (response.getStatus()<200 || response.getStatus()>=300) { throw new IllegalStateException('推送节点未确认保存') }
            var received = mapper.readValue(response.body(),Map)
            if ((received.datasourceId+'')!=datasourceId) { throw new IllegalStateException('推送节点返回的数据源不匹配') }
        } finally { if (response!=null) { response.close() } }
        db.table('push_node_t').where().eq('tenant_id',tenantId).eq('tid',nodeId).eq('is_del',0)
            .update({last_sync_time:new Date(),last_sync_status:'SUCCESS',last_sync_source_id:datasourceId})
    }
} catch (e) {
    if (pushNode!=null) { db.table('push_node_t').where().eq('tenant_id',tenantId).eq('tid',nodeId).eq('is_del',0).update({last_sync_time:new Date(),last_sync_status:'FAILED',last_sync_source_id:datasourceId}) }
    // 不把服务间地址、连接配置、响应正文或凭据写入响应及日志。
    audit.warn('推送登记配置同步未完成，datasourceId={}，errorClass={}',datasourceId,e.getClass().getName())
    exit 503, '推送配置未能确认保存到海通，请稍后使用同一数据源重试'
}
return {synced:true,nodeId:nodeId,datasourceId:datasourceId,clientId:clientId,tableCount:tableCount,columnCount:columnCount,target:'haitong'}
