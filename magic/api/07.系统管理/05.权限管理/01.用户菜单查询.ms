{
  "properties": {
    "gaLocalRoleAdapter": "v1"
  },
  "id": "c6ccbc4af86e4b599db7bf9cac8d774c",
  "script": null,
  "groupId": "882093f918054984b1504ea77e58d3af",
  "name": "01.用户菜单查询",
  "createTime": 1782660716480,
  "updateTime": 1784437771407,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/tree",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "获取当前用户菜单接口（基于角色权限，返回Vue路由树结构）；使用请求参数 appId；涉及 rm_user_role_rela_t、rm_role_t、rm_role_menu_rela_t、rm_menu_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tenantAccounts
import tenantRuntime
import "@post:/sym/user/me" as me
import cn.hutool.core.util.StrUtil

// 读取当前租户用户在目标应用的菜单树。
var tenant = tenantRuntime.id()
if (!tenant) { exit 401, '当前租户无效' }
var userId = me().id;
// 解析请求体并为可选参数设置默认值。
var appId = body?.appId || tenantAccounts.session().get("appId");

try {
    // 1. 查询用户所属角色
    var userRoles = db.table("rm_user_role_rela_t")
        .where().eq("tenant_id", tenant).eq("user_id", userId)
        .select();

    if (!userRoles || userRoles.size() == 0) {
        // 返回当前业务分支的标准结果。
        return [];
    }

    var roleIds = [];
    for (row in userRoles) {
        roleIds.add(row.roleId);
    }

    if(tenantAccounts.isLocal(tenant)) {
      var activeRoles=db.table('rm_role_t').where().eq('tenant_id', tenant).in('id',roleIds.distinct()).eq('app_id',appId).eq('deleted',0).eq('status',0).select();
      roleIds=activeRoles.map(r=>r.id);if(roleIds.size()==0) {return [];}
    }
    // 2. 查询角色关联的菜单资源ID
    var roleResources = db.table("rm_role_menu_rela_t")
        .where().eq("tenant_id", tenant).in("role_id", roleIds.distinct())
        .select();

    if (!roleResources || roleResources.size() == 0) {
        // 返回当前业务分支的标准结果。
        return [];
    }

    var resourceIds = [];
    for (row in roleResources) {
        resourceIds.add(row.appResourceId);
    }

    // 3. 查询菜单资源详情（只取未删除的菜单和分组，排除按钮，status=1的标记hidden）
    var query = db.table("rm_menu_t")
        .where().eq("tenant_id", tenant).in("id", resourceIds.distinct())
        .eq("deleted", 0)
        .ne("resource_type", 1);

    if (appId) {
        query = query.eq("app_id", appId);
    }

    var resources = query.orderBy("sort_num", "asc").select();

    // 4. 构建Vue路由树
    var itemMap = {};
    var childrenByParent = {};
    for (res in resources) {
        itemMap[res.id] = res;
        var parent = res.parentId || '';
        if (childrenByParent[parent] == null) { childrenByParent[parent] = []; }
        childrenByParent[parent].add(res);
    }

    // 构建每个节点的完整路径（向上递归拼接url）
    var fullPathMap = {};
    var activePath = {};
    var getFullPath = (resId) => {
        if (fullPathMap[resId]) { return fullPathMap[resId]; }
        var res = itemMap[resId];
        if (!res) { return ""; }
        var url = res.url || "";
        if (res.parentId == 'ROOT' || !itemMap[res.parentId]) {
            fullPathMap[resId] = url;
            // 返回当前业务分支的标准结果。
            return url;
        }
        if (activePath[resId]) { exit 500, '菜单父级存在循环'; }
        activePath[resId] = true;
        var parentPath = getFullPath(res.parentId);
        activePath.remove(resId);
        fullPathMap[resId] = parentPath + url;
        // 返回当前业务分支的标准结果。
        return fullPathMap[resId];
    };
    for (res in resources) {
        getFullPath(res.id);
    }

    // 将单条资源转为Vue路由节点
    var buildNode = (res) => {
        var node = {};
        if (res.resourceType == 2) {
            var groupKey = res.sno ? res.sno : res.name;
            node.path = "__group_" + groupKey + "__";
            node.meta = { type: "group", title: res.name };
            // 返回当前业务分支的标准结果。
            return node;
        }

        node.path = res.url ? res.url : "";

        // name: 完整路径去掉前导/，/替换为-
        var fullPath = fullPathMap[res.id] || "";
        if (res.url && res.url.startsWith("http")) {
            // 外部链接保持原样
            node.name = res.code ? res.code : res.sno;
        } else {
            var nameStr = fullPath.startsWith("/") ? fullPath.substring(1) : fullPath;
            node.name = nameStr.replace(/\//g, "-");
        }

        // sno决定component/componentName：inner_开头用componentName，否则用component，空则不设置
        if (res.sno) {
            if (res.sno.startsWith("inner_")) {
                node.component = res.sno.substring(6);
            } else {
                node.componentName = res.sno;
            }
        }

        node.meta = { title: res.name };
        if (res.status == 1) {
            node.meta.hidden = true;
        }

        // staticIcon → meta.icon
        if (res.staticIcon) {
            node.meta.icon = res.staticIcon;
        }

        // 外部链接
        if (res.resourceType == 3 || StrUtil.startWith(res.url,'http')) {
            if (res.openType == 0){
                node.meta.target = "_self"
            }
            // open_type: 1=iframe打开
            if (res.openType == 1) {
                node.meta.openMode = "iframe";
            }
            // open_type: 2=新页面
            if (res.openType == 2){
                node.meta.target = "_blank"
            }
        }

        // 返回当前业务分支的标准结果。
        return node;
    };

    // 递归构建子树
    var buildChildren = (parentId) => {
        var children = [];
        for (res in (childrenByParent[parentId] || [])) {
            if (res.parentId == parentId) {
                var childNode = buildNode(res);

                // 递归查找子节点的children
                var subChildren = buildChildren(res.id);
                if (subChildren && subChildren.size() > 0) {
                    childNode.children = subChildren;
                }

                // 有子节点且非分组菜单、非外部链接，设component=Layout
                //     && res.resourceType != 2
                //     && (!res.url || !res.url.startsWith("http"))) {
                //     } else {
                //     }
                // }

                children.add(childNode);
            }
        }
        // 返回当前业务分支的标准结果。
        return children;
    };

    // 顶层节点：parentId=ROOT 或 parentId不在资源列表中
    var tree = [];
    for (res in resources) {
        var isTopLevel = res.parentId == 'ROOT' || !itemMap[res.parentId];
        if (isTopLevel) {
            var rootNode = buildNode(res);

            var rootChildren = buildChildren(res.id);
            if (rootChildren && rootChildren.size() > 0) {
                rootNode.children = rootChildren;
            }

            // 有子节点的顶层菜单，设component=Layout
            //     && res.resourceType != 2
            //     && res.sno
            //     && (!res.url || !res.url.startsWith("http"))) {
            //     } else {
            //     }
            // }

            tree.add(rootNode);
        }
    }

    // 返回当前业务分支的标准结果。
    return tree;
} catch(e) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '获取用户菜单失败: ' + e.message;
}
