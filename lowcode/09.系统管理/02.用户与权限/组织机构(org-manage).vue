<template>
  <div class="card-container flex flex-col px-5 pt-5 pb-2">
    <!-- 操作区 -->
    <div class="mb-5 flex-x-between">
      <!-- 左侧操作按钮 -->
      <div class="flex">
        <el-button type="primary" icon="Plus" @click="handleAction('新增')">新增</el-button>
      </div>
      <!-- 右侧搜索区域 -->
      <div class="flex gap-2">
        <el-input
          v-model="searchKeyword"
          placeholder="请输入机构名称"
          clearable
          @clear="handleSearch"
          @keyup.enter="handleSearch"
        >
          <template #suffix>
            <el-icon class="org-search-icon" role="button" aria-label="搜索机构" title="点击查询" @click="handleSearch"><Search /></el-icon>
          </template>
        </el-input>
      </div>
    </div>

    <!-- 数据展示区 -->
    <DataTable
      ref="dataTableRef"
      :columns="columns"
      :data="fetchData"
      :row-sort="false"
      :show-page="false"
      :row-key="'id'"
      :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
    >
      <!-- 机构名称列：禁用状态显示灰色删除线 -->
      <template #column-name="{ row }">
        <span :class="{ 'is-disabled': row.status === 1 }">{{ row.name }}</span>
      </template>
    </DataTable>

    <!-- 排序弹框 -->
    <el-dialog v-model="sortDialogVisible" title="调整排序" width="480px" destroy-on-close>
      <div class="sort-dialog-content">
        <div class="sort-tip mb-3 text-sm text-gray-500">拖拽调整顺序，排序号将自动更新</div>
        <VueDraggable v-model="sortItems" :animation="200" handle=".sort-handle" class="sort-list">
          <div v-for="(item, index) in sortItems" :key="item.id" class="sort-item">
            <Icon class="sort-handle cursor-grab" icon="el-icon-rank" />
            <span class="sort-index">{{ index + 1 }}</span>
            <span class="sort-name">{{ item.name }}</span>
            <span class="sort-current text-gray-400 text-xs ml-auto">
              当前排序: {{ item.sortNum ?? "-" }}
            </span>
          </div>
        </VueDraggable>
      </div>
      <template #footer>
        <el-button @click="sortDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="sortSaving" @click="handleSortSave">
          <template #icon><Icon icon="save" /></template>
          保存排序
        </el-button>
      </template>
    </el-dialog>

    <!-- 机构配置抽屉：页签直接作为抽屉标题，便于在信息与用户间切换 -->
    <el-drawer v-model="drawerVisible" :size="700">
      <template #header>
        <el-tabs v-model="drawerTab" class="drawer-tabs" @tab-change="handleDrawerTabChange">
          <el-tab-pane label="机构信息" name="info" />
          <el-tab-pane label="机构用户" name="users" :disabled="!formData.id" />
        </el-tabs>
      </template>
      <div class="px-4">
        <json-form
          v-if="drawerTab === 'info'"
          ref="formRef"
          :data="formData"
          :rules="formRules"
          :options="{ form: { labelWidth: '130px' } }"
        >
          <template #field-parentId="scope">
            <dict-select
              :model-value="'' + scope.model.value"
              type="cascader"
              :options="parentOrgOptions"
              :props="{
                checkStrictly: true,
                emitPath: false,
              }"
              clearable
              popper-class="my-app-org-select"
              @change="(e) => scope.model.callback(e.target.value)"
            />
          </template>
        </json-form>

        <div v-else class="org-users-panel">
          <el-table v-loading="orgUsersLoading" :data="orgUsers" class="w-full">
            <el-table-column type="index" label="序号" width="70" align="center" />
            <el-table-column prop="userName" label="用户账号" min-width="130" show-overflow-tooltip />
            <el-table-column prop="realName" label="姓名" min-width="110" show-overflow-tooltip />
            <el-table-column prop="phone" label="手机号" min-width="130" />
            <el-table-column prop="roleNames" label="角色" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">{{ row.roleNames || "-" }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'info' : 'success'" size="small">
                  {{ row.status === 1 ? "禁用" : "启用" }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
      <template #footer>
        <el-button icon="close" @click="handleAction('关闭抽屉')">关闭</el-button>
        <el-button v-if="drawerTab === 'info'" type="primary" :disabled="isLoading" @click="handleAction('保存')">
          <template #icon><Icon icon="save" /></template>
          保存
        </el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script lang="ts" setup>
import { computed, ref, nextTick } from "vue";
import { Search } from "@element-plus/icons-vue";
import { FormUtils } from "@/utils/form";
import { VueDraggable } from "vue-draggable-plus";

// 接口地址
const API = {
  LIST: "/sym/org/pageOrg",
  SAVE_OR_UPDATE: "/sym/org/saveOrUpdate",
  SORT: "/sym/org/sort",
  DETAIL: "/sym/org/detail",
  DELETE: "/sym/org/delete",
  USER_PAGE: "/sym/user/page",
};

// 响应式数据
const searchKeyword = ref("");
const drawerVisible = ref(false);
const formData = ref({});
const drawerTab = ref<"info" | "users">("info");
const orgUsers = ref<any[]>([]);
const orgUsersLoading = ref(false);
const dataTableRef = ref<any>();
const formRef = ref<any>();
const isLoading = ref<boolean>(false);
const orgTree = ref<any[]>([]);

// 记录树的展开状态
const expandedRowKeys = ref<Set<string>>(new Set());

// 排序弹框相关
const sortDialogVisible = ref(false);
const sortItems = ref<any[]>([]);
const sortSaving = ref(false);
const sortParentId = ref<string>("");

// 保存当前展开的行
const saveExpandState = () => {
  const elTable = dataTableRef.value?.$table;
  if (!elTable) return;

  // 获取当前展开的行
  const expandedRows = elTable.getExpandRows?.() || [];
  expandedRowKeys.value = new Set(expandedRows.map((row: any) => row.id));
  console.log("保存展开状态:", expandedRowKeys.value.size, "个节点");
};

// 恢复展开的行
const restoreExpandState = () => {
  const elTable = dataTableRef.value?.$table;
  if (!elTable || expandedRowKeys.value.size === 0) return;

  // 递归查找并展开节点
  const expandNodes = (data: any[]) => {
    data.forEach((row) => {
      if (expandedRowKeys.value.has(row.id) && row.children?.length) {
        elTable.toggleRowExpansion(row, true);
      }
      if (row.children?.length) {
        expandNodes(row.children);
      }
    });
  };

  // 延迟执行，确保数据已渲染
  nextTick(() => {
    expandNodes(dataTableRef.value?.tableData || []);
    console.log("恢复展开状态:", expandedRowKeys.value.size, "个节点");
  });
};

// 二次刷新：保存状态 → 刷新 → 恢复状态
const refreshWithState = async () => {
  saveExpandState();
  await dataTableRef.value?.refresh();
  restoreExpandState();
};

// 加载当前机构直属用户；接口已按当前租户过滤，orgId 只用于查看该机构归属。
const loadOrgUsers = async () => {
  if (!formData.value?.id) {
    orgUsers.value = [];
    return;
  }
  try {
    orgUsersLoading.value = true;
    const result = await $common.post(API.USER_PAGE, {
      pageNum: 1,
      pageSize: 200,
      orgId: formData.value.id,
    });
    orgUsers.value = result?.list || [];
  } catch (error) {
    console.error("获取机构用户失败:", error);
    $message.error("获取机构用户失败");
    orgUsers.value = [];
  } finally {
    orgUsersLoading.value = false;
  }
};

const handleDrawerTabChange = (tab: string | number) => {
  if (tab === "users") loadOrgUsers();
};

// 表格列配置
const columns = [
  {
    prop: "name",
    label: "机构名称",
  },
  {
    prop: "orgPath",
    label: "机构全路径",
  },
  {
    prop: "nature",
    label: "机构类型",
    options: "orgType",
  },
  {
    prop: "updateTime",
    label: "更新时间",
  },
  {
    prop: "operation",
    label: "操作",
    type: "buttons",
    buttons: [
      {
        label: "详情",
        type: "primary",
        link: true,
        click: (row: any) => handleAction("详情", row),
      },
      {
        label: "新增子机构",
        type: "primary",
        link: true,
        click: (row: any) => handleAction("新增", row),
      },
      {
        dropdown: true,
        label: "更多",
        type: "primary",
        link: true,
        icon: "more_horiz",
        dropdownItems: [
          {
            label: "排序",
            click: (row: any) => handleAction("排序", row),
          },
          {
            label: "禁用",
            if: (row: any) => !row.status,
            click: (row: any) => handleAction("禁用", row),
          },
          {
            label: "启用",
            if: (row: any) => row.status == 1,
            click: (row: any) => handleAction("启用", row),
          },
          {
            label: "删除",
            if: (row: any) => row.id !== "ROOT",
            color: "#f56c6c",
            click: (row: any) => handleAction("删除", row),
          },
        ],
      },
    ],
  },
];

// 表单规则
const formRules = ref([]);
formRules.value = FormUtils.fixJson([
  {
    type: "u-title",
    title: "基本信息",
  },
  {
    type: "form-text",
    field: "id",
    hidden: true,
  },
  {
    field: "name",
    title: "机构名称",
    type: "input",
    props: {
      placeholder: "请输入机构名称",
      maxlength: 200,
    },
    $required: true,
  },
  {
    field: "parentId",
    title: "上级机构",
    type: "cascader",
    props: {
      placeholder: "请选择上级机构",
      options: [],
      props: {
        checkStrictly: true,
        emitPath: false,
      },
      popperClass: "my-app-org-select",
      placement: "bottom",
    },
    $required: true,
  },
  // {
  //   field: "shortName",
  //   title: "机构简称",
  //   type: "input",
  //   props: {
  //     placeholder: "请输入机构简称",
  //     maxlength: 200,
  //   },
  // },
  {
    field: "nature",
    title: "机构类型",
    type: "dict-select",
    props: {
      placeholder: "请选择机构类型",
      options: "orgType",
    },
    $required: true,
  },
  {
    field: "serialNumber",
    title: "序列号",
    type: "input",
    props: {
      placeholder: "自动生成序列号",
      maxlength: 100,
      disabled: true,
    },
  },
  {
    field: "standard",
    title: "机构级别",
    type: "dict-select",
    props: {
      placeholder: "请输入机构级别",
      maxlength: 64,
      options: "orgLevel",
    },
  },
  {
    field: "creditCode",
    title: "统一社会信用代码",
    type: "input",
    props: {
      placeholder: "请输入统一社会信用代码",
      maxlength: 100,
    },
  },
  {
    field: "originIds",
    title: "外部ID",
    type: "input",
    props: {
      placeholder: "请输入外部ID",
      maxlength: 100,
    },
  },
  {
    field: "sortNum",
    title: "排序号",
    type: "number",
    props: {
      placeholder: "请输入排序号",
      min: 0,
    },
    style: "width: 200px",
  },
  // {
  //   field: "status",
  //   title: "状态",
  //   type: "radio",
  //   value: 1,
  //   options: [
  //     { value: 0, label: "启用" },
  //     { value: 1, label: "禁用" },
  //   ],
  // },
  /*{
    type: "u-title",
    title: "扩展信息",
  },
  {
    field: "deptType",
    title: "部门类型",
    type: "input",
    props: {
      placeholder: "请输入部门类型",
      maxlength: 64,
    },
  },
  {
    field: "deptCode",
    title: "部门编码",
    type: "input",
    props: {
      placeholder: "请输入部门编码",
      maxlength: 100,
    },
  },
  {
    field: "nationalCode",
    title: "国家部门编码",
    type: "input",
    props: {
      placeholder: "请输入国家部门编码",
      maxlength: 100,
    },
  },
  {
    field: "nationalName",
    title: "国家部门名称",
    type: "input",
    props: {
      placeholder: "请输入国家部门名称",
      maxlength: 100,
    },
  },
  {
    field: "regionCode",
    title: "区域编码",
    type: "input",
    props: {
      placeholder: "请输入区域编码",
      maxlength: 100,
    },
  },
  {
    field: "nodeType",
    title: "节点类型",
    type: "input",
    props: {
      placeholder: "请输入节点类型",
      maxlength: 16,
    },
  },
  {
    field: "label",
    title: "标签",
    type: "input",
    props: {
      placeholder: "请输入标签",
      maxlength: 1000,
    },
  },
  {
    field: "leaderOrgId",
    title: "上级领导机构",
    type: "input",
    props: {
      placeholder: "请输入上级领导机构ID",
      maxlength: 50,
    },
  },
  {
    field: "description",
    title: "详细描述",
    type: "textarea",
    props: {
      placeholder: "请输入详细描述",
      maxlength: 1024,
      rows: 3,
    },
  },*/
]);

// 扁平数据转树状结构
const buildTree = (flatList: any[], rootParentId = "ROOT") => {
  const nodeMap = new Map<string, any>();
  const tree: any[] = [];

  // 第一次遍历：建立节点映射
  flatList.forEach((item) => {
    nodeMap.set(item.id, { ...item, children: [], value: item.id, label: item.name });
  });

  // 第二次遍历：构建树
  flatList.forEach((item) => {
    const node = nodeMap.get(item.id);
    if (item.parentId === rootParentId || !nodeMap.has(item.parentId)) {
      // 根节点
      tree.push(node);
    } else {
      // 子节点，添加到父节点的 children
      const parent = nodeMap.get(item.parentId);
      if (parent) {
        parent.children.push(node);
      }
    }
  });

  // “全部机构”是列表接口返回的虚拟根节点，不应作为可维护机构展示。
  // 去掉该节点并提升其子机构，保留原有层级和展开行为。
  const virtualRoot = tree.find((item) => item.name === "全部机构");
  return virtualRoot?.children?.length ? virtualRoot.children : tree.filter((item) => item.name !== "全部机构");
};

// 编辑父机构时排除当前节点及其整棵子树，前端避免产生循环；服务端仍会再次校验。
const parentOrgOptions = computed(() => {
  const currentId = (formData.value as any)?.id;
  const filterTree = (nodes: any[]): any[] =>
    (nodes || [])
      .filter((node) => node.id !== currentId)
      .map((node) => ({ ...node, children: filterTree(node.children) }));
  return filterTree(orgTree.value);
});

// 机构详情可能由搜索结果打开，父级候选必须始终使用完整组织树。
const loadOrgTreeOptions = async () => {
  const result = await fetchOrgList();
  orgTree.value = buildTree(result?.list || []);
};

// 处理搜索
const handleSearch = () => {
  refreshWithState();
};

// 统一操作处理方法
const handleAction = (type: string, row?: any) => {
  switch (type) {
    case "刷新":
      refreshWithState();
      break;
    case "搜索":
      handleSearch();
      break;
    case "新增":
      formRef.value?.resetFields();
      formData.value = { parentId: row?.id || $user.orgRootId };
      drawerTab.value = "info";
      orgUsers.value = [];
      drawerVisible.value = true;
      loadOrgTreeOptions().catch((error) => console.error("加载机构树失败:", error));
      break;
    case "详情":
      fetchOrgDetail(row.id);
      break;
    case "排序":
      handleOpenSortDialog(row);
      break;
    case "禁用":
      $dialog
        .confirm(`确定要禁用机构【${row?.name}】吗?`, "提示", {
          confirmButtonText: "确定",
          cancelButtonText: "取消",
          type: "warning",
        })
        .then(async () => {
          await $common.post(API.SAVE_OR_UPDATE, { id: row.id, status: 1 });
          $message.success("禁用成功");
          refreshWithState();
        });
      break;
    case "启用":
      $dialog
        .confirm(`确定要启用机构【${row?.name}】吗?`, "提示", {
          confirmButtonText: "确定",
          cancelButtonText: "取消",
          type: "warning",
        })
        .then(async () => {
          await $common.post(API.SAVE_OR_UPDATE, { id: row.id, status: 0 });
          $message.success("启用成功");
          refreshWithState();
        });
      break;
    case "删除":
      $dialog
        .confirm("确认要删除该机构及其全部子机构吗？删除后不可恢复。", "提示", {
          confirmButtonText: "确定",
          cancelButtonText: "取消",
          type: "warning",
        })
        .then(() => {
          handleDelete(row.id);
        })
        .catch(() => {});
      break;
    case "关闭抽屉":
      drawerVisible.value = false;
      drawerTab.value = "info";
      orgUsers.value = [];
      break;
    case "保存":
      handleSave();
      break;
    default:
      console.warn("未处理的操作类型:", type);
  }
};

// 获取机构详情
const fetchOrgDetail = async (id: string) => {
  try {
    const [res] = await Promise.all([
      $common.post(API.DETAIL, { id }),
      loadOrgTreeOptions(),
    ]);
    formData.value = res || {};
    drawerTab.value = "info";
    orgUsers.value = [];
    drawerVisible.value = true;
  } catch (error) {
    console.error("获取机构详情失败:", error);
    $message.error("获取机构详情失败");
  }
};

// 处理保存
const handleSave = async () => {
  try {
    await formRef.value?.validate();
    const data = await formRef.value?.getSaveData();
    isLoading.value = true;

    await $common.post(API.SAVE_OR_UPDATE, data);
    $message.success("保存成功");
    drawerVisible.value = false;
    refreshWithState();
  } catch (error) {
    console.error("保存失败:", error);
    $message.error("保存失败，请检查表单数据");
  } finally {
    isLoading.value = false;
  }
};

// 处理删除
const handleDelete = async (id: string) => {
  try {
    await $common.post(API.DELETE, { ids: [id] });
    $message.success("删除成功");
    refreshWithState();
  } catch (error) {
    console.error("删除失败:", error);
    $message.error("删除失败");
  }
};

// 打开排序弹框：查找当前节点的所有同级节点
const handleOpenSortDialog = (row: any) => {
  const parentId = row.parentId || "ROOT";
  sortParentId.value = parentId;

  // 从 orgTree 中查找同级节点
  const findSiblings = (nodes: any[], targetParentId: string): any[] => {
    // 如果是根级，直接返回顶层节点
    if (targetParentId === "ROOT") {
      return nodes.map((n) => ({ id: n.id, name: n.name, sortNum: n.sortNum }));
    }
    // 在子节点中查找
    for (const node of nodes) {
      if (node.id === targetParentId) {
        return (node.children || []).map((n: any) => ({
          id: n.id,
          name: n.name,
          sortNum: n.sortNum,
        }));
      }
      if (node.children?.length) {
        const result = findSiblings(node.children, targetParentId);
        if (result.length) return result;
      }
    }
    return [];
  };

  sortItems.value = findSiblings(orgTree.value, parentId);
  if (sortItems.value.length === 0) {
    $message.warning("未找到同级节点");
    return;
  }
  sortDialogVisible.value = true;
};

// 保存排序
const handleSortSave = async () => {
  try {
    sortSaving.value = true;
    // 逐个调用 saveOrUpdate 更新排序号
    await $common.post(API.SORT, { ids: sortItems.value.map((item) => item.id) });

    $message.success("排序保存成功");
    sortDialogVisible.value = false;
    refreshWithState();
  } catch (error) {
    console.error("排序保存失败:", error);
    $message.error("排序保存失败");
  } finally {
    sortSaving.value = false;
  }
};

// 获取组织机构列表
const fetchOrgList = async (params?: { name?: string }) => {
  return $common.get(API.LIST, params || {});
};

// 获取数据函数（供 DataTable 调用）
const fetchData = async (params: { pageNo: number; pageSize: number }) => {
  // 构建请求参数
  const queryParams: any = {};
  if (searchKeyword.value) {
    queryParams.name = searchKeyword.value;
  }

  // 调用接口获取扁平数据
  const res = await fetchOrgList(queryParams);
  const flatList = res?.list || [];
  // 转换为树状结构
  const treeData = buildTree(flatList);
  if (!searchKeyword.value) {
    orgTree.value = treeData;
    if (treeData.length == 1) {
      nextTick(() => {
        nextTick(() => {
          expandedRowKeys.value.add(treeData[0].id);
          restoreExpandState();
        });
      });
    }
  }

  // 处理搜索过滤（接口未支持模糊搜索时，前端过滤）
  let filteredData = treeData;
  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase();
    const filterTree = (data: any[]) => {
      return data.filter((item) => {
        const matches = item.name?.toLowerCase().includes(keyword);
        if (item.children && item.children.length > 0) {
          item.children = filterTree(item.children);
          return matches || item.children.length > 0;
        }
        return matches;
      });
    };
    filteredData = filterTree(treeData);
  }

  return {
    list: filteredData,
    total: filteredData.length,
  };
};
</script>

<style scoped lang="scss">
.is-disabled {
  color: var(--el-text-color-disabled);
  text-decoration: line-through;
}

.org-search-icon {
  color: #1677ff;
  cursor: pointer;
  font-size: 17px;

  &:hover {
    color: #409eff;
  }
}

.drawer-tabs {
  width: 100%;
  height: 54px;

  :deep(.el-tabs__header) {
    height: 54px;
    margin: 0;
  }

  :deep(.el-tabs__nav-wrap) {
    height: 54px;
  }

  :deep(.el-tabs__item) {
    height: 54px;
    line-height: 54px;
  }

  :deep(.el-tabs__active-bar) {
    bottom: -1px;
    height: 2px;
  }

  :deep(.el-tabs__nav-wrap::after) {
    display: none;
  }

  :deep(.el-tabs__content) {
    display: none;
  }
}

:deep(.el-drawer__header) {
  height: 54px;
  min-height: 54px;
  margin-bottom: 0;
  padding: 0 20px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  box-sizing: border-box;
}

.org-users-panel {
  min-height: 260px;
}

.sort-list {
  max-height: 400px;
  overflow-y: auto;
}

.sort-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  margin-bottom: 8px;
  background: var(--el-bg-color);
  transition: box-shadow 0.2s;

  &:hover {
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  }

  .sort-handle {
    color: var(--el-text-color-placeholder);
    font-size: 18px;
  }

  .sort-index {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    border-radius: 50%;
    background: var(--el-color-primary-light-9);
    color: var(--el-color-primary);
    font-size: 12px;
    font-weight: 600;
  }

  .sort-name {
    flex: 1;
    font-size: 14px;
    color: var(--el-text-color-regular);
  }
}
</style>
