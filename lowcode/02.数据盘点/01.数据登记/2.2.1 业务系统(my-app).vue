<template>
  <div class="card-container datasource-explorer-page flex flex-col px-5 pt-5 pb-2 w-full h-full">
    <el-splitter class="datasource-explorer-splitter">
      <!-- 管理员具有左侧树，非管理员不显示 -->
      <el-splitter-panel v-if="isLoadTree" size="300px" min="10%">
        <div class="left-panel">
          <div class="panel-header">
            <h3>业务系统</h3>
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
              :search-placeholder="'请输入搜索关键词'"
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
                      v-if="node?.data.type"
                      :icon="node?.data.type === 'db' ? node?.data.dbType : node?.data.type"
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
              <el-button type="primary" icon="plus" @click="handleAction('登记业务系统')">
                登记业务系统
              </el-button>
            </div>
            <div class="list-filters">
              <!--              <dict-select-->
              <!--                v-if="() => $user.isAdmin"-->
              <!--                v-model="state.org"-->
              <!--                type="cascader"-->
              <!--                :props="{-->
              <!--                  checkStrictly: false,-->
              <!--                  emitPath: false,-->
              <!--                }"-->
              <!--                clearable-->
              <!--                popper-class="my-app-org-select"-->
              <!--                style="width: 200px"-->
              <!--                placeholder="来源部门筛选"-->
              <!--                options="org"-->
              <!--                @change="handleAction('refresh')"-->
              <!--              />-->
              <el-input
                v-model.trim="state.keyword"
                class="keyword-input"
                placeholder="请输入关键字"
                clearable
                @clear="handleAction('refresh')"
                @keydown.enter="handleAction('refresh')"
              >
                <template #suffix>
                  <span
                    class="keyword-search-trigger"
                    role="button"
                    tabindex="0"
                    title="查询"
                    @mousedown.prevent
                    @click.stop="handleAction('refresh')"
                    @keydown.enter.prevent="handleAction('refresh')"
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
            :data="fetchData"
            :immediate="false"
          >
            <template #column-dbName="{ row }">
              <el-text
                type="primary"
                cursor-pointer
                @click="handleAction('详情', { ...row, type: 'db', title: row.dbName })"
              >
                <Icon :icon="row.dbType"></Icon>
                {{ row.dbName }}
              </el-text>
            </template>
            <template #column-appName="{ row, text }">
              <el-text
                type="primary"
                cursor-pointer
                @click="handleAction('详情', { ...row, type: 'app', title: text })"
              >
                <Icon icon="app"></Icon>
                {{ text }}
              </el-text>
            </template>
            <template #column-dbNum="{ row }">
              <el-popover
                v-if="row.dbNum > 0"
                :hide-after="0"
                :width="'auto'"
                trigger="click"
                title="数据库列表"
                :popper-style="{ maxHeight: '280px', overflowY: 'hidden', paddingRight: '0' }"
                @before-enter="getDbList(row.tid)"
              >
                <template #reference>
                  <el-link type="primary">{{ row.dbNum }}个</el-link>
                </template>
                <div v-if="dbList.loading" v-loading="dbList.loading" style="height: 24px"></div>
                <div v-else-if="dbList.data.length > 0" class="max-h-60 overflow-y-auto pr-3">
                  <div
                    v-for="(db, index) in dbList.data"
                    :key="index"
                    class="flex items-center py-1"
                  >
                    <Icon :icon="db.dbType"></Icon>
                    <span class="ml-2">{{ db.dbName }}</span>
                  </div>
                </div>
              </el-popover>
              <el-link v-else type="info" disabled>{{ row.dbNum }}个</el-link>
            </template>
            <template #column-catalogNum="{ row }">
              <el-popover
                v-if="row.catalogNum > 0"
                :hide-after="0"
                :width="'auto'"
                trigger="click"
                title="数据目录列表"
                :popper-style="{ maxHeight: '280px', overflowY: 'hidden', paddingRight: '0' }"
                @before-enter="getCatalogList(row.tid)"
              >
                <template #reference>
                  <el-link type="primary">{{ row.catalogNum }}个</el-link>
                </template>
                <div
                  v-if="catalogList.loading"
                  v-loading="catalogList.loading"
                  style="height: 24px"
                ></div>
                <div v-else-if="catalogList.data.length > 0" class="max-h-60 overflow-y-auto pr-3">
                  <div
                    v-for="(catalog, index) in catalogList.data"
                    :key="index"
                    class="flex items-center py-1"
                  >
                    <Icon icon="catalog"></Icon>
                    <span class="ml-2">{{ catalog.catalogName }}</span>
                  </div>
                </div>
              </el-popover>
              <el-link v-else type="info" disabled>{{ row.catalogNum }}个</el-link>
            </template>
            <template #column-tableNum="{ row }">
              <el-popover
                v-if="row.tableNum > 0"
                :hide-after="0"
                :width="'auto'"
                trigger="click"
                title="数据表列表"
                :popper-style="{
                  maxHeight: '280px',
                  overflowY: 'hidden',
                  paddingRight: '0',
                }"
                @before-enter="getTableList(row.tid)"
              >
                <template #reference>
                  <el-link type="primary">{{ Number(row.tableNum || 0).toLocaleString("zh-CN") }}</el-link>
                </template>
                <div
                  v-if="tableList.loading"
                  v-loading="tableList.loading"
                  style="height: 24px"
                ></div>
                <div v-else-if="tableList.data.length > 0" class="max-h-60 overflow-y-auto pr-3">
                  <div
                    v-for="(table, index) in tableList.data"
                    :key="index"
                    class="flex items-center py-1"
                  >
                    <Icon icon="catalog"></Icon>
                    <span class="ml-2">{{ table.tableName }}</span>
                  </div>
                </div>
              </el-popover>
              <el-link v-else type="info" disabled>{{ Number(row.tableNum || 0).toLocaleString("zh-CN") }}</el-link>
            </template>

            <template #column-progress="{ row }">
              <div class="flex-y-center">
                <span style="width: 50px">登记表</span>
                <el-progress
                  v-if="row.total !== null"
                  class="flex-1"
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
import { ref, reactive, watch, onMounted, onActivated, computed, nextTick } from "vue";
import { useRouter } from "vue-router";
import { get } from "lodash-es";
import { useRegisterModal } from "@/composables";

// app登记弹框
const { openRegisterModal, open } = useRegisterModal();
const router = useRouter();
const treeRef = ref();
const tableRef = ref();
const skipFirstActivation = ref(true);
const suppressInitialTreeClick = ref(false);
const treeData = ref([
  {
    name: "全部",
    value: "",
    serialNumber: $user.orgRootSerialNumber,
    children: [],
  },
]);
// 统计数据映射
const countMap = ref<Record<string, any>>({});
const state = reactive({
  status: undefined,
  org: undefined,
  keyword: "",
  deletedIds: [],
  leftSelectNode: {} as any,
  leftLoading: false,
});

// 数据库列表
const dbList = reactive({
  loading: false,
  data: [] as any[],
});

// 目录列表
const catalogList = reactive({
  loading: false,
  data: [] as any[],
});

// 数据表列表
const tableList = reactive({
  loading: false,
  data: [] as any[],
});

// 是否加载左侧树（管理员显示）
const isLoadTree = computed(() => $user.isAdmin);

// 表格配置
const cols = [
  { label: "系统名称", prop: "appName" },
  { label: "系统描述", prop: "assetDesc", minWidth: 200, maxWidth: 300 },
  {
    label: "在用状态",
    prop: "assetStatusOther",
    align: "center",
    width: 100,
  },
  {
    label: "数据表（个）",
    prop: "tableNum",
    type: "num",
    precision: 0,
    width: 100,
    sortable: true,
  },
  {
    label: "数据来源部门",
    // 展示字段由分页接口按 orgId 回填；orgId 本身仍是保存和组织树筛选的唯一关联键。
    prop: "orgName",
    minWidth: 180,
    tooltip: true,
  },
  { label: "状态", prop: "displayStatus", options: "status", align: "center", width: 90 },
  {
    type: "buttons",
    label: "操作",
    prop: "action",
    align: "right",
    buttons: [
      {
        if(row: any) {
          return true;
        },
        type: "primary",
        label: "编辑",
        click(row) {
          handleAction("编辑", row);
        },
      },
      {
        if(row: any) {
          return true;
        },
        // 接口是异步删除，防止重复点击
        disabled: (row) => state.deletedIds?.includes(row.tid || row.id),
        type: "danger",
        label: "删除",
        click(row) {
          handleAction("删除", row);
        },
      },
    ],
  },
];

// 监听关闭登记弹框
watch(open, (newVal, oldVal) => {
  if (newVal === false && oldVal) {
    handleAction("refresh");
  }
});

onActivated(() => {
  if (skipFirstActivation.value) {
    skipFirstActivation.value = false;
    return;
  }
  handleAction("refresh");
});

onMounted(() => {
  handleAction("init", { initial: true });
});

/** 递归汇总节点自身及所有后代的应用系统数，父节点显示完整合计 */
const getTreeNodeCount = (node: any): number => {
  if (!node) return 0;
  // “全部”必须直接使用与右侧列表相同 ES 查询快照的总命中数。
  // 部分历史应用仍保存旧部门编号，不在当前组织树中，不能再靠子节点相加推导总数。
  if (!node.value && countMap.value._total != null) {
    return Number(countMap.value._total) || 0;
  }
  const key = `${node.type || "org"}.${node.value}`;
  const ownCount = Number(get(countMap.value, key, 0)) || 0;
  return ownCount + (node.children || []).reduce(
    (total: number, child: any) => total + getTreeNodeCount(child),
    0
  );
};

/** 收集当前节点及所有后代组织 ID，保证父节点统计值与列表范围一致 */
const collectTreeNodeIds = (node: any): string[] => {
  if (!node) return [];
  return [node.value, ...(node.children || []).flatMap((child: any) => collectTreeNodeIds(child))]
    .filter(Boolean);
};

/** 判断节点及其所有后代的统计值是否全为 0 */
const isAllZero = (node: any): boolean => getTreeNodeCount(node) === 0;

/** 递归过滤掉统计值为 0 且后代也全为 0 的节点 */
const filterZeroNodes = (nodes: any[]): any[] => {
  return nodes
    .filter((node) => !isAllZero(node))
    .map((node) => ({
      ...node,
      children: node.children?.length ? filterZeroNodes(node.children) : node.children,
    }));
};

const applyDefaultTreeState = () => {
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
      tree.setCurrentNode?.(treeData.value[0]);
      setTimeout(() => {
        suppressInitialTreeClick.value = false;
      }, 0);
    });
  });
};

const handleAction = (type: string, row?: any) => {
  switch (type) {
    case "init":
      if (!isLoadTree.value) {
        // 没有左侧树，刷新数据列表
        if (!row?.initial) {
          handleAction("refresh");
        }
        break;
      }
      // 左树与右表同一轮启动，互不等待；右表默认展示全部应用系统。
      handleAction("refresh");
      state.leftLoading = true;
      Promise.all([
        $common.post("/sym/org/getOrgTree", { parentId: $user.orgRootId }),
        $common.post("/dst/statistics/common", {
          countFields: ["orgId"],
          conditions: [{ field: "assetType", type: "match", value: "app" }],
        }),
      ])
        .then(([data, countRes]) => {
          countMap.value = {
            org: countRes.orgId || {},
            _total: Number(countRes._total) || 0,
          };
          treeData.value[0].children = filterZeroNodes(data || []);
          suppressInitialTreeClick.value = true;
          applyDefaultTreeState();
        })
        .catch((error) => {
          console.error("加载应用系统组织树失败:", error);
          $message.error("组织机构树加载失败，请稍后重试");
        })
        .finally(() => {
          state.leftLoading = false;
        });
      break;
    case "node-click":
      // 左侧树点击
      state.leftSelectNode = row;
      if (suppressInitialTreeClick.value && !row?.value) {
        suppressInitialTreeClick.value = false;
        break;
      }
      suppressInitialTreeClick.value = false;
      handleAction("refresh");
      break;
    case "refresh":
      tableRef.value.refresh(true);
      break;
    case "详情":
      router.push({ path: "detail", query: { id: row.id, type: row.type, title: row.title } });
      break;
    case "编辑":
      openRegisterModal({
        registerClass: "app",
        registerData: { app: row },
      });
      break;
    case "登记业务系统":
      openRegisterModal({
        registerClass: "app",
        registerData: {},
      });
      break;
    case "删除":
      const appId = row?.tid || row?.id;
      if (!appId) {
        $message.error("该业务系统缺少标识，无法删除");
        return;
      }
      $common.handle({
        url: "/dst/application/delete",
        info: `是否删除该应用系统【${row.appName}】？确认后会一并清理其数据源、数据表、字段、资源目录、接入任务及关联脏数据。`,
        data: { tid: appId },
        done: () => {
          state.deletedIds.push(appId);
          setTimeout(() => {
            tableRef.value.refresh(false);
          }, 1000);
        },
      });
      break;
  }
};

// 列表接口
const fetchData = async ({ pageNo: pageNum, pageSize }) => {
  const conditions = [
    {
      field: "assetType",
      value: "app",
      type: "match",
    },
    {
      field: "appName",
      value: state.keyword ? `${state.keyword}` : undefined,
      type: "like",
    },
  ];
  // 非管理员添加组织id条件
  if (!$user.isAdmin) {
    conditions.push({
      field: "orgId",
      value: $user.orgId,
      type: "match",
    });
  } else {
    // 管理员：优先使用左侧树选中节点，否则使用顶部下拉筛选
    if (state.leftSelectNode?.value) {
      const { type = "org" } = state.leftSelectNode;
      const values = collectTreeNodeIds(state.leftSelectNode);
      conditions.push({
        field: `${type}Id`,
        value: values,
        type: "in",
      });
    } else if (state.org) {
      conditions.push({
        field: "orgId",
        value: state.org,
        type: "match",
      });
    }
  }
  const { list = [], total } = await $common.post("/dst/application/page", {
    sortField: "updatedTime",
    sortDir: "desc",
    pageNum,
    pageSize,
    conditions,
  });
  return {
    list: list
      .map((item: any) => ({
        ...item,
        displayStatus: 2,
      }))
      .sort((a, b) => b.tableNum - a.tableNum),
    total,
  };
};

// 获取数据库列表
const getDbList = async (appId) => {
  dbList.loading = true;
  try {
    const response = await $common.post("/dst/application/databases", { appId });
    dbList.data = response || [];
  } catch (error) {
    console.error("获取数据库列表失败:", error);
    dbList.data = [];
  } finally {
    dbList.loading = false;
  }
};

// 获取目录列表
const getCatalogList = async (appId) => {
  catalogList.loading = true;
  try {
    const response = await $common.post("/dst/catalog/list", {
      appId,
      assetType: "catalog",
    });
    catalogList.data = response || [];
  } catch (error) {
    console.error("获取目录列表失败:", error);
    catalogList.data = [];
  } finally {
    catalogList.loading = false;
  }
};

// 获取表列表
const getTableList = async (appId) => {
  tableList.loading = true;
  try {
    const response = await $common.post("/dst/application/tables", {
      appId,
      assetType: "table",
    });
    tableList.data = response || [];
  } catch (error) {
    console.error("获取目录列表失败:", error);
    tableList.data = [];
  } finally {
    tableList.loading = false;
  }
};
</script>

<style scoped lang="scss">
.left-panel {
  width: 100%;
  height: 100%;
  border-right: 1px solid rgba(5, 5, 5, 0.06);
  overflow-y: auto;

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

:deep(.right-panel .el-input__suffix .el-icon),
:deep(.left-panel .u-tree-search .el-input__suffix .el-icon),
:deep(.left-panel .u-tree-search .el-input__suffix-inner .el-icon) {
  color: #1677ff;
}

.right-panel {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;

  :deep(.pagination) {
    container-type: inline-size;
    display: flex;
    flex-wrap: nowrap;
    align-items: center;
    gap: 10px;
    min-width: 0;
    overflow: hidden;

    > span:first-child,
    .el-pagination {
      flex: 0 0 auto;
      white-space: nowrap;
    }
  }
}

.datasource-explorer-page,
.datasource-explorer-splitter {
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

.datasource-explorer-splitter {
  width: 100%;
  height: 100%;
}

.datasource-explorer-splitter :deep(.el-splitter-panel),
.datasource-explorer-splitter :deep(.el-splitter__panel) {
  min-width: 0 !important;
  overflow: hidden !important;
}

.list-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  flex: 0 0 auto;
  margin-bottom: 16px;
}

.list-toolbar__primary {
  flex: 0 0 auto;
}

.list-filters {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  flex: 0 1 auto;
  min-width: 0;
  margin-left: auto;
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
}

.keyword-search-trigger:hover,
.keyword-search-trigger:focus-visible {
  color: #0958d9;
  background: #eaf3ff;
  outline: none;
}

@media (max-width: 760px) {
  .list-toolbar {
    align-items: stretch;
  }

  .list-filters {
    width: 100%;
    margin-left: 0;
  }

  .keyword-input {
    flex: 1 1 100%;
    width: 100%;
    max-width: none;
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

.my-app-org-select .el-cascader-panel .el-cascader-menu .el-cascader-menu__wrap.el-scrollbar__wrap {
  height: 250px;
}
</style>
