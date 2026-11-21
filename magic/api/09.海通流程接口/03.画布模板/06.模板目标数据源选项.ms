{
  "properties": {},
  "id": "83bfdc9a5b1e4ae5a53335b6942213f3",
  "script": null,
  "groupId": "061bd630dc3758bdaf894b4508985387",
  "name": "06.模板目标数据源选项",
  "path": "/targets",
  "method": "POST",
  "parameters": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    }
  ],
  "description": "从当前行业已配置数据源中分页选择模板目标库，按ID批量回显。"
}
================================
import tenantRuntime
import tableRead
import dataScope
import cn.hutool.json.JSONUtil
import com.linewell.dataelement.integration.nifi.canvas.compile.RegisteredDatasourceType as datasourceTypes
import java.util.List

// POST /nifi/canvas/targets。复用资源中心的数据源主表，模板配置按当前行业及管理范围选择真实目标库。
var tenantId = tenantRuntime.id()
if (tenantId == null || tenantId.isBlank()) { exit 401, '请重新登录' }
if (dataScope.dataSource().scope != 'ALL') { exit 403, '当前角色无权配置模板目标库' }
var keyword = (body?.keyword || '').toString().trim()
if (keyword.length() > 96) { exit 400, '搜索关键字最多96个字符' }
var aliases = {MYSQL:['mysql'],POSTGRESQL:['postgresql'],ORACLE:['oracle','oracle12','oracle12+'],
    OCEANBASE_ORACLE:['oceanbaseoracle'],OCEANBASE_MYSQL:['oceanbasemysql'],HIVE:['hive','mrshive'],
    DM:['dm','dameng'],KINGBASE:['kingbase','kingbase8'],SQLSERVER:['sqlserver','mssql'],
    FTP:['ftp','sftp'],MONGODB:['mongodb']}
var kind = (body?.kind || '').toString()
if (kind != '' && aliases[kind] == null) { exit 400, '目标库类型无效' }
var query = tableRead.from('db_datasource_t','d',tenantId)
var column = (name,type) => tableRead.column('d',name,type)
var id = column('tid','TEXT')
query.where(tableRead.equal(column('is_del','NUMBER'),0))
query.where(tableRead.equal(column('show_connect','NUMBER'),1))
if (kind != '') { query.where(tableRead.in(column('db_type','TEXT'),aliases[kind])) }
if (keyword != '') { query.where(tableRead.contains(column('db_name','TEXT'),keyword)) }
var projection = {tid:id,label:column('db_name','TEXT'),dbType:column('db_type','TEXT'),
    poolCfg:column('pool_cfg','LOB')}
var pageNum = Math.max(1,(body?.pageNum || 1)::int)
var pageSize = Math.max(1,Math.min(100,(body?.pageSize || 20)::int))
var ids = body?.ids
var result = null
if (ids != null) {
    if (!(ids instanceof List) || ids.size() > 100) { exit 400, '一次最多回显100个数据源' }
    if (ids.isEmpty()) { return {list:[],total:0,pageNum:1,pageSize:pageSize} }
    query.where(tableRead.in(id,ids))
    var rows = query.rows(projection,id,'asc',ids.size())
    result = {rows:rows,total:rows.size()}
} else {
    result = query.page(projection,column('db_name','TEXT'),'asc','tid',pageNum,pageSize)
}
var list = []
for (row in result.rows) {
    var config = row.poolCfg == null ? {} : JSONUtil.parseObj(row.poolCfg)
    var canonical = datasourceTypes.configType(row.dbType,config)
    if (canonical == 'DAMENG') { canonical = 'DM' }
    list.add({tid:row.tid,value:row.tid,label:row.label,dbName:row.label,dbType:row.dbType,kind:canonical})
}
return {list:list,total:result.total,pageNum:pageNum,pageSize:pageSize}
