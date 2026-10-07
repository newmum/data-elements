{
  "properties" : { },
  "id" : "57d488fe51404e37afe8528c348c87ee",
  "script" : null,
  "groupId" : "b3f641e93f574cc5a88009f84e695ab1",
  "name" : "01.菜单保存修改",
  "createTime" : 1782660725684,
  "updateTime" : 1791088093611,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/saveOrUpdate",
  "method" : "POST",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : "{\n    \"resourceType\": 0,\n    \"urlType\": 0,\n    \"openType\": 0\n}",
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : null,
  "description" : "菜单保存修改（POST /sym/menu/saveOrUpdate）。支持新增、编辑和菜单层级移动；移动时校验循环引用并同步整棵子树的 top_id；公安LOCAL账号使用本地库与令牌会话，其他租户保留原机制；涉及 rm_menu_t 等业务表。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
// 接口：01.菜单保存修改
// 路由：POST /sym/menu/saveOrUpdate
// 业务说明：菜单保存修改（POST /sym/menu/saveOrUpdate）。支持新增、编辑和菜单层级移动；移动时校验循环引用并同步整棵子树的 top_id；公安LOCAL账号使用本地库与令牌会话，其他租户保留原机制；涉及 rm_menu_t 等业务表。
import tenantAccounts
import cn.hutool.core.util.IdUtil
import "@post:/sym/user/me" as me
import cn.dev33.satoken.stp.StpUtil

// 菜单资源保存/更新接口。
// 菜单层级调整时同步当前节点及全部后代的 top_id，防止导航树与权限树失配。


var now = new Date();
var id           = body?.id || '';
var name         = body?.name || '';
var parentId     = body?.parentId || 'ROOT';
var integerValue = (value, defaultValue) => {
    if (value == null || (value + '').trim() == '') {
        return defaultValue;
    }
    return value.asInt();
};
var resourceType = integerValue(body?.resourceType, 0);
var sno          = body?.sno || '';
var url          = body?.url || '';
var urlType      = integerValue(body?.urlType, 0);
var staticIcon   = body?.staticIcon || '';
var dynamicImg   = body?.dynamicImg || '';
var openType     = integerValue(body?.openType, 0);
var code         = body?.code || '';
var appId        = body?.appId || tenantAccounts.session().get("appId");
var sortNum      = body?.sortNum == null || (body.sortNum + '').trim() == '' ? null : body.sortNum;
var status       = integerValue(body?.status, 0);
var dataScopeEnabled = integerValue(body?.dataScopeEnabled, 0) == 1 ? 1 : 0;

if (!name) {
    exit 400, '菜单名称不能为空';
}

try {
    var updateId = me().createId;
    var normalizedParentId = parentId ? parentId : 'ROOT';
    var data = {
        name: name,
        parentId: normalizedParentId,
        resourceType: resourceType.asInt(),
        sno: sno,
        url: url,
        urlType: urlType,
        staticIcon: staticIcon,
        dynamicImg: dynamicImg,
        openType: openType,
        code: code,
        dataScopeEnabled: resourceType == 0 ? dataScopeEnabled : 0,
        appId: appId
    };

    if (id) {
        var exist = db.table("rm_menu_t")
            .where().eq("id", id).eq("deleted", 0).selectOne();
        if (exist == null) {
            exit 404, '记录不存在: ' + id;
        }
        if (normalizedParentId == id) {
            exit 400, '菜单不能设置自身为父级';
        }

        var parentRecord = null;
        var newTopId = id;
        if (normalizedParentId != 'ROOT') {
            parentRecord = db.table("rm_menu_t")
                .where().eq("id", normalizedParentId).eq("deleted", 0).selectOne();
            if (parentRecord == null) {
                exit 400, '目标父级菜单不存在';
            }
            if (parentRecord.resourceType == 1) {
                exit 400, '按钮节点不能作为父级菜单';
            }

            // 沿目标父级向上检查，禁止把菜单移动到自己的后代节点下。
            var cursor = parentRecord;
            while (cursor != null && cursor.id != null) {
                if (cursor.id == id) {
                    exit 400, '不能将菜单移动到自己的下级菜单中';
                }
                if (!cursor.parentId || cursor.parentId == 'ROOT') {
                    cursor = null;
                } else {
                    cursor = db.table("rm_menu_t")
                        .where().eq("id", cursor.parentId).eq("deleted", 0).selectOne();
                }
            }
            newTopId = parentRecord.topId || parentRecord.id;
        }

        data.topId = newTopId;
        if (sortNum != null) {
            data.sortNum = sortNum.asInt();
        }
        data.updateId = updateId;
        data.updateTime = now;

        db.table("rm_menu_t").withBlank()
            .where().eq("id", id)
            .update(data);

        // 父级变化后，所有后代必须继承新的顶级菜单 ID。
        if (newTopId != exist.topId || normalizedParentId != exist.parentId) {
            var queue = [id];
            while (queue.size() > 0) {
                var currentId = queue.remove(0);
                var children = db.table("rm_menu_t")
                    .where().eq("parent_id", currentId).eq("deleted", 0).select();
                for (child in children) {
                    db.table("rm_menu_t")
                        .where().eq("id", child.id)
                        .update({ topId: newTopId, updateId: updateId, updateTime: now });
                    queue.add(child.id);
                }
            }
        }

        return {
            id: id,
            parentId: normalizedParentId,
            topId: newTopId,
            moved: normalizedParentId != exist.parentId
        };
    }

    var newId = IdUtil.getSnowflakeNextIdStr();
    if (!sortNum) {
        var maxSortRow = db.table("rm_menu_t")
            .where().eq("parent_id", normalizedParentId).eq("deleted", 0)
            .orderBy("sort_num", "desc").selectOne();
        sortNum = maxSortRow != null ? (maxSortRow.sortNum + 1) : 1;
    }

    var newTopId = newId;
    if (normalizedParentId != 'ROOT') {
        var parentRecord = db.table("rm_menu_t")
            .where().eq("id", normalizedParentId).eq("deleted", 0).selectOne();
        if (parentRecord == null) {
            exit 400, '目标父级菜单不存在';
        }
        if (parentRecord.resourceType == 1) {
            exit 400, '按钮节点不能作为父级菜单';
        }
        newTopId = parentRecord.topId || parentRecord.id;
    }

    data.id = newId;
    data.topId = newTopId;
    data.sortNum = sortNum;
    data.createId = updateId;
    data.createTime = now;
    data.updateId = updateId;
    data.updateTime = now;
    data.deleted = 0;
    data.status = status;
    db.table("rm_menu_t").insert(data);
    return { id: newId, parentId: normalizedParentId, topId: newTopId };
} catch(e) {
    exit 500, '操作失败: ' + e.message;
}
