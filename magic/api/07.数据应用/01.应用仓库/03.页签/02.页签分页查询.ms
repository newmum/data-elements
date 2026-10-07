{
  "properties": {},
  "id": "124c8c77129242f786e0903e5172a90b",
  "script": null,
  "groupId": "8f2a6564b6fd43c28ff7a5e35d4d7cd2",
  "name": "02.页签分页查询",
  "createTime": 1782955345276,
  "updateTime": 1784437771880,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"pageNum\": \"1\",\n    \"pageSize\": \"20\",\n    \"pid\": \"测试\",\n    \"tabType\": \"app\",\n    \"keyword\": \"测试\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"total\": 0,\n        \"list\": null\n    }\n}",
  "description": "按所属应用、页签类型和关键词查询应用仓库页签，返回分页资料与总数。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.页签分页查询
// 路由：POST /das/repository/tab/page
// 业务说明：按所属应用、页签类型和关键词查询应用仓库页签，返回分页资料与总数。
import log
import uiComponentAdmin

/**
 * 应用仓库页签分页查询
 * POST /api/das/repository/tab/page
 */

var pageNum = body?.pageNum::long(1)
var pageSize = body?.pageSize::long(20)
var offsetLimit = pageNum == 0 ? 0 : (pageNum - 1) * pageSize

var pid = body?.pid
var tabType = body?.tabType
var keyword = body?.keyword

var sql = """
    select
        t.tid,
        t.pid,
        --t.name,
        -- t.name_cn,
        t.tab_type,
        t.rela_id,
        t.publish_id,
        t.thumbnail,
        t.seq_no,
        t.created_by,
        t.created_time,
        t.updated_by,
        t.updated_time,
        case
            when t.tab_type = 3 then bs.name_cn
            when t.tab_type = 2 then sf.form_name
            else null
        end as name_cn,
        case
            when t.tab_type = 3 then bs.name
            when t.tab_type = 2 then sf.form_name
            else null
        end as name
    from app_workshop_repository_tab t
    left join app_workshop_big_screen bs on bs.tid = t.rela_id and bs.is_del = 0 and t.tab_type = 3
    left join sym_form sf on sf.tid = t.rela_id and sf.is_del = 0 and t.tab_type = 2
"""

var whereSql = " where t.is_del = 0 "
if (pid != null && pid != '') {
    whereSql = whereSql + " and t.pid = #{pid} "
}
if (tabType != null && tabType != '') {
    whereSql = whereSql + " and t.tab_type = #{tabType} "
}
if (keyword != null && keyword != '') {
    whereSql = whereSql + " and (t.name like concat('%', #{keyword}, '%') or t.name_cn like concat('%', #{keyword}, '%')) "
}

var orderSql = " order by t.seq_no asc, t.created_time desc "
// 从 MySQL 业务表读取当前有效数据。
var pageList = db.page(sql + whereSql + orderSql, pageSize, offsetLimit)
uiComponentAdmin.enrichRepositoryTabs(pageList.list)

log.info('分页查询应用仓库页签成功, pid: {}, tabType: {}, pageNum: {}, pageSize: {}, total: {}', pid, tabType, pageNum, pageSize, pageList?.total)

// 返回当前业务分支的标准结果。
return pageList
