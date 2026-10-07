{
  "properties": {},
  "id": "2cd6a8dd790e4ac3aa16622893d90a47",
  "script": null,
  "groupId": "9668e1a8b17d458f8739c9558d60ca98",
  "name": "05.字典导入",
  "createTime": 1782660716008,
  "updateTime": 1784437771345,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/dict-import",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"dicts\": \"测试\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "说明: 将所有字典定义批量初始化插入 sym_dict_t 表（幂等：先清空再插入）；使用请求参数 dicts。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：05.字典导入
// 路由：POST /sym/dict-import
// 业务说明：说明: 将所有字典定义批量初始化插入 sym_dict_t 表（幂等：先清空再插入）；使用请求参数 dicts。
// 初始化字典数据
// 路径: /api/sym/initDict
// 方法: GET
// 说明: 将所有字典定义批量初始化插入 sym_dict_t 表（幂等：先清空再插入）

import "@post:/sym/getDict" as getDict;
import cn.hutool.core.util.IdUtil;

var now = new Date();


// 解析请求体并为可选参数设置默认值。
var dicts = body.dicts || {
    "sceneType": {
        tid: "ddddddd",
        "label": "应用场景类型",
        "options": [
            {
                "tagType": "primary",
                "label": "数据查询",
                "value": "0"
            },
            {
                "tagType": "warning",
                "label": "统计分析",
                "value": "1"
            },
            {
                "tagType": "success",
                "label": "业务流程支撑",
                "value": "2",
                "children": []
            }
        ]
    },
}  
// -------------------------------------------------------
// 构建插入记录
// -------------------------------------------------------
// 递归辅助：将树形节点及其 children 展开为平铺记录
// parentTid: 父节点 tid; items: 当前层节点数组; parentPath: 父节点 tree_path; levelNo: 当前层级
var result = [];
var buildTree = (parentTid, items, parentPath, levelNo) => {
  var sortIdx = 0;
  for (node in items) {
    var nodeTid = node.tid || IdUtil.getSnowflakeNextIdStr();
    var hasChildren = (node.children != null && node.children.size() > 0);
    result.push({
      tid: nodeTid,
      dict_name: node.label,
      dict_code: node.value.asString(),
      dict_desc: node.dictDesc,
      parent_id: parentTid,
      sort_no: sortIdx + 1,
      level_no: levelNo,
      tree_path: parentPath + '.' + nodeTid,
      created_by: 'system',
      created_time: now,
      updated_by: 'system',
      updated_time: now,
      is_del: 0,
      tag_type: (node.tagType != null && node.tagType != '') ? node.tagType : null,
      icon: (node.icon != null && node.icon != '') ? node.icon : null,
      biz_type: (node.bizType != null && node.bizType != '') ? node.bizType : null,
    });
    sortIdx = sortIdx + 1;
    // 如果有 children，递归继续展开
    if (hasChildren) {
      buildTree(nodeTid, node.children, parentPath + '.' + nodeTid, levelNo + 1);
    }
  }
};

for (dictCode in dicts.keySet()) {
  var dict = dicts[dictCode];
  var parentTid = dict.tid || IdUtil.getSnowflakeNextIdStr();
  var parentPath = '0.' + parentTid;
  result.push({
    tid: parentTid,
    dict_name: dict.label,
    dict_code: dictCode,
    dict_desc: dict.dictDesc,
    parent_id: '0',
    sort_no: 0,
    level_no: 2,
    tree_path: parentPath,
    created_by: 'system',
    created_time: now,
    updated_by: 'system',
    updated_time: now,
    is_del: 0,
    tag_type: dict.tagType,
    icon: dict.icon,
    biz_type: dict.bizType,
  });

  // 检测 options 是否为树形（第一项有 children 属性）
  var options = dict.options;
  if (options.size() > 0 && options[0].children != null) {
    // 树形结构：递归展开，起始 level_no=3
    buildTree(parentTid, options, parentPath, 3);
  } else {
    // 平铺结构：直接遍历插入 level_no=3
    var sortIdx = 0;
    for (item in options) {
      var childTid = item.tid || IdUtil.getSnowflakeNextIdStr();
      result.push({
        tid: childTid,
        dict_name: item.label,
        dict_code: item.value.asString(),
        dict_desc: item.dict_desc,
        parent_id: parentTid,
        sort_no: sortIdx + 1,
        level_no: 3,
        tree_path: parentPath + '.' + childTid,
        created_by: 'system',
        created_time: now,
        updated_by: 'system',
        updated_time: now,
        is_del: 0,
        tag_type: (item.tagType != null && item.tagType != '') ? item.tagType : null,
        icon: (item.icon != null && item.icon != '') ? item.icon : null,
        biz_type: (item.bizType != null && item.bizType != '') ? item.bizType : null,
      });
      sortIdx = sortIdx + 1;
    }
  }
}
// 返回当前业务分支的标准结果。
return result

if (result.size() == 0){
  // 返回当前业务分支的标准结果。
  return null;
}
// -------------------------------------------------------
// 执行批量插入
// -------------------------------------------------------
db.table('sym_dict_t')
  .primary('tid')
  .batchInsert(result);

// 返回当前业务分支的标准结果。
return {
  code: 200,
  message: '字典初始化成功',
  total: result.size()
};
