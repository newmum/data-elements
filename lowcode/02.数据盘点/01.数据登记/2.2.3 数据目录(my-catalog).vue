<template>
  <div class="card-container datasource-explorer-page flex flex-col px-5 pt-5 pb-2 w-full h-full">
    <el-splitter class="datasource-explorer-splitter">
      <!-- 管理员具有左侧树，非管理员不显示 -->
      <el-splitter-panel v-if="isLoadTree" size="300px" min="10%">
        <div class="left-panel">
          <div class="panel-header">
            <h3>数据目录</h3>
          </div>

          <div class="tree-container">
            <UTree
              :loading="state.leftLoading"
              ref="treeRef"
              :data="treeData"
              node-key="value"
              :search="true"
              :expand="false"
              :default-expand-all="false"
              :auto-expand-first-node="false"
              :expand-on-click-node="false"
              search-placeholder="请输入组织机构名称"
              @node-click="(node) => handleAction('node-click', node)"
            >
              <template #node="{ node }">
                <u-tree-node
                  show-icon
                  style="--gap: 0; --node-count-color: #323643"
                  :keyword="treeRef?.searchValue"
                  :label="node.label"
                >
                  <template #count>
                    <span
                      v-if="getTreeNodeCount(node.data) > 0"
                    >
                      ({{ getTreeNodeCount(node.data) }})
                    </span>
                    <span v-else></span>
                  </template>
                  <template #icon>
                    <Icon
                      v-if="node?.data.type === 'app'"
                      icon="app"
                      color="#8b5cf6"
                      class="mr-1"
                    />
                    <Icon
                      v-else
                      :icon="node.expanded ? 'folder-open' : 'folder'"
                      color="#0b8bf9"
                      class="mr-1"
                      size="23"
                    />
                  </template>
                </u-tree-node>
              </template>
            </UTree>
          </div>
        </div>
      </el-splitter-panel>

      <el-splitter-panel>
        <div class="right-panel" :class="{ 'pl-4': isLoadTree }">
          <div class="list-toolbar">
            <div class="list-toolbar__primary">
              <el-button :disabled="!props.editable" type="primary" icon="plus" @click="handleAction('register')">
                登记数据目录
              </el-button>
            </div>
            <div class="list-filters">
              <dict-select
                v-model="state.status"
                class="filter-select catalog-status-filter"
                placeholder="状态筛选"
                options="status"
                :picks="['待注册', '审批中', '已注册']"
                @change="handleAction('query')"
              />
              <el-input
                v-model.trim="state.keyword"
                class="keyword-input catalog-keyword-input"
                placeholder="请输入数据目录名称或关联数据资源"
                clearable
                @clear="handleAction('query')"
                @keydown.enter="handleAction('query')"
              >
                <template #suffix>
                  <span
                    class="keyword-search-trigger"
                    role="button"
                    tabindex="0"
                    title="查询"
                    @mousedown.prevent
                    @click="handleAction('query')"
                    @keydown.enter.prevent="handleAction('query')"
                  >
                    <Icon icon="search" />
                  </span>
                </template>
              </el-input>
            </div>
          </div>
          <data-table
            ref="tableRef"
            show-index
            :columns="cols"
            :loading="state.rightLoading"
            :data="fetchData"
            @filter-change="handleAction('filter-change', $event)"
          >
            <template #column-catalogName="{ row }">
              <el-text
                type="primary"
                cursor-pointer
                @click="
                  handleAction('详情', { id: row.tid, type: 'catalog', title: row.catalogName })
                "
              >
                <Icon icon="catalog"></Icon>
                {{ row.catalogName }}
              </el-text>
            </template>
            <template #column-sourceTableName="{ row }">
              <el-text
                v-if="row.sourceTableId"
                type="primary"
                cursor-pointer
                @click="
                  handleAction('详情', {
                    id: row.sourceTableId,
                    type: 'table',
                    title: row.sourceTableName,
                  })
                "
              >
                <Icon icon="table"></Icon>
                {{ row.sourceTableName }}
              </el-text>
              <el-text v-else type="info">-</el-text>
            </template>
            <template #column-progress="{ row }">
              <div class="flex-y-center">
                <span style="width: 50px">登记表</span>
                <el-progress
                  v-if="row.total !== null"
                  class="flex-[auto_1_1]"
                  :percentage="(row.finishNum / row.total) * 100"
                  :format="() => `${row.finishNum} / ${row.total}`"
                />
              </div>
            </template>
          </data-table>
        </div>
      </el-splitter-panel>
    </el-splitter>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, reactive, onMounted, onActivated, computed, nextTick } from "vue";
import { useRouter } from "vue-router";
import { useRegisterModal, useDetailDialog } from "@/composables";
import { get } from "lodash-es";

const props = withDefaults(
  defineProps<{
    appId?: string;
    editable?: boolean;
    dataSourceType?: string;
  }>(),
  { editable: true }
);
const countMap = ref({});
const orgTreeSource = ref([]);
const treeStatistics = ref({ total: 0, orgCounts: {}, orgApps: {} });

// 登记弹框
const { openRegisterModal, open } = useRegisterModal();
const { openDetailDialog } = useDetailDialog();
const router = useRouter();
const treeRef = ref();
const tableRef = ref();
const treeData = ref([
  {
    name: "全部",
    value: "",
    serialNumber: $user.orgRootSerialNumber,
    children: [],
  },
]);
const state = reactive({
  status: undefined,
  keyword: undefined,
  leftSelectNode: {},
  deletingIds: [],
  filters: {}, // 筛选条件
  leftLoading: false,
  rightLoading: false,
});

const isLoadTree = computed(() => !props.appId && $user.isAdmin);

// 监听关闭登记弹框
watch(open, (newVal, oldVal) => {
  if (newVal === false && oldVal) {
    handleAction("query");
  }
});

onActivated(() => {
  tableRef.value?.refresh(false);
});

onMounted(() => {
  handleAction("init");
});

/** 与数据源页相同：父部门包含所有后代，不重复累加业务系统节点。 */
const getTreeNodeCount = (node: any): number => {
  if (!node) return 0;
  if (node.type === "app") return Number(node.count || 0);
  if (!node.value) return Number(treeStatistics.value.total || 0);
  const own = Number(get(countMap.value, `org.${node.value}`, 0)) || 0;
  return own + (node.children || [])
    .filter((child: any) => child.type !== "app")
    .reduce((sum: number, child: any) => sum + getTreeNodeCount(child), 0);
};
const isAllZero = (node: any): boolean => getTreeNodeCount(node) === 0;

const collectTreeNodeIds = (node: any): string[] => {
  if (!node || node.type === "app") return [];
  return [node.value, ...(node.children || [])
    .filter((child: any) => child.type !== "app")
    .flatMap((child: any) => collectTreeNodeIds(child))].filter(Boolean);
};

/** 递归过滤掉统计值为 0 且后代也全为 0 的节点 */
const filterZeroNodes = (nodes: any[]): any[] => {
  return nodes
    .filter((node) => !isAllZero(node))
    .map((node) => ({
      ...node,
      children: node.children?.length ? filterZeroNodes(node.children) : node.children,
    }));
};

/** 末级部门下显示业务系统，不再追加数据库层级。目录数量来自目录权威表。 */
const appendApplicationNodes = (nodes: any[]): any[] => (nodes || []).map((node: any) => {
  const children = appendApplicationNodes(node.children || []);
  const apps = children.length === 0
    ? (treeStatistics.value.orgApps[node.value] || []).map((app: any) => ({
        ...app, type: "app", name: app.label, ownerOrgId: node.value,
        serialNumber: app.value, children: [],
      }))
    : [];
  return { ...node, type: "org", children: [...children, ...apps] };
});

const applyStatistics = (result: any) => {
  treeStatistics.value = {
    total: Number(result?.total || 0),
    orgCounts: result?.orgCounts || {},
    orgApps: result?.orgApps || {},
  };
  countMap.value = { org: treeStatistics.value.orgCounts };
  treeData.value[0].children = appendApplicationNodes(filterZeroNodes(orgTreeSource.value));
  // 用新树中的节点更新当前筛选对象，避免旧子树遗漏新登记的部门。
  const findNode = (nodes: any[], selected: any): any => {
    for (const node of nodes) {
      if (node.value === selected.value && node.type === selected.type &&
          (node.type !== "app" || node.ownerOrgId === selected.ownerOrgId)) return node;
      const found = findNode(node.children || [], selected);
      if (found) return found;
    }
  };
  if (state.leftSelectNode.value) {
    state.leftSelectNode = findNode(treeData.value[0].children, state.leftSelectNode) || {};
  }
  expandDefaultOrganizationPath();
};

/** 首屏仅展开“全部 → 第一层机构 → 内设机构”；更下级节点保持收起。 */
const expandDefaultOrganizationPath = () => {
  nextTick(() => {
    nextTick(() => {
      const tree = treeRef.value?.getTree?.();
      if (!tree) return;
      let current = treeData.value[0];
      for (let depth = 0; current && depth < 3; depth += 1) {
        const treeNode = tree.getNode(current.value);
        if (treeNode) treeNode.expanded = true;
        current = (current.children || []).find((child: any) => child.type !== "app");
      }
      tree.setCurrentNode?.(state.leftSelectNode.value ? state.leftSelectNode : treeData.value[0]);
    });
  });
};

const handleAction = async (type: string, row?: any) => {
  switch (type) {
    case "init":
      if (!isLoadTree.value) {
        // 没有左侧树，刷新数据列表
        handleAction("refresh");
        break;
      }

      // 左侧加载树
      state.leftLoading = true;
      if (isLoadTree.value) {
        Promise.all([
          $common.post("/sym/org/getOrgTree", { parentId: $user.orgRootId }),
          $common.post("/dst/catalog/treeStatistics", { conditions: buildConditions(false) }),
        ])
          .then(([data, result]) => {
            orgTreeSource.value = data || [];
            applyStatistics(result);
          })
          .catch((error) => {
            console.error("加载数据目录组织机构树失败:", error);
            $message.error("组织机构树加载失败，请稍后重试");
          })
          .finally(() => {
            state.leftLoading = false;
          });
      }
      break;
    case "node-click":
      // 左侧树点击
      state.leftSelectNode = row;
      handleAction("refresh");
      break;
    case "filter-change":
      // 筛选条件变化
      state.filters = row || {};
      handleAction("query");
      break;
    case "query":
      if (isLoadTree.value) {
        state.leftLoading = true;
        $common.post("/dst/catalog/treeStatistics", { conditions: buildConditions(false) })
          .then(applyStatistics)
          .then(() => tableRef.value?.refresh(true))
          .catch((error) => {
            console.error("刷新数据目录统计失败:", error);
            $message.error("目录统计刷新失败，请稍后重试");
          })
          .finally(() => { state.leftLoading = false; });
      } else {
        tableRef.value?.refresh(true);
      }
      break;
    case "refresh":
      tableRef.value?.refresh(true);
      break;
    case "详情":
      if (props.dataSourceType === "dwm") {
        openDetailDialog({ ...row, dataSourceType: props.dataSourceType });
      } else {
        router.push({
          path: "detail",
          query: {
            id: row.id,
            type: row.type,
            title: row.title,
            dataSourceType: row.type == "catalog" ? "ods" : undefined,
          },
        });
      }
      break;
    case "register":
      if (!props.editable) return;
      openRegisterModal({
        registerClass: "catalog",
        // 若在app详情中的catalog列表，登记需附带appId
        registerData: props.appId ? { app: { tid: props.appId } } : {},
        updateFormRule: [
          {
            field: "dataSourceType",
            value: "ods",
          },
        ],
      });
      break;
    case "编辑":
      if (!props.editable || !row?.tid) return;
      openRegisterModal({
        registerClass: "catalog",
        registerData: { catalog: [row] },
      });
      break;
    case "删除":
      if (!props.editable || !row?.tid || state.deletingIds.includes(row.tid)) return;
      state.deletingIds.push(row.tid);
      try {
        await $common.handle({
          url: "/dst/catalog/delete",
          info: `确认删除数据目录【${row.catalogName}】及其已登记数据项？此操作不会删除来源数据库中的物理表。`,
          data: { tid: row.tid },
          done: () => handleAction("query"),
        });
      } finally {
        state.deletingIds = state.deletingIds.filter((tid) => tid !== row.tid);
      }
      break;
  }
};

// 树统计与列表共用筛选条件；树分面不限制当前选中的部门。
const buildConditions = (includeTree = true) => {
  const conditions = [
    // 资产类型
    {
      field: "assetType",
      value: "catalog",
      type: "match",
    },
    // 读取ods层目录，手动登记目录均为ods
    {
      field: "dataSourceType",
      value: props.dataSourceType || "ods",
      type: "match",
    },
  ];
  if (state.status) {
    // 审批中由 flowStatus=1 判断，其余按 assetStatus 筛选
    conditions.push(
      state.status === "1"
        ? { field: "flowStatus", value: "1", type: "match" }
        : { field: "assetStatus", value: state.status, type: "match" }
    );
  }
  if (state.keyword) {
    // 关键字查询
    conditions.push({
      field: "catalogName",
      value: state.keyword,
      type: "like",
    });
  }
  if (includeTree && state.leftSelectNode.value) {
    const { type = "org", value, ownerOrgId } = state.leftSelectNode;
    if (type === "app") {
      conditions.push({ field: "appId", value, type: "match" });
      if (ownerOrgId) conditions.push({ field: "orgId", value: ownerOrgId, type: "match" });
    } else {
      conditions.push({ field: "orgIds", value: collectTreeNodeIds(state.leftSelectNode), type: "in" });
    }
  }
  if (props.appId) {
    // 添加appId查询条件
    conditions.push({
      field: "appId",
      value: props.appId,
      type: "match",
    });
  }
  // 处理筛选条件
  if (state.filters) {
    Object.keys(state.filters).forEach((key) => {
      const value = state.filters[key];
      if (value !== undefined && value !== null && value !== "") {
        conditions.push({
          field: key,
          value,
          type: "match",
        });
      }
    });
  }

  // 非管理员添加组织id条件
  if (!$user.isAdmin) {
    conditions.push({
      field: "orgId",
      value: $user.orgId,
      type: "match",
    });
  }

  return conditions;
};

// 列表接口仅查询保存的目录，无补建和源表状态准入。
const fetchData = async ({ pageNo: pageNum, pageSize }) => {
  const { list = [], total } = await $common.post("/dst/catalog/page", {
    sortField: "updatedTime",
    sortDir: "desc",
    pageNum,
    pageSize,
    conditions: buildConditions(),
  });
  return {
    list: list.map((item: any) => ({
      ...item,
      displayStatus: Number(item.flowStatus) === 1 ? 1 : Number(item.assetStatus),
    })),
    total,
  };
};

// 表格配置
const cols = [
  { label: "数据目录名称", prop: "catalogName", minWidth: 250 },
  { label: "关联数据资源", prop: "sourceTableName", minWidth: 250 },
  {
    label: "数据组织",
    prop: "dataSourceType",
    options: "dataSourceType",
    props: { showPath: false },
    width: 120,
  },
  { label: "数据所在层级", prop: "dataLevel", options: "dataLevel", width: 120 },
  { label: "共享属性", prop: "shareType", options: "shareType", width: 120 },
  { label: "注册时间", prop: "regTime", type: "date" },
  { label: "状态", prop: "displayStatus", options: "status", width: 120 },
  {
    label: "操作",
    prop: "action",
    align: "right",
    headerAlign: "center",
    width: 120,
    buttons: [
      {
        if: () => props.editable,
        type: "primary",
        link: true,
        label: "编辑",
        disabled: (row) => state.deletingIds.includes(row.tid),
        click: (row) => handleAction("编辑", row),
      },
      {
        if: () => props.editable,
        type: "danger",
        link: true,
        // 确认或请求期间防止重复操作，取消后恢复按钮。
        disabled: (row) => state.deletingIds.includes(row.tid),
        label: "删除",
        click: (row) => handleAction("删除", row),
      },
    ],
  },
];
</script>

<style scoped lang="scss">
.left-panel {
  height: 100%;
  min-width: 0;
  display: flex;
  flex-direction: column;

  .panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    border-bottom: 1px solid rgba(5, 5, 5, 0.06);
    padding-bottom: 10px;
    margin-bottom: 10px;
    height: 43px;
    margin-right: 14px;

    h3 {
      margin: 0;
      font-size: 16px;
      font-weight: 700;
      color: #464c64;
    }
  }

  .tree-container {
    height: calc(100% - 53px);
    min-height: 0;
    :deep(.u-tree) {
      height: 100%;
    }

    :deep(.u-tree-search) {
      margin-right: 14px;
    }

    :deep(.el-tree) {
      padding-right: 14px;
    }

    :deep(.el-tree-node__content) {
      padding-bottom: 2px;
      overflow: hidden;
    }

    :deep(.el-tree-node__label),
    :deep(.u-tree-node__label) {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
}

.datasource-explorer-page {
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

:deep(.datasource-explorer-splitter) {
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

:deep(.datasource-explorer-splitter > .el-splitter-panel) {
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

.right-panel {
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.list-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  flex: 0 0 auto;
  margin-bottom: 16px;
}

.list-toolbar__primary {
  flex: 0 0 auto;
}

.list-filters {
  display: flex;
  justify-content: flex-end;
  flex: 0 1 auto;
  min-width: 0;
  align-items: center;
  gap: 10px;
  margin-left: auto;
}

.filter-select {
  flex: 0 0 120px;
  width: 120px;
}

.keyword-input {
  flex: 0 1 200px;
  width: 200px;
  max-width: 200px;
  min-width: 160px;
}

.keyword-search-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  color: #1677ff;
  cursor: pointer;
  border-radius: 6px;
  transition:
    color 0.18s ease,
    background-color 0.18s ease;
}

.keyword-search-trigger:hover,
.keyword-search-trigger:focus-visible {
  color: #0958d9;
  background: #eaf3ff;
  outline: none;
}

.right-panel {
  :deep(.pagination) {
    container-type: inline-size;
    display: flex;
    flex-wrap: nowrap;
    align-items: center;
    gap: 10px;
    min-width: 0;
    overflow: hidden;
  }

  :deep(.pagination > span:first-child),
  :deep(.pagination .el-pagination) {
    flex: 0 0 auto;
    white-space: nowrap;
  }
}

@container (max-width: 680px) {
  .right-panel :deep(.pagination > span:first-child),
  .right-panel :deep(.pagination .el-pagination__jump) {
    display: none;
  }
}

@container (max-width: 480px) {
  .right-panel :deep(.pagination .el-pagination__sizes) {
    display: none;
  }
}

@media (max-width: 760px) {
  .list-toolbar {
    align-items: stretch;
  }

  .list-filters {
    width: 100%;
    flex-wrap: wrap;
    margin-left: 0;
  }

  .filter-select {
    flex: 1 1 calc(50% - 5px);
    width: auto;
  }

  .keyword-input {
    flex: 1 1 100%;
    width: 100%;
    max-width: none;
  }
}
</style>
