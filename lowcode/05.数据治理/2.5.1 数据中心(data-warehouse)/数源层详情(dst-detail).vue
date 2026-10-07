<template>
  <div class="data-catalog-container card-container px-5 pt-5 pb-2">
    <el-splitter style="width: 100%; height: 100%">
      <el-splitter-panel size="360px" min="10%">
        <div class="left-panel">
          <div class="left-header flex items-center justify-between">
            <h3>数源层 DTS</h3>
          </div>

          <div class="tree-container">
            <UTree
              ref="treeRef"
              :data="treeData"
              :node-key="'value'"
              :highlight-key="orgId"
              :search="true"
              :expand="false"
              :expand-on-click-node="false"
              :search-placeholder="'请输入关键字'"
              class="tree-container-custom"
              @node-click="handleNodeClick"
              @highlight-done="handleHighlightDone"
            >
              <template #node="{ node }">
                <u-tree-node
                  show-icon
                  style="--gap: 0"
                  :keyword="treeRef?.searchValue"
                  :label="node.label"
                >
                  <template #count>
                    <span
                      v-if="get(countMap, `${node?.data.type || 'org'}.${node.data.value}`, 0) > 0"
                    >
                      ({{ get(countMap, `${node?.data.type || "org"}.${node.data.value}`, 0) }})
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
        <div class="right-panel pl-4">
          <DataTable
            ref="tableRef"
            :columns="tableColumns"
            :data="fetchData"
            :immediate="false"
            :show-index="true"
            style="height: 100%"
          >
            <template #toolbar>
              <div class="flex justify-between items-center">
                <div>
                  <el-button type="primary" icon="refresh" @click="refreshData">
                    刷新数据表
                  </el-button>
                </div>
                <div>
                  <el-input
                    v-model.trim="searchQuery"
                    :suffix-icon="'el-icon-Search'"
                    placeholder="输入关键词搜索"
                    clearable
                    style="width: 250px"
                    @keydown.enter="refreshData"
                  />
                </div>
              </div>
            </template>
            <template #column-tableName="{ row }">
              <el-link
                type="primary"
                cursor-pointer
                @click="handleDetailClick(row.id, row.tableName, 'table')"
              >
                <Icon icon="table"></Icon>
                {{ row.tableName }}
              </el-link>
            </template>
            <template #column-dbId="{ row }">
              <el-link
                type="primary"
                cursor-pointer
                @click="handleDetailClick(row.dbId, row.dbName, 'db')"
              >
                <Icon :icon="row.dbType || 'db'"></Icon>
                {{ row.dbName || "-" }}
              </el-link>
            </template>
            <template #column-appName="{ row }">
              <el-link
                type="primary"
                cursor-pointer
                @click="handleDetailClick(row.appId, row.appName, 'app')"
              >
                <Icon icon="app"></Icon>
                {{ row.appName }}
              </el-link>
            </template>
          </DataTable>
        </div>
      </el-splitter-panel>
    </el-splitter>
  </div>
</template>

<script setup lang="ts">
import { get } from "lodash-es";
import { ref, onMounted } from "vue";
import { useRouter, useRoute } from "vue-router";

const router = useRouter();
const route = useRoute();

const orgId = route.query.orgId as string | undefined;
const treeRef = ref<any>(null);

// 当前操作的节点
const currentNode = ref();

// 树数据
const treeData = ref<any[]>([
  {
    name: "全部",
    value: "",
    children: [],
  },
]);

// 表格列配置
const tableColumns = ref([
  { prop: "tableName", label: "表名" },
  { prop: "tableNameCn", label: "表注释" },
  {
    prop: "dbId",
    label: "所属数据库",
  },
  { prop: "appName", label: "所属系统" },
  {
    prop: "fieldCount",
    label: "字段数",
    formatter: (row: any, column: any, cellValue: number) => {
      if (cellValue === null || cellValue === undefined) {
        return "0 个";
      }
      return cellValue + " 个";
    },
  },
  {
    prop: "storageSize",
    label: "数据量",
  },
  {
    prop: "recordCount",
    label: "记录数",
    type: "number",
    formatter: (row: any, column: any, cellValue: number) => {
      if (cellValue === null || cellValue === undefined || cellValue === 0) {
        return "0 条";
      }
      if (cellValue >= 10000) {
        return (cellValue / 10000).toFixed(2) + " 万条";
      }
      return cellValue + " 条";
    },
  },
  { prop: "flowStatus", label: "状态", width: 100, options: "status" },
  { prop: "updatedTime", label: "数据更新时间", width: 170 },
  // {
  //   prop: "actions",
  //   label: "状态",
  //   width: 120,
  //   buttons: [
  //     {
  //       type: "primary",
  //       label: "查看目录",
  //       icon: "el-icon-view",
  //       link: true,
  //       if(row: any) {
  //         return row.status === "已登记";
  //       },
  //     },
  //     {
  //       type: "primary",
  //       label: "去登记",
  //       icon: "el-icon-EditPen",
  //       link: true,
  //       if(row: any) {
  //         return row.status === "待登记";
  //       },
  //     },
  //   ],
  // },
]);

// 搜索和筛选条件
const tableRef = ref<any>(null);
const searchQuery = ref("");
const countMap = ref({});

/** 判断节点及其所有后代的统计值是否全为 0 */
const isAllZero = (node: any): boolean => {
  const key = `${node.type || "org"}.${node.value}`;
  const count = get(countMap.value, key, 0);
  if (count !== 0) return false;
  if (node.children?.length) {
    return node.children.every((child: any) => isAllZero(child));
  }
  return true;
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

const fetchTreeData = async () => {
  await Promise.all([
    $common.post("/sym/org/getOrgAppDbTree", { parentId: $user.orgRootId }),
    $common.post("/dwm/center/dstTable"),
  ])
    .then(([data, countRes]) => {
      countMap.value = {
        org: countRes.orgId,
        app: countRes.appId,
        db: countRes.dbId,
      };
      treeData.value[0].children = filterZeroNodes(data);
    })
    .catch((error) => {
      console.error("API 请求失败:", error);
    });
};

const handleDetailClick = (id: string, title: string, type: string) => {
  console.log("handleDetailClick", title, type);
  router.push({
    path: "detail",
    query: { id, type, title },
  });
};

const fetchData = async ({ pageNo: pageNum, pageSize }: { pageNo: number; pageSize: number }) => {
  const conditions = [
    // 资产类型
    {
      field: "assetType",
      value: "table",
      type: "match",
    },
  ];
  if (searchQuery.value) {
    // 关键字查询
    conditions.push({
      field: "tableName",
      value: searchQuery.value,
      type: "like",
    });
  }

  if (currentNode.value) {
    // 左侧树查询
    const { type = "org", value, serialNumber, children } = currentNode.value;

    if (type === "org") {
      // org 节点：查找该部门下nodeId为业务库的db
      const dbList = await $common.post("/dst/database/table/list", {
        nodeId: "业务库",
        assetType: "db",
        orgId: value || undefined, // 考虑查全部时，不传orgId
        assetStatus: 2,
      });
      const dbIds = (dbList || []).map((child: any) => child.tid).filter(Boolean);
      conditions.push({
        field: "dbId",
        value: dbIds?.length ? dbIds : [""],
        type: dbIds?.length ? "in" : "match",
      });
    }

    if (type === "app") {
      // app 节点：查找app下nodeId为业务库的db
      const dbList = await $common.post("/dst/database/table/list", {
        nodeId: "业务库",
        assetType: "db",
        appId: value,
        assetStatus: 2,
      });
      const dbIds = (dbList || []).map((child: any) => child.tid).filter(Boolean);
      conditions.push({
        field: "dbId",
        value: dbIds?.length ? dbIds : [""],
        type: dbIds?.length ? "in" : "match",
      });
    }
    if (type === "db") {
      conditions.push({
        field: `${type}Id`,
        value,
        type: "match",
      });
    }
  }

  const response = await $common.post("/dst/database/table/page", {
    sortField: "updatedTime",
    sortDir: "desc",
    pageNum,
    pageSize,
    conditions,
  });
  const data = response.data || response;
  const list = data.list || [];
  const total = data.total || 0;
  const fieldCountSum = list.reduce((acc, cur) => acc + (+cur.fieldCount || 0), 0);
  const recordCountSum = list.reduce((acc, cur) => acc + (cur.recordCount || 0), 0);
  console.log("fieldCountSum:", fieldCountSum);
  console.log("recordCountSum:", recordCountSum);
  return {
    list,
    total,
  };
};

const refreshData = () => {
  tableRef.value.refresh(false);
};

// 初始化数据
onMounted(async () => {
  await fetchTreeData();
});

// 树节点点击
const handleNodeClick = (node: any) => {
  currentNode.value = node;
  refreshData();
};

/** 高亮完成后：更新当前节点并刷新右表 */
const handleHighlightDone = (node: any) => {
  currentNode.value = node;
  refreshData();
};

// // 批量发布
// const batchPublish = () => {
//   if (selectedRows.value.length === 0) {
//     // 如果没有选中行，可以添加提示
//     return;
//   }
//   // 显示批量发布弹窗
//   batchPublishDialogVisible.value = true;
// };
</script>

<style scoped lang="scss">
.data-catalog-container {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;

  .left-panel {
    width: 100%;
    height: 100%;
    border-right: 1px solid rgba(5, 5, 5, 0.06);
    overflow-y: auto;

    .left-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      border-bottom: 1px solid rgba(5, 5, 5, 0.06);
      padding-bottom: 10px;
      margin-bottom: 10px;
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

      :deep(.el-tree) {
        --el-tree-node-content-height: 100%;
      }

      .tree-container-custom {
        height: 100%;

        :deep(.u-tree-search) {
          margin-right: 14px;
        }

        :deep(.el-tree) {
          padding-right: 14px;
        }
      }

      :deep(.el-tree-node__content) {
        &:hover {
          .more-icon {
            opacity: 1;
          }
        }

        &.is-current {
          background-color: var(--el-color-primary-light-9) !important;
          color: var(--el-color-primary);
        }
      }

      :deep(.el-dropdown-menu__item.delete-item) {
        &:hover {
          background-color: #fef0f0;
        }
      }
    }
  }

  .right-panel {
    width: 100%;
    height: 100%;
    :deep(.el-button .el-icon + span) {
      margin-left: 3px;
    }
  }
}
</style>
