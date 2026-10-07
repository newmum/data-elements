{
  "properties": {},
  "id": "20df1f42ab43450f9ec60780f83df52b",
  "script": null,
  "groupId": "8f2a6564b6fd43c28ff7a5e35d4d7cd2",
  "name": "06.页签详情",
  "createTime": 1782955345307,
  "updateTime": 1784437771886,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/queryById",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按页签主键读取应用仓库页签配置，返回编辑页面所需的详情。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：06.页签详情
// 路由：POST /das/repository/tab/queryById
// 业务说明：按页签主键读取应用仓库页签配置，返回编辑页面所需的详情。
import log
import uiComponentAdmin

/**
 * 应用仓库页签详情
 * GET /api/das/repository/tab/detail/{tid}
 */

var tid = body?.tid || path?.tid
if (tid == null || tid == '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, "页签ID不能为空"
}

var sql = """
    select
        t.tid,
        t.pid,
        t.name,
        t.name_cn,
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
        end as rela_name_cn,
        case
            when t.tab_type = 3 then bs.name
            when t.tab_type = 2 then sf.form_name
            else null
        end as rela_name
    from app_workshop_repository_tab t
    left join app_workshop_big_screen bs on bs.tid = t.rela_id and bs.is_del = 0 and t.tab_type = 3
    left join sym_form sf on sf.tid = t.rela_id and sf.is_del = 0 and t.tab_type = 2
    where t.is_del = 0
    and t.tid = #{tid}
"""

// 从 MySQL 业务表读取当前有效数据。
var detail = db.selectOne(sql)
if (detail == null) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, "页签不存在"
}
uiComponentAdmin.enrichRepositoryTabs([detail])

log.info('查询应用仓库页签详情成功, tid: {}', tid)

// 返回当前业务分支的标准结果。
return detail
