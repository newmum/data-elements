{
  "properties" : { },
  "id" : "1dc43be6fbe648ea979c59fa37da7fb8",
  "script" : null,
  "groupId" : "b3f641e93f574cc5a88009f84e695ab1",
  "name" : "05.菜单路由查询",
  "createTime" : 1782660715939,
  "updateTime" : 1791089851203,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/routes",
  "method" : "GET",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : "{\n}",
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": [\n        {\n            \"path\": \"/register\",\n            \"name\": \"register\",\n            \"component\": \"Layout\",\n            \"meta\": {\n                \"title\": \"数据盘点\"\n            },\n            \"children\": [\n                {\n                    \"path\": \"__group_数据来源盘点__\",\n                    \"meta\": {\n                        \"type\": \"group\",\n                        \"title\": \"数据来源盘点\"\n                    }\n                },\n                {\n                    \"path\": \"/register-yyxt\",\n                    \"name\": \"register-register-yyxt\",\n                    \"meta\": {\n                        \"title\": \"业务系统\",\n                        \"icon\": \"menu-app\"\n                    },\n                    \"children\": [\n                        {\n                            \"path\": \"/list\",\n                            \"name\": \"register-register-yyxt-list\",\n                            \"componentName\": \"my-app\",\n                            \"meta\": {\n                                \"title\": \"列表\",\n                                \"hidden\": true\n                            }\n                        },\n                        {\n                            \"path\": \"/detail\",\n                            \"name\": \"register-register-yyxt-detail\",\n                            \"componentName\": \"detail-page\",\n                            \"meta\": {\n                                \"title\": \"详情\",\n                                \"hidden\": true\n                            }\n                        }\n                    ]\n                },\n                {\n                    \"path\": \"/register-sjkb\",\n                    \"name\": \"register-register-sjkb\",\n                    \"meta\": {\n                        \"title\": \"数据探查\",\n                        \"icon\": \"menu-find\"\n                    },\n                    \"children\": [\n                        {\n                            \"path\": \"/list\",\n                            \"name\": \"register-register-sjkb-list\",\n                            \"componentName\": \"my-db\",\n                            \"meta\": {\n                                \"title\": \"列表\",\n                                \"hidden\": true\n                            }\n                        },\n                        {\n                            \"path\": \"/detail\",\n                            \"name\": \"register-register-sjkb-detail\",\n                            \"componentName\": \"detail-page\",\n                            \"meta\": {\n                                \"title\": \"详情\",\n                                \"hidden\": true\n                            }\n                        }\n                    ]\n                },\n                {\n                    \"path\": \"__group_数据目录登记__\",\n                    \"meta\": {\n                        \"type\": \"group\",\n                        \"title\": \"数据目录登记\"\n                    }\n                },\n                {\n                    \"path\": \"/register-sjml\",\n                    \"name\": \"register-register-sjml\",\n                    \"meta\": {\n                        \"title\": \"数据目录\",\n                        \"icon\": \"menu-cata\"\n                    },\n                    \"children\": [\n                        {\n                            \"path\": \"/list\",\n                            \"name\": \"register-register-sjml-list\",\n                            \"componentName\": \"my-catalog\",\n                            \"meta\": {\n                                \"title\": \"列表\",\n                                \"hidden\": true\n                            }\n                        },\n                        {\n                            \"path\": \"/detail\",\n                            \"name\": \"register-register-sjml-detail\",\n                            \"componentName\": \"detail-page\",\n                            \"meta\": {\n                                \"title\": \"详情\",\n                                \"hidden\": true\n                            }\n                        }\n                    ]\n                },\n                {\n                    \"path\": \"/register-sjdr\",\n                    \"name\": \"register-register-sjdr\",\n                    \"meta\": {\n                        \"title\": \"数据导入\",\n                        \"icon\": \"menu-cata\"\n                    },\n                    \"children\": [\n                        {\n                            \"path\": \"/list\",\n                            \"name\": \"register-register-sjdr-list\",\n                            \"componentName\": \"my-fileCatalog\",\n                            \"meta\": {\n                                \"title\": \"列表\",\n                                \"hidden\": true\n                            }\n                        },\n                        {\n                            \"path\": \"/detail\",\n                            \"name\": \"register-register-sjdr-detail\",\n                            \"componentName\": \"detail-page\",\n                            \"meta\": {\n                                \"title\": \"详情\",\n                                \"hidden\": true\n                            }\n                        }\n                    ]\n                }\n            ]\n        },\n        {\n            \"path\": \"/jr\",\n            \"name\": \"jr\",\n            \"component\": \"Layout\",\n            \"meta\": {\n                \"title\": \"数据接入\"\n            },\n            \"children\": [\n                {\n                    \"path\": \"__group_接入任务创建__\",\n                    \"meta\": {\n                        \"type\": \"group\",\n                        \"title\": \"接入任务创建\"\n                    }\n                },\n                {\n                    \"path\": \"/data-convergence\",\n                    \"name\": \"jr-data-convergence\",\n                    \"meta\": {\n                        \"title\": \"数据汇聚\"\n                    },\n                    \"children\": [\n                        {\n                            \"path\": \"/list\",\n                            \"name\": \"jr-data-convergence-list\",\n                            \"componentName\": \"data-convergence\",\n                            \"meta\": {\n                                \"title\": \"列表\",\n                                \"hidden\": true\n                            }\n                        },\n                        {\n                            \"path\": \"/detail\",\n                            \"name\": \"jr-data-convergence-detail\",\n                            \"componentName\": \"detail-page\",\n                            \"meta\": {\n                                \"title\": \"详情\",\n                                \"hidden\": true\n                            }\n                        }\n                    ]\n                },\n                {\n                    \"path\": \"__group_数据接入保障__\",\n                    \"meta\": {\n                        \"type\": \"group\",\n                        \"title\": \"数据接入保障\"\n                    }\n                },\n                {\n                    \"path\": \"/data-access\",\n                    \"name\": \"jr-data-access\",\n                    \"componentName\": \"data-access\",\n                    \"meta\": {\n                        \"title\": \"数据接入\",\n                        \"hidden\": true\n                    }\n                },\n                {\n                    \"path\": \"/access-monitoring\",\n                    \"name\": \"jr-access-monitoring\",\n                    \"componentName\": \"access-monitoring\",\n                    \"meta\": {\n                        \"title\": \"任务管理\",\n                        \"icon\": \"menu-monitor\"\n                    }\n                },\n                {\n                    \"path\": \"/data-dz\",\n                    \"name\": \"jr-data-dz\",\n                    \"meta\": {\n                        \"title\": \"数据对账\",\n                        \"icon\": \"menu-book\"\n                    },\n                    \"children\": [\n                        {\n                            \"path\": \"/data-dz-list\",\n                            \"name\": \"jr-data-dz-data-dz-list\",\n                            \"componentName\": \"data-dz\",\n                            \"meta\": {\n                                \"title\": \"数据对账列表\",\n                                \"hidden\": true\n                            }\n                        },\n                        {\n                            \"path\": \"/data-dz-dj\",\n                            \"name\": \"jr-data-dz-data-dz-dj\",\n                            \"componentName\": \"data-dz-dj\",\n                            \"meta\": {\n                                \"title\": \"新增对账策略\",\n                                \"hidden\": true\n                            }\n                        }\n                    ]\n                }\n            ]\n        }\n    ]\n}",
  "description" : "当前用户菜单入口（GET /sym/menu）；读取或计算后返回业务结果。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
// 接口：05.菜单路由查询
// 路由：GET /sym/menu/routes
// 业务说明：当前用户菜单入口（GET /sym/menu）；读取或计算后返回业务结果。
/*
 * 当前用户菜单入口（GET /sym/menu）。
 *
 * 数据来源：复用 /sym/auth/userMenu 返回的权限菜单树。
 * 门户整理：仅移除已下线的个人工作台；其余入口由租户权限菜单配置决定，
 * 保持顶部导航与权限管理页一致。
 */
import "@post:/sym/auth/userMenu" as userMenu;

// 个人中心保留资源申请，并把默认入口定位到资源申请。
var stripUnusedPersonalWorkbench = (nodes) => {
    if (nodes == null) {
        return [];
    }
    return nodes.filter(item => {
        var path = item?.path == null ? "" : item.path + "";
        var name = item?.name == null ? "" : item.name + "";
        var componentName = item?.componentName == null ? "" : item.componentName + "";
        return path != "/my-workplace" &&
            name != "workplace-my-workplace" &&
            componentName != "my-workplace";
    }).map(item => {
        if (item?.children != null) {
            item.children = stripUnusedPersonalWorkbench(item.children);
        }
        if ((item?.path + "") == "/workplace") {
            item.redirect = "/workplace/my-apply/list";
        }
        return item;
    });
};

return stripUnusedPersonalWorkbench(userMenu());
