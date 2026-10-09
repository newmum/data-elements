<template>
  <div class="card-container datasource-explorer-page flex flex-col px-5 pt-5 pb-2 w-full h-full">
    <el-splitter class="datasource-explorer-splitter">
      <el-splitter-panel v-if="isLoadTree" size="300px" min="10%">
        <div class="left-panel">
          <div class="panel-header">
            <h3>数据源</h3>
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
                    <span v-if="getTreeNodeCount(node.data) > 0">
                      ({{ getTreeNodeCount(node.data) }})
                    </span>
                    <span v-else></span>
                  </template>
                  <template #icon>
                    <Icon
                      :icon="
                        node.data?.type === 'app'
                          ? 'app'
                          : node.expanded
                            ? 'folder-open'
                            : 'folder'
                      "
                      :color="node.data?.type === 'app' ? '#7a5af8' : '#0b8bf9'"
                      class="mr-1"
                      :size="node.data?.type === 'app' ? '19' : '23'"
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
        <el-button
          type="primary"
          icon="plus"
          @click="handleAction('register')"
        >
          登记数据源
        </el-button>
      </div>

      <div class="list-filters">
        <el-select
          v-model="state.dbType"
          clearable
          placeholder="数据库类型"
          class="filter-select"
          @change="handleAction('query')"
        >
          <el-option
            v-for="item in dbTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          >
            <div class="db-type-option">
              <el-icon class="db-type-option__icon" :title="dbTypeNameMap[item.value] || item.value">
                <Icon :icon="item.icon || 'database-network'" />
              </el-icon>
              <span>{{ item.label }}</span>
            </div>
          </el-option>
        </el-select>
        <el-select
          v-model="state.registrationState"
          clearable
          placeholder="登记状态"
          class="filter-select filter-select--status"
          @change="handleAction('query')"
        >
          <el-option label="待登记" value="pending" />
          <el-option label="未登记完成" value="incomplete" />
          <el-option label="已完成" value="completed" />
        </el-select>
        <el-input
          v-model.trim="state.keyword"
          class="keyword-input"
          placeholder="请输入数据源名称、业务系统名称或库名"
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
              @click.stop="handleAction('query')"
              @keydown.enter.prevent="handleAction('query')"
            >
              <Icon icon="search" />
            </span>
          </template>
        </el-input>
      </div>
    </div>

    <data-table ref="tableRef" show-index :columns="cols" :data="fetchData">
      <template #column-dbName="{ row }">
        <el-text
          type="primary"
          cursor-pointer
          @click="handleAction('详情', { id: row.tid || row.id, title: row.dbName, type: 'db' })"
        >
          <Icon :icon="datasourceTypeIcon(row)" />

          {{ row.dbName }}
        </el-text>
      </template>

      <template v-if="!isAppDetail" #column-appName="{ row }">
        <span
          v-if="row.appId"
          type="primary"
        >
          {{ row.appName }}
        </span>
      </template>

      <template v-if="isAppDetail" #column-appName="{ row }">
        <span v-if="row.appId">
          {{ row.appName }}
        </span>
      </template>

      <template #column-database="{ row }">
        <el-tooltip
          v-if="isConnectionUnavailable(row)"
          content="该数据源未提供数据库连接信息"
          placement="top"
        >
          <el-text class="connection-unavailable" type="info">
            <Icon icon="el-icon-Lock" />
            暂不提供
          </el-text>
        </el-tooltip>

        <el-text v-else-if="row.database || row.schema">
          {{ row.database || row.schema }}
        </el-text>

        <el-text v-else type="info">-</el-text>
      </template>

      <template #column-tableNum="{ row }">
        <span v-if="Number(row.tableNum ?? 0) > 0" class="table-count-text">
          {{ Number(row.tableNum ?? 0).toLocaleString("zh-CN") }}
        </span>
        <span v-else class="table-count-text is-empty">未探查</span>
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
            <el-link type="primary">{{ row.catalogNum ?? 0 }}个</el-link>
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

        <el-link v-else type="info" disabled>{{ row.catalogNum ?? 0 }}个</el-link>
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
      <template #column-displayStatus="{ row }">
        <el-tag :type="datasourceStatusType(row)" size="small" effect="light">
          {{ datasourceStatusText(row) }}
        </el-tag>
      </template>
    </data-table>

    <el-drawer
      v-model="tableState.open"
      size="min(1080px, 94vw)"
      resizable
      class="catalog-register-drawer"
      :aria-label="`登记目录 · ${tableState.db.dbName || '当前数据源'}`"
      destroy-on-close
    >
      <template #header>
        <div class="catalog-drawer-header">
          <el-tabs v-model="tableState.seg" class="catalog-drawer-tabs" @tab-change="changeSeg">
            <el-tab-pane
              v-for="option in catalogSegmentOptions"
              :key="option.value"
              :label="option.label"
              :name="option.value"
            />
          </el-tabs>
          <span class="catalog-drawer-context" :title="`${tableState.db.dbName || '当前数据源'} · ${tableState.db.database || tableState.db.schema || ''}`">
            {{ tableState.db.dbName || "当前数据源" }}
            <span v-if="tableState.db.database || tableState.db.schema">
              · {{ tableState.db.database || tableState.db.schema }}
            </span>
          </span>
          <div class="catalog-drawer-actions">
            <span v-if="tableState.selection.length" class="catalog-selection-tip">
              已选 {{ tableState.selection.length }} 张（当前页）
            </span>
            <el-button
              v-if="tableState.seg === '待登记'"
              type="primary"
              icon="plus"
              :loading="tableState.preparing"
              @click="registerCatalog(tableState.selection)"
            >登记目录</el-button>
            <el-button
              v-if="tableState.seg === '已登记'"
              type="primary"
              icon="edit"
              @click="registerCatalog(null, tableState.selection)"
            >编辑数据目录</el-button>
            <el-button
              circle
              plain
              icon="refresh"
              title="重新采集数据表（与登记第二步一致）"
              aria-label="重新采集数据表"
              :loading="tableState.collecting"
              :disabled="tableState.loading || tableState.collecting"
              @click="refreshCollectedTables()"
            />
          </div>
        </div>
      </template>

      <div
        v-loading="tableState.loading"
        class="catalog-drawer-body"
        :class="{ 'has-error': tableState.error }"
        :element-loading-text="tableState.collecting ? `正在采集数据表 ${tableState.collectionProgress}%` : '正在读取数据表快照'"
      >
        <el-alert
          v-if="tableState.collecting"
          :title="`正在后台采集数据表 ${tableState.collectionProgress}%，完成后自动刷新列表`"
          type="info"
          show-icon
          :closable="false"
        />
        <el-alert
          v-if="tableState.error"
          :title="tableState.error"
          type="warning"
          show-icon
          :closable="false"
        />

        <div class="catalog-table-shell">
          <data-table
            :key="`${tableState.db.tid}:${tableState.seg}`"
            ref="gridRef"
            :data="fetchCatalogPage"
            :columns="catalogColumns"
            :immediate="false"
            :show-selection="true"
            :show-index="true"
            :hide-pagination-on-single-page="false"
            row-key="tid"
            :operation-width="80"
            operation-align="center"
            class="catalog-paged-table"
            @selection-change="tableState.selection = $event"
            @filter-change="changeCatalogFilters"
          >
            <template #column-tableName="{ row }">
              <el-text class="catalog-table-name">
                <el-tooltip :content="tableTypeTip(row)" placement="top">
                  <span :class="['catalog-table-type-icon', { 'is-view': isViewTable(row) }]">
                    <Icon :icon="tableTypeIcon(row)" />
                  </span>
                </el-tooltip>
                {{ row.tableName }}
              </el-text>
            </template>

            <template #column-catalogName="{ row }">
              <el-text>
                <Icon icon="catalog" />

                {{ row.catalogName }}
              </el-text>
            </template>

            <template #column-businessType="{ row }">
              <el-tooltip :content="row.businessTypeReason || '已保存的数据表业务类型'" placement="top">
                <el-tag size="small" :type="catalogBusinessTypeTag(row.businessType)">
                  {{ row.businessType === '不确定' ? '暂不处理' : row.businessType }}
                </el-tag>
              </el-tooltip>
            </template>

            <template #column-assetStatus="{ row }">
              <dict-label :model-value="row.assetStatus" options="status" />
            </template>

            <template #operation="{ row }">
              <el-link
                v-if="!row.catalogId"
                :disabled="tableState.preparing"
                @click="registerCatalog([row])"
              >
                登记目录
              </el-link>

              <el-link v-if="row.catalogId" @click="openDetail(row)">查看详情</el-link>
            </template>
          </data-table>
        </div>
      </div>

      <el-drawer v-model="tableState.openDetail" size="950" title="查看详情" resizable>
        <detail-page
          :id="tableState.catalogId"
          type="catalog"
          :is-breadcrumbs="false"
        ></detail-page>
      </el-drawer>
    </el-drawer>
        </div>
      </el-splitter-panel>
    </el-splitter>
  </div>
</template>

<script setup>
import { reactive, computed, nextTick, ref, watch, onActivated, onMounted, onBeforeUnmount } from "vue";
import { resolveCollectedBusinessType, tableBusinessTypeOptions } from "@/utils";
import { useRouter, useRoute } from "vue-router";
import { get } from "lodash-es";
import { useRegisterModal, useDetailDialog } from "@/composables";
import { useDictStore } from "@/store";
const props = defineProps({
  appId: { type: String, default: "" },
  dataSourceType: { type: String, default: "" },
  editable: { type: Boolean, default: true },
});
// 登记弹框
const { openRegisterModal, completedVersion } = useRegisterModal();
const { openDetailDialog } = useDetailDialog();
const router = useRouter();
const route = useRoute();
const tableRef = ref();
const treeRef = ref();
const skipFirstActivation = ref(true);
const treeData = ref([
  {
    name: "全部",
    label: "全部",
    value: "",
    serialNumber: $user.orgRootSerialNumber,
    children: [],
  },
]);
const countMap = ref({});
const orgTreeSource = ref([]);
const treeStatistics = ref({
  total: 0,
  assignedTotal: 0,
  unassignedTotal: 0,
  orgCounts: {},
  orgApps: {},
  dbTypes: {},
});
const datasourceStatusText = (row) => {
  return ["待登记", "未登记完成", "已完成"][Number(row?.assetStatus ?? row?.asset_status ?? 0)] || "待登记";
};
const datasourceStatusType = (row) => {
  return Number(row?.assetStatus ?? row?.asset_status) === 2 ? "success" : "warning";
};
const availableDbTypes = ref({});
// 动态组件未及时传入 appId 时，使用详情页 URL 的 id 作为可靠兜底。
const detailAppId = computed(() =>
  props.appId || (route.query.type === "app" ? String(route.query.id || "") : "")
);
// 全局数据源列表与业务系统列表保持一致：管理员显示组织机构树，应用详情内不重复展示。
const isLoadTree = computed(() => $user.isAdmin && !detailAppId.value);
// 业务系统详情页严格按当前系统 ID 查询其关联数据源。
const state = reactive({
  status: undefined,
  keyword: undefined,
  dbType: undefined,
  registrationState: undefined,
  deletedIds: [],
  open: false,
  leftSelectNode: {},
  leftLoading: false,
});
const dbTypeNameMap = {
  mysql: "MySQL",
  oracle: "Oracle",
  oceanbasemysql: "OceanBase MySQL",
  oceanbaseoracle: "OceanBase Oracle",
  gaussdb: "GaussDB",
  gbase8a: "GBase 8a",
  sqlserver: "SQL Server",
  hive: "Hive",
  vertica: "Vertica",
  dameng: "达梦数据库",
  postgresql: "PostgreSQL",
  kingbase8: "人大金仓",
  maxcompute: "MaxCompute",
  minio: "MinIO",
  ftp: "FTP",
  api: "API",
  kafka: "Kafka",
  elasticsearch: "Elasticsearch 搜索引擎",
  other: "其他类型",
};
const dbTypeOrder = [
  "mysql",
  "oracle",
  "oceanbasemysql",
  "oceanbaseoracle",
  "gaussdb",
  "gbase8a",
  "sqlserver",
  "hive",
  "maxcompute",
  "vertica",
  "dameng",
  "postgresql",
  "kingbase8",
  "minio",
  "ftp",
  "api",
  "kafka",
  "elasticsearch",
  "other",
];
const dbTypeIconMap = {
  mysql: "mysql",
  oracle: "oracle",
  oceanbasemysql: "oceanbasemysql",
  oceanbaseoracle: "oceanbaseoracle",
  gaussdb: "gaussdb",
  gbase8a: "gbase8a",
  sqlserver: "sqlserver",
  hive: "hive",
  maxcompute: "maxcompute",
  vertica: "vertica",
  dameng: "dameng",
  postgresql: "postgresql",
  kingbase8: "kingbase8",
  minio: "minio",
  ftp: "ftp",
  api: "api",
  kafka: "kafka",
  elasticsearch: "elasticsearch",
  other: "database-network",
};
const normalizeDbType = (value) => {
  const normalized = String(value || "other").toLowerCase().replace(/[\s_-]/g, "");
  const alias = {
    pg: "postgresql",
    postgres: "postgresql",
    postgresql: "postgresql",
    mssql: "sqlserver",
    sqlserver: "sqlserver",
    dm: "dameng",
    dameng: "dameng",
    kingbase: "kingbase8",
    kingbasees: "kingbase8",
    kingbase8: "kingbase8",
    oceanbasemysql: "oceanbasemysql",
    oceanbaseoracle: "oceanbaseoracle",
    es: "elasticsearch",
    elastic: "elasticsearch",
    elasticsearch: "elasticsearch",
  };
  return alias[normalized] || normalized || "other";
};
const datasourceTypeIcon = (row) => dbTypeIconMap[normalizeDbType(row?.dbType)] || "database-network";
const dbTypeOptions = computed(() => {
  const countMap = {};
  Object.entries(availableDbTypes.value || {}).forEach(([value, count]) => {
    const key = normalizeDbType(value);
    countMap[key] = Number(countMap[key] || 0) + Number(count || 0);
  });
  return Object.entries(countMap)
    .map(([value, count]) => ({
      value,
      icon: dbTypeIconMap[value] || "database-network",
      label: `${dbTypeNameMap[value] || value}（${Number(count || 0)}）`,
    }))
    .sort((a, b) => {
      const ai = dbTypeOrder.includes(a.value) ? dbTypeOrder.indexOf(a.value) : 999;
      const bi = dbTypeOrder.includes(b.value) ? dbTypeOrder.indexOf(b.value) : 999;
      if (ai !== bi) return ai - bi;
      return a.label.localeCompare(b.label, "zh-CN");
    });
});
// 表格列表
const tableList = reactive({
  loading: false,
  data: [],
});
// 目录列表
const catalogList = reactive({
  loading: false,
  data: [],
});
// app详情页时，app文字不可被点击
const isAppDetail = computed(() => route.path.endsWith("/detail") && route.query.type === "app");
/** showConnect=0 表示数据源只登记业务信息，不对外提供连接参数。 */
const isConnectionUnavailable = (row) =>
  row?.showConnect === 0 || row?.showConnect === "0" || row?.showConnect === false;
// 监听关闭登记弹框
watch(completedVersion, () => {
  if (tableState.open && tableState.db?.tid) {
    loadTables(tableState.db.tid, { silent: true });
  }
  handleAction("init");
});
onActivated(() => {
  // KeepAlive 首次挂载时 data-table 会自行加载，跳过第一次 activated，避免首屏重复请求。
  if (skipFirstActivation.value) {
    skipFirstActivation.value = false;
    return;
  }
  handleAction("refresh");
});
onMounted(() => {
  // 首屏表格由 data-table 自行加载；初始化树时不再额外刷新右侧列表。
  handleAction("init", { initial: true });
});

/** 递归汇总节点自身与全部后代的数据源数量，父节点显示完整合计。 */
const getTreeNodeCount = (node) => {
  if (!node) return 0;
  if (node.type === "app") return Number(node.count || 0);
  if (!node.value) return Number(treeStatistics.value.total || 0);
  const key = `${node.type || "org"}.${node.value}`;
  const ownCount = Number(get(countMap.value, key, 0)) || 0;
  const orgChildren = (node.children || []).filter((child) => child.type !== "app");
  if (orgChildren.length === 0) return ownCount;
  return ownCount + orgChildren.reduce(
    (total, child) => total + getTreeNodeCount(child),
    0
  );
};

/** 父级节点筛选时同时包含其所有后代部门，确保数量与右侧列表口径一致。 */
const collectTreeNodeIds = (node) => {
  if (!node) return [];
  if (node.type === "app") return [];
  return [
    node.value,
    ...(node.children || [])
      .filter((child) => child.type !== "app")
      .flatMap((child) => collectTreeNodeIds(child)),
  ]
    .filter(Boolean);
};

const isAllZero = (node) => getTreeNodeCount(node) === 0;

/** 隐藏自身及后代均无数据源的空节点，降低树的浏览噪音。 */
const filterZeroNodes = (nodes) =>
  (nodes || [])
    .filter((node) => !isAllZero(node))
    .map((node) => ({
      ...node,
      children: node.children?.length ? filterZeroNodes(node.children) : node.children,
    }));

/** 仅在组织机构末级节点下追加业务系统，数量直接来自 ES 的 orgId→appId 嵌套聚合。 */
const appendApplicationNodes = (nodes) =>
  (nodes || []).map((node) => {
    const orgChildren = appendApplicationNodes(node.children || []);
    const appChildren =
      orgChildren.length === 0
        ? (treeStatistics.value.orgApps?.[node.value] || []).map((app) => ({
            ...app,
            name: app.label,
            serialNumber: app.value,
            children: [],
          }))
        : [];
    return {
      ...node,
      type: "org",
      children: [...orgChildren, ...appChildren],
    };
  });

const rebuildTree = () => {
  treeData.value[0].children = appendApplicationNodes(filterZeroNodes(orgTreeSource.value));
  applyDefaultTreeState();
};

/**
 * 首屏仅展开“全部 → 第一层机构 → 内设机构”的组织路径；更下级的部门保持收起。
 * 同时静默高亮“全部”，不触发 node-click，避免右侧列表再次请求。
 */
const applyDefaultTreeState = () => {
  nextTick(() => {
    nextTick(() => {
      const tree = treeRef.value?.getTree?.();
      if (!tree) return;

      let current = treeData.value[0];
      for (let depth = 0; current && depth < 3; depth += 1) {
        const treeNode = tree.getNode(current.value);
        if (treeNode) treeNode.expanded = true;
        current = (current.children || []).find((child) => child.type !== "app");
      }
      tree.setCurrentNode?.(treeData.value[0]);
    });
  });
};

const statisticsPayload = () => ({
  keyword: state.keyword || "",
  dbType: state.dbType || "",
  registrationState: state.registrationState || "",
  orgIds: $user.isAdmin ? [] : [$user.orgId].filter(Boolean),
});

/** 刷新 ES 分面统计，不访问数据源主表或数据表。 */
const loadTreeStatistics = async () => {
  const result = (await $common.post(
    "/dst/database/treeStatistics",
    statisticsPayload()
  )) || {};
  treeStatistics.value = {
    total: Number(result.total || 0),
    assignedTotal: Number(result.assignedTotal || 0),
    unassignedTotal: Number(result.unassignedTotal || 0),
    orgCounts: result.orgCounts || {},
    orgApps: result.orgApps || {},
    dbTypes: result.dbTypes || {},
  };
  countMap.value = { org: treeStatistics.value.orgCounts };
  if (Object.keys(availableDbTypes.value).length === 0) {
    availableDbTypes.value = treeStatistics.value.dbTypes;
  }
  rebuildTree();
};

const handleAction = (type, row) => {
  switch (type) {
    case "init":
      if (!isLoadTree.value) {
        if (!row?.initial) {
          handleAction("refresh");
        }
        break;
      }
      state.leftLoading = true;
      Promise.all([
        $common.post("/sym/org/getOrgTree", { parentId: $user.orgRootId }),
        $common.post("/dst/database/treeStatistics", statisticsPayload()),
      ])
        .then(([data, countRes]) => {
          orgTreeSource.value = data || [];
          treeStatistics.value = {
            total: Number(countRes?.total || 0),
            assignedTotal: Number(countRes?.assignedTotal || 0),
            unassignedTotal: Number(countRes?.unassignedTotal || 0),
            orgCounts: countRes?.orgCounts || {},
            orgApps: countRes?.orgApps || {},
            dbTypes: countRes?.dbTypes || {},
          };
          availableDbTypes.value = treeStatistics.value.dbTypes;
          countMap.value = { org: treeStatistics.value.orgCounts };
          rebuildTree();
          if (!row?.initial) {
            handleAction("refresh");
          }
        })
        .catch((error) => {
          console.error("加载数据源组织机构树失败:", error);
          $message.error("组织机构树加载失败，请稍后重试");
        })
        .finally(() => {
          state.leftLoading = false;
        });
      break;
    case "node-click":
      state.leftSelectNode = row || {};
      handleAction("refresh");
      break;
    case "query":
      state.leftLoading = true;
      // 左树分面与右侧分页互不依赖，并发请求可避免表格额外等待一次 ES 聚合。
      loadTreeStatistics()
        .catch((error) => {
          console.error("刷新数据源筛选统计失败:", error);
          $message.error("查询失败，请稍后重试");
        })
        .finally(() => {
          state.leftLoading = false;
        });
      handleAction("refresh");
      break;
    case "reset-query":
      state.keyword = undefined;
      state.dbType = undefined;
      state.registrationState = undefined;
      state.leftSelectNode = {};
      handleAction("query");
      break;
    case "refresh":
      tableRef.value.refresh(true);
      break;
    case "登记数据目录":
      tableState.open = true;
      tableState.db = row;
      tableState.seg = "待登记";
      tableState.pendingTotal = 0;
      tableState.registeredTotal = 0;
      tableState.filters = {};
      tableState.selection = [];
      tableState.error = "";
      nextTick(() => {
        loadTables(row.tid);
      });
      break;
    case "登记数据表资源":
      openRegisterModal({
        registerClass: "dbTable",
        registerData: { db: [row], currentStep: 1 },
      });
      break;
    case "详情":
      if (props.dataSourceType === "dwm") {
        openDetailDialog({ ...row, dataSourceType: props.dataSourceType });
      } else {
        router.push({ path: "detail", query: { id: row.id, type: row.type, title: row.title } });
      }
      break;
    case "register":
      // 登记数据源
      openRegisterModal({
        registerClass: "db",
        // 若在app详情中的db列表，登记需附带appId
        registerData: detailAppId.value ? { app: { tid: detailAppId.value } } : {},
      });
      break;
    case "编辑":
      openRegisterModal({
        // 数据探查入口仅编辑数据源配置，保存后直接完成，不进入五步登记。
        registerClass: "db",
        registerMode: "explore-edit",
        registerData: { db: [row] },
      });
      break;
    case "删除":
      $common.handle({
        url: "/dst/database/delete",
        info: `是否删除该数据【${row.dbName}】`,
        data: { tid: row.tid },
        done: () => {
          state.deletedIds.push(row.tid);
          setTimeout(() => {
            handleAction("init");
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
      value: "db",
      type: "match",
    },
  ];
  if (state.keyword) {
    conditions.push({
      field: "keyword",
      value: state.keyword,
      type: "match",
    });
  }
  if (state.dbType) {
    conditions.push({
      field: "dbType",
      value: state.dbType,
      type: "match",
    });
  }
  if (state.registrationState) {
    conditions.push({
      field: "registrationState",
      value: state.registrationState,
      type: "match",
    });
  }
  if (detailAppId.value) {
    conditions.push({
      field: "appId",
      value: detailAppId.value,
      type: "match",
    });
  }
  // 非管理员添加组织id条件
  if (!$user.isAdmin) {
    conditions.push({
      field: "orgId",
      value: $user.orgId,
      type: "match",
    });
  } else if (isLoadTree.value && state.leftSelectNode?.value) {
    if (state.leftSelectNode?.type === "app") {
      conditions.push({
        field: "appId",
        value: state.leftSelectNode.value,
        type: "match",
      });
    } else {
      conditions.push({
        field: "orgId",
        value: collectTreeNodeIds(state.leftSelectNode),
        type: "in",
      });
    }
  }
  const { list = [], total } = await $common.post("/dst/database/page", {
    sortField: "updatedTime",
    sortDir: "desc",
    pageNum,
    pageSize,
    conditions,
  });
  return {
    list: list
      .map((item) => ({
        ...item,
        tableNum: Number(item.tableNum ?? item.table_num ?? 0),
        displayStatus: Number(item.assetStatus ?? item.asset_status ?? 0),
      }))
,
    total,
  };
};
// 获取表格列表
const getTableList = async (dbId) => {
  tableList.loading = true;
  try {
    const response = await $common.post("/dst/catalog/list", {
      dbId,
      assetType: "table",
    });
    tableList.data = response || [];
  } catch (error) {
    console.error("获取表格列表失败:", error);
    tableList.data = [];
  } finally {
    tableList.loading = false;
  }
};
// 获取目录列表
const getCatalogList = async (dbId) => {
  catalogList.loading = true;
  try {
    const response = await $common.post("/dst/catalog/list", {
      dbId,
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
// 表格配置
const cols = [
  { label: "数据源名称", prop: "dbName", minWidth: 250 },
  { label: "业务系统名称", prop: "appName", minWidth: 270, maxWidth: 420 },
  { label: "库名/schema", prop: "database", width: 170 },
  // { label: "数据库类型", prop: "dbType" },
  {
    label: "表数量（张）",
    prop: "tableNum",
    type: "num",
    precision: 0,
    width: 120,
    props: { emptyText: "-" },
    sortable: true,
  },
  {
    label: "创建时间",
    prop: "createdTime",
    width: 180,
    align: "center",
  },
  // {
  //   label: "目录数量",
  //   prop: "catalogNum",
  //   width: 120,
  //   align: "center",
  //   props: { suffix: "个" },
  //   sortable: true,
  // },
  { label: "状态", prop: "displayStatus", align: "center" },
  {
    type: "buttons",
    label: "操作",
    prop: "action",
    align: "right",
    buttons: [
      {
        type: "primary",
        label: "登记目录",
        click: (row) => handleAction("登记数据目录", row),
      },
      {
        if(row) {
          return true;
        },
        type: "primary",
        label: "编辑",
        click: (row) => handleAction("编辑", row),
      },
      {
        if(row) {
          return true;
        },
        type: "danger",
        // 接口是异步删除，防止重复点击
        disabled: (row) => state.deletedIds?.includes(row.tid),
        label: "删除",
        click: (row) => handleAction("删除", row),
      },
    ],
  },
];
/**

 * 登记数据目录属性

 */
const gridRef = ref();
const tableState = reactive({
  open: false,
  openDetail: false,
  catalogId: "",
  seg: "待登记",
  loading: false,
  preparing: false,
  collecting: false,
  collectionProgress: 0,
  error: "",
  db: {}, // 当前选择的数据库
  pendingTotal: 0,
  registeredTotal: 0,
  selection: [],
  filters: {},
  columns1: [
    { type: "checkbox", width: 40, align: "center" },
    { type: "seq", width: 60, align: "center" },
    {
      field: "tableName",
      title: "数据表名称",
      // 筛选
      filters: [{ data: "" }],
      filterMethod({ option, row, column }) {
        if (option.data) {
          const keys = option.data.split("\n").filter((e) => e);
          return keys.some((key) => `${row[column.field]}`.indexOf(key) > -1);
        }
        return true;
      },
      slots: { default: "tableName", filter: "inputFilter" },
      minWidth: 200,
    },
    {
      field: "tableNameCn",
      title: "表注释",
      minWidth: 200,
    },
    { field: "fieldCount", title: "字段数", width: 86, align: "center" },
    {
      field: "businessType",
      title: "业务类型",
      slots: { default: "businessType" },
      filters: tableBusinessTypeOptions.map((item) => ({ label: item.label, value: item.value })),
      width: 90,
    },
    {
      field: "action",
      title: "操作",
      slots: { default: "action" },
      fixed: "right",
      align: "center",
      width: 80,
    },
  ],
  columns2: [
    { type: "checkbox", width: 40, align: "center" },
    { type: "seq", width: 60, align: "center" },
    {
      field: "catalogName",
      title: "数据目录名称",
      // 筛选
      filters: [{ data: "" }],
      filterMethod({ option, row, column }) {
        if (option.data) {
          const keys = option.data.split("\n").filter((e) => e);
          return keys.some((key) => `${row[column.field]}`.indexOf(key) > -1);
        }
        return true;
      },
      slots: { default: "catalogName", filter: "inputFilter" },
      minWidth: 200,
    },
    {
      field: "tableName",
      title: "数据表名称",
      minWidth: 200,
      filters: [{ data: "" }],
      filterMethod({ option, row, column }) {
        if (option.data) {
          const keys = option.data.split("\n").filter((e) => e);
          return keys.some((key) => `${row[column.field]}`.indexOf(key) > -1);
        }
        return true;
      },
      slots: { default: "tableName", filter: "inputFilter" },
    },
    { field: "fieldCount", title: "字段数", width: 86, align: "center" },
    {
      field: "businessType",
      title: "业务类型",
      slots: { default: "businessType" },
      filters: tableBusinessTypeOptions.map((item) => ({ label: item.label, value: item.value })),
      width: 90,
    },
    {
      field: "assetStatus",
      title: "状态",
      slots: { default: "assetStatus" },
      filters: useDictStore()
        .getDictItems("status")
        .filter((el) => el.label === "已注册"),
      width: 90,
    },
    {
      field: "action",
      title: "操作",
      slots: { default: "action" },
      fixed: "right",
      align: "center",
      width: 80,
    },
  ],
});
const catalogSegmentOptions = computed(() => [
  { label: `待登记（${tableState.pendingTotal}）`, value: "待登记" },
  { label: `已登记（${tableState.registeredTotal}）`, value: "已登记" },
]);
const catalogColumns = computed(() =>
  (tableState.seg === "已登记" ? tableState.columns2 : tableState.columns1)
    .filter((column) => column.field && column.field !== "action")
    .map((column) => ({
      prop: column.field,
      label: column.title,
      width: column.width,
      minWidth: column.minWidth,
      align: column.field === "fieldCount" ? "right" : ["businessType", "assetStatus"].includes(column.field) ? "center" : "left",
      type: column.field === "fieldCount" ? "num" : undefined,
      filterable: !!column.filters && column.field !== "assetStatus",
      filterOptions: column.field === "businessType" ? tableBusinessTypeOptions : undefined,
    }))
);

const normalizeTableName = (value) => String(value || "").trim().toLowerCase();

const isViewTable = (row) => {
  const rawType = String(row?.tableType || row?.type || row?.tableKind || "")
    .trim()
    .toLowerCase();
  return rawType.includes("view") || rawType.includes("视图");
};
const tableTypeIcon = (row) => (isViewTable(row) ? "view" : "table");
const tableTypeTip = (row) => (isViewTable(row) ? "视图" : "数据表");
const catalogBusinessTypeTag = (value) => ({
  字典表: "warning", 日志表: "danger", 业务表: "success", 过程表: "primary",
  备份表: "info", 不确定: "info", 暂不处理: "info",
}[value] || "info");

const normalizeExploredTable = (row, registeredRow) => {
  const classification = resolveCollectedBusinessType(row, registeredRow || row);
  const physicalType = String(row?.tableType || registeredRow?.tableType || "").toLowerCase();
  const catalogId =
    registeredRow?.sourceCatalogId ||
    registeredRow?.source_catalog_id ||
    registeredRow?.catalogId ||
    row?.sourceCatalogId ||
    row?.catalogId ||
    "";
  return {
    ...row,
    ...registeredRow,
    tableName: row?.tableName || registeredRow?.tableName || registeredRow?.tableNameEn,
    tableNameCn:
      registeredRow?.tableNameCn ||
      registeredRow?.tableComment ||
      row?.tableNameCn ||
      row?.tableComment ||
      row?.tableName,
    tableType:
      physicalType.includes("view") || physicalType.includes("视图") ? "视图" : "数据表",
    fieldCount: Number(
      row?.fieldCount ?? row?.columnCount ?? registeredRow?.fieldCount ?? registeredRow?.columnCount ?? 0
    ),
    businessType: classification.type,
    businessTypeReason: classification.reason,
    tid: registeredRow?.tid || registeredRow?.tableId || row?.tid || row?.tableId || "",
    catalogId,
    sourceCatalogId: catalogId,
    catalogName: registeredRow?.catalogName || row?.catalogName || "",
  };
};

const snapshotRows = (result) =>
  Array.isArray(result) ? result : result?.rows || result?.list || [];

const canCollectCatalogTables = () => {
  const source = tableState.db || {};
  let pool = source.poolCfg ?? source.pool_cfg ?? {};
  if (typeof pool === "string") {
    try { pool = JSON.parse(pool); } catch { pool = {}; }
  }
  const type = String(source.dbType || source.databaseType || source.dataSourceType || pool?.dbType || "").trim().toLowerCase();
  const mode = String(source.accessMode ?? source.access_mode ?? source.dataAccessMode ?? source.data_access_mode ??
    pool?.accessMode ?? pool?.access_mode ?? pool?.dataAccessMode ?? pool?.data_access_mode ?? "").trim().toLowerCase();
  if (["ftp", "sftp"].includes(type)) return true;
  return !isConnectionUnavailable(source) &&
    !["receive", "capture", "push", "pull", "数据推送", "数据拉取"].includes(mode) &&
    !["api", "file", "excel"].includes(type);
};

// 与第二步同一落库快照；每次只读当前页，不全量下载、不连接物理源库。
let catalogPageRequest = 0;
const fetchCatalogPage = async ({ pageNo, pageSize }) => {
  const requestId = ++catalogPageRequest;
  const dbId = tableState.db?.tid;
  const segment = tableState.seg;
  if (!dbId || !tableState.open) return { list: [], total: 0 };
  tableState.loading = true;
  tableState.error = "";
  // 勾选仅作用于当前页；翻页、筛选或切换状态不会携带隐藏的旧勾选。
  tableState.selection = [];
  gridRef.value?.$table?.clearSelection?.();
  try {
    const result = await $common.get("/dst/database/metadata/tables", {
      dbId, catalogPaged: 1, pageNo, pageSize, includeGovernance: false,
      catalogStatus: segment === "已登记" ? "registered" : "pending",
      tableNames: tableState.filters.tableName || "",
      catalogNames: tableState.filters.catalogName || "",
      businessType: tableState.filters.businessType || "",
      checkCollectionMissing: canCollectCatalogTables() ? 1 : 0,
    });
    const list = snapshotRows(result).map((row) => normalizeExploredTable(row, row));
    if (requestId === catalogPageRequest && tableState.open && tableState.db?.tid === dbId && tableState.seg === segment) {
      tableState.pendingTotal = Number(result.pendingTotal || 0);
      tableState.registeredTotal = Number(result.registeredTotal || 0);
    }
    return { list, total: Number(result.total || 0) };
  } catch (error) {
    if (requestId === catalogPageRequest) tableState.error = errorText(error, "读取目录登记列表失败，请重试");
    throw error;
  } finally {
    if (requestId === catalogPageRequest) tableState.loading = false;
  }
};

let catalogCollectionTimer = null;
let catalogCollectionSession = 0;
const stopCatalogCollectionPolling = () => {
  catalogCollectionSession += 1;
  if (catalogCollectionTimer) clearTimeout(catalogCollectionTimer);
  catalogCollectionTimer = null;
  tableState.collecting = false;
};
watch(() => tableState.open, (open) => {
  if (!open) {
    stopCatalogCollectionPolling();
    catalogPageRequest += 1;
    tableState.loading = false;
    tableState.selection = [];
  }
});
onBeforeUnmount(stopCatalogCollectionPolling);

const pollCatalogCollection = async (dbId, jobId, session) => {
  if (session !== catalogCollectionSession || !tableState.open || tableState.db?.tid !== dbId) return;
  try {
    const result = await $common.get("/dst/database/metadata/tables/collection/status",
      { dbId, jobId: jobId || undefined });
    if (session !== catalogCollectionSession) return;
    tableState.collectionProgress = Number(result.progressPercent || 0);
    if (result.status === "RUNNING") {
      tableState.collecting = true;
      catalogCollectionTimer = setTimeout(() => pollCatalogCollection(dbId, result.jobId, session), 1500);
      return;
    }
    tableState.collecting = false;
    if (result.status === "FAILED") throw new Error(result.error || "数据表采集失败，请检查连接信息后重试");
    if (result.status === "SUCCEEDED") {
      await loadTables(dbId, { silent: true });
      if (session === catalogCollectionSession && !tableState.error) {
        $message.success(`数据表采集完成，新增 ${result.addedCount || 0} 张，已存在 ${result.unchangedCount || 0} 张。`);
      }
    }
  } catch (error) {
    if (session !== catalogCollectionSession) return;
    tableState.collecting = false;
    tableState.error = errorText(error, "读取采集状态失败，请重新点击采集以恢复状态查询");
    $message.error(tableState.error);
  }
};

const refreshCollectedTables = async () => {
  const dbId = tableState.db?.tid;
  if (!dbId || tableState.loading || tableState.collecting) return;
  if (!canCollectCatalogTables()) {
    await loadTables(dbId, { silent: true });
    return;
  }
  stopCatalogCollectionPolling();
  const session = catalogCollectionSession;
  tableState.collecting = true;
  tableState.collectionProgress = 0;
  tableState.error = "";
  try {
    const result = await $common.post("/dst/database/metadata/tables/collection/start",
      { dbId, mode: "REFRESH" });
    if (session !== catalogCollectionSession) return;
    $message.info("已提交数据表采集任务，完成后将自动刷新目录列表。");
    await pollCatalogCollection(dbId, result.jobId, session);
  } catch (error) {
    if (session !== catalogCollectionSession) return;
    tableState.collecting = false;
    tableState.error = errorText(error, "无法启动数据表采集任务");
    if (!error?.handled) $message.error(tableState.error);
  }
};

const errorText = (error, fallback) =>
  error?.response?.data?.msg ||
  error?.response?.data?.message ||
  error?.msg ||
  error?.message ||
  fallback;

// 采集仅由刷新按钮显式触发；打开和完成登记后只刷新分页查询。
const loadTables = async (dbId, options = {}) => {
  if (!dbId || tableState.db?.tid !== dbId) return;
  tableState.error = "";
  if (!options.silent) {
    tableState.seg = "待登记";
    tableState.filters = {};
  }
  tableState.selection = [];
  await nextTick();
  gridRef.value?.refresh(true);
  // 重新打开抽屉可继续观察已存在的后台任务，但不能重复启动采集。
  if (canCollectCatalogTables() && !tableState.collecting && tableState.open) {
    try {
      const status = await $common.get("/dst/database/metadata/tables/collection/status", { dbId });
      if (status.status === "RUNNING") {
        tableState.collecting = true;
        pollCatalogCollection(dbId, status.jobId, catalogCollectionSession);
      } else if (status.status === "NOT_COLLECTED" && !tableState.pendingTotal && !tableState.registeredTotal && !tableState.loading) {
        tableState.error = "尚未采集数据表，请点击刷新按钮启动与登记第二步相同的采集任务。";
      }
    } catch (error) {
      tableState.error = errorText(error, "读取采集状态失败");
    }
  }
};
// 切换seg
const changeSeg = (val) => {
  tableState.seg = val;
  tableState.filters = {};
  tableState.selection = [];
  nextTick(() => {
    gridRef.value?.refresh(true);
  });
};
const changeCatalogFilters = (filters) => {
  tableState.filters = { ...filters };
  tableState.selection = [];
  gridRef.value?.refresh(true);
};
// 登记空目录，批量表登记目录、批量编辑目录
const ensureTableAssets = async (tables) => {
  const missing = (tables || []).filter((row) => !row.tid && !row.tableId);
  if (!missing.length) return tables;

  const result = await $common.post("/dst/database/table/batchAnnotate", {
    dbId: tableState.db.tid,
    datasourceId: tableState.db.tid,
    annotated: 0,
    tables: missing.map((row) => ({
      propList: {
        datasourceId: tableState.db.tid,
        dbId: tableState.db.tid,
        tableName: row.tableName,
        tableNameEn: row.tableName,
        tableNameCn: row.tableNameCn || row.tableComment || row.tableName,
        tableComment: row.tableComment || row.tableNameCn || row.tableName,
        tableType: row.tableType || "数据表",
        businessType: row.businessType || "业务表",
        recordCount: row.recordCount,
        fieldCount: row.fieldCount,
        storageSize: row.storageSize || row.totalSizeFormatted,
        totalSizeFormatted: row.totalSizeFormatted || row.storageSize,
        totalSizeBytes: row.totalSizeBytes ?? row.totalSizeBytes_num,
        orgId: tableState.db.orgId,
        orgPath: tableState.db.orgPath,
        appId: tableState.db.appId,
      },
    })),
  });
  const savedRows = Array.isArray(result) ? result : result?.rows || result?.list || [];
  const savedMap = new Map(
    savedRows.map((row) => [
      normalizeTableName(row.tableName || row.tableNameEn),
      row.tid || row.id,
    ])
  );
  missing.forEach((row) => {
    row.tid = savedMap.get(normalizeTableName(row.tableName)) || row.tid;
    row.tableId = row.tid;
  });
  const unresolved = missing.find((row) => !row.tid);
  if (unresolved) {
    throw new Error(`${unresolved.tableName} 未能生成数据表资产，无法登记目录`);
  }
  return tables;
};

const registerCatalog = async (tables, catalogs) => {
  if (!catalogs?.length && !tables?.length) {
    $message.warning("请选择至少一张待登记数据表");
    return;
  }
  if (catalogs?.length) {
    // 批量编辑目录
    openRegisterModal({
      registerClass: "catalog",
      registerData: {
        catalog: catalogs.map((el) => ({
          did: el.did || el.catalogId || $common.uuid(),
          tid: el.catalogId,
          catalogName: el.catalogName,
        })),
        table: catalogs,
      },
    });
  }
  if (tables?.length) {
    tableState.preparing = true;
    try {
      const sourceTables = tables.map((el) => {
        const tableName = el.tableName || el.name || "";
        const tableNameCn = el.tableNameCn || el.tableComment || tableName;
        return {
          ...el,
          tableName,
          tableNameCn,
        };
      });
      await ensureTableAssets(sourceTables);
      // 物理探查行先补齐 db_table_t 主键，再进入目录登记，确保目录与来源表关系完整。
      openRegisterModal({
        registerClass: "catalog",
        registerData: {
          catalog: sourceTables.map((el) => ({
            did: el.did || el.catalogId || $common.uuid(),
            tid: el.catalogId,
            catalogName: `${String(el.tableNameCn || el.tableName).replace("表", "目录")}(${el.tableName})`,
            catalogNameEn: `${el.tableName}`,
            assetDesc: `${String(el.tableNameCn || el.tableName).replace("表", "目录")}(${el.tableName})`,
            sourceTableId: el.tid,
            dbId: tableState.db.tid,
            appId: tableState.db.appId,
            businessType: el.businessType,
          })),
          table: sourceTables,
        },
      });
    } catch (error) {
      const message = errorText(error, "准备数据表资产失败");
      console.error("登记数据目录前置处理失败:", error);
      $message.error(message);
    } finally {
      tableState.preparing = false;
    }
  }
};
const openDetail = (row) => {
  tableState.openDetail = true;
  tableState.catalogId = row.catalogId;
};
</script>

<style scoped lang="scss">
/*抽屉详情*/

:deep(.el-drawer) .detail-container__content {
  margin-top: 12px;

  & > div {
    padding: 0 0 0 8px;
  }
}

:deep(.catalog-register-drawer) {
  min-width: 720px;
}

:deep(.catalog-register-drawer .el-drawer__header) {
  box-sizing: border-box;
  height: 56px;
  min-height: 56px;
  margin-bottom: 0;
  padding: 0 20px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

:deep(.catalog-register-drawer .el-drawer__body) {
  min-height: 0;
  padding: 0;
  overflow: hidden;
}

.catalog-drawer-header {
  min-width: 0;
  height: 56px;
  padding-right: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.catalog-drawer-tabs {
  height: 56px;
  flex: 0 0 auto;

  :deep(.el-tabs__header) { height: 100%; margin: 0; }
  :deep(.el-tabs__nav-wrap),
  :deep(.el-tabs__nav-scroll),
  :deep(.el-tabs__nav),
  :deep(.el-tabs__item) { height: 100%; }
  :deep(.el-tabs__item) { line-height: 56px; }
  :deep(.el-tabs__active-bar) { height: 2px; bottom: 0; z-index: 2; }
  :deep(.el-tabs__nav-wrap::after) { display: none; }
  :deep(.el-tabs__content) { display: none; }
}

.catalog-drawer-context {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #667085;
  font-size: 13px;
  line-height: 20px;
}

.catalog-drawer-body {
  box-sizing: border-box;
  height: 100%;
  min-height: 0;
  padding: 12px 20px 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: #f8fafc;
}

.catalog-drawer-body.has-error {
  min-height: 0;
}

.catalog-drawer-actions {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 8px;
  :deep(.el-button + .el-button) { margin-left: 0; }
}
.catalog-selection-tip {
  color: #667085;
  font-size: 12px;
  white-space: nowrap;
}

.catalog-table-shell {
  display: flex;
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  border: 1px solid #dfe3e8;
  background: #ffffff;
}
.catalog-paged-table {
  min-height: 0;
  min-width: 0;
  container-type: inline-size;
}
.catalog-paged-table :deep(.pagination) {
  box-sizing: border-box;
  margin-top: 4px;
  padding: 4px 12px 10px;
  min-width: 0;
  gap: 8px 12px;
}
@container (max-width: 680px) {
  .catalog-paged-table :deep(.pagination > span:first-child),
  .catalog-paged-table :deep(.el-pagination__jump) { display: none; }
}
@container (max-width: 480px) {
  .catalog-paged-table :deep(.el-pagination__sizes) { display: none; }
}

.catalog-table-name {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.catalog-table-type-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: 1px solid #cfe1ff;
  border-radius: 4px;
  color: #1677ff;
  background: #edf5ff;
  flex: 0 0 24px;
}

.catalog-table-type-icon.is-view {
  border-color: #ccebdd;
  color: #16875d;
  background: #eefaf5;
}

.connection-unavailable {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #909399;
  font-size: 13px;
}

.connection-unavailable :deep(.icon) {
  color: #a8abb2;
}
.table-count-text {
  color: #1d2939;
  font-weight: 500;
}

.table-count-text.is-empty {
  color: #98a2b3;
  font-weight: 400;
}

.left-panel {
  height: 100%;
  min-width: 0;
  display: flex;
  flex-direction: column;

  .panel-header {
    display: flex;
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
  container-type: inline-size;
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
  flex: 0 1 clamp(120px, 18cqw, 160px);
  width: clamp(120px, 18cqw, 160px);
}

.filter-select--status {
  flex-basis: clamp(108px, 16cqw, 144px);
  width: clamp(108px, 16cqw, 144px);
}

.db-type-option {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.db-type-option__icon {
  flex: 0 0 auto;
  color: #1683ff;
  font-size: 16px;
}

.keyword-input {
  flex: 0 1 clamp(210px, 32cqw, 360px);
  width: clamp(210px, 32cqw, 360px);
  max-width: 360px;
  min-width: 210px;
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

@media (max-width: 840px) {
  :deep(.catalog-register-drawer) {
    min-width: 100%;
  }

  .catalog-drawer-context,
  .catalog-selection-tip { display: none; }
}

</style>
