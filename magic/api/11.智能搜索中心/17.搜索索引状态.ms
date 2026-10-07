{
  "properties": {},
  "id": "search_es_index_status_01",
  "script": null,
  "groupId": "31808ca3357c3f359b69a090101ffcdc",
  "name": "17.搜索索引状态",
  "createTime": 1789857320685,
  "updateTime": 1789857473705,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/admin/index-status",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": "",
  "description": "查看当前租户智能搜索 ES 投影别名状态，不返回连接凭据。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：17.搜索索引状态
// 路由：GET /search/admin/index-status
// 业务说明：查看当前租户智能搜索 ES 投影别名状态，不返回连接凭据。
import tenantRuntime
import searchEs
var tenantId=tenantRuntime.id()
if(tenantId==null||(''+tenantId).trim()==''){throw new IllegalStateException('登录会话缺少租户信息，请重新登录')}
var tenantKey=(''+tenantId).toLowerCase()
var catalogRead='jcc-search-catalog-'+tenantKey+'-read'
var catalogWrite='jcc-search-catalog-'+tenantKey+'-write'
var portalRead='jcc-search-portal-'+tenantKey+'-read'
return {tenantId:tenantId,catalogReadAlias:catalogRead,catalogReadIndices:searchEs.aliasIndices(catalogRead),catalogWriteAlias:catalogWrite,catalogWriteIndices:searchEs.aliasIndices(catalogWrite),portalReadAlias:portalRead,portalReadIndices:searchEs.aliasIndices(portalRead)}
