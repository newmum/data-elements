{
  "properties": {},
  "id": "d2ca261a63de4aa38188a0ed93c93acb",
  "script": null,
  "groupId": "c1736799c6af43359a75e6764601931f",
  "name": "05.机构删除",
  "createTime": 1782660716431,
  "updateTime": 1787278225032,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/delete",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "在当前租户内批量归档机构及子机构；最多1000个入口ID，整批校验归属并拒绝仍有关联用户的子树，成功后记录审计。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：05.机构删除
// 路由：POST /sym/org/delete
// 业务说明：组织机构删除接口（支持批量，并递归删除全部子机构）；使用请求参数 ids、id；涉及 rm_org_t 等业务表。
// 组织机构删除接口（支持批量，并递归删除全部子机构）
import '@/common/audit-operation' as auditOperation;
import tenantRuntime;
import cn.dev33.satoken.stp.StpUtil;

var now = new Date();
var ids = body?.ids;
if (ids == null || ids.size() == 0) {
    var singleId = body?.id || '';
    if (singleId != '') {
        ids = [singleId];
    }
}
if (ids == null || ids.size() == 0) {
    exit 400, '机构ID不能为空';
}

var tenantId=tenantRuntime.id();var sql=db.camel();
if(ids.size()>1000){exit 400,'一次最多删除1000个机构';}
try {
 return sql.transaction(()=>{
    var orgRows=sql.table('rm_org_t').where().eq('tenant_id',tenantId).eq('deleted',0).page(5001,0).list;
    if(orgRows.size()>5000){exit 400,'机构范围过大，请分级维护';}
    var updateId = StpUtil.getLoginId() + '';
    var rootIds = [];
    for (id in ids) {
        if (id != null && id != '' && !rootIds.contains(id)) {
            rootIds.add(id);
        }
    }
    if (rootIds.size() == 0) {
        exit 400, '机构ID不能为空';
    }
    // ROOT 仅是“全部机构”的系统根节点，不是可删除的业务机构。
    if (rootIds.contains('ROOT')) {
        exit 400, '全部机构为系统根节点，不能删除';
    }

    var existList = orgRows.filter(org=>rootIds.contains(org.id));
    var existIds = [];
    for (item in existList) {
        existIds.add(item.id);
    }
    var notFound = [];
    for (id in rootIds) {
        if (!existIds.contains(id)) {
            notFound.add(id);
        }
    }
    if (notFound.size() > 0) {
        exit 404, '机构不存在或已删除: ' + notFound.join(',');
    }

    // 从待删机构向下遍历；visited 同时避免批量选择父子节点或异常循环造成重复处理。
    var deleteIds = [];
    var visited = {};
    var queue = [];
    for (id in rootIds) {
        queue.add(id);
    }
    while (queue.size() > 0) {
        var currentId = queue.remove(0);
        if (visited[currentId] == true) {
            continue;
        }
        visited[currentId] = true;
        deleteIds.add(currentId);
        var children = orgRows.filter(org=>org.parentId==currentId);
        for (child in children) {
            queue.add(child.id);
        }
    }

    // 删除整棵子树前统一检查有效用户，避免留下无法登录或无法维护的悬空所属机构。
    var assignedUsers=sql.selectOne("""
        select count(*) as total from rm_user_org_rela_t r
        join rm_user_t u on u.id=r.user_id and u.tenant_id=#{tenantId} and u.deleted=0
        where r.tenant_id=#{tenantId} and r.org_id in (#{deleteIds})
    """).total;
    if(assignedUsers>0){exit 409,'机构或下级机构仍有关联用户，请先调整用户所属机构';}
    sql.table('rm_org_t').where().eq('tenant_id',tenantId).in('id', deleteIds).update({
        deleted: 1,
        updateId: updateId,
        updateTime: now
    });
    auditOperation('ORG_ARCHIVED','ORGANIZATION',null,null,{orgIds:deleteIds,count:deleteIds.size()});
    return {
        ids: rootIds,
        count: deleteIds.size(),
        deleted: true,
        recursive: true
    };
 });
} catch (e) {
    exit 500, '删除失败: ' + e.message;
}
