<template>
  <div class="data-convergence card-container datasource-explorer-page flex flex-col px-5 pt-5 pb-2 w-full h-full">
    <el-splitter class="datasource-explorer-splitter">
      <el-splitter-panel size="300px" min="10%">
        <div class="left-panel">
          <div class="panel-header">
            <h3>数据任务</h3>
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
              search-placeholder="请输入组织/应用系统/数据库"
              class="tree-container-custom"
              @node-click="handleNodeClick"
            >
              <template #node="{ node }">
                <u-tree-node
                  show-icon
                  style="--gap: 0; --node-count-color: #323643"
                  :keyword="treeRef?.searchValue"
                  :label="node.label"
                >
                  <template #count>
                    <span v-if="getTreeNodeCount(node.data) > 0">({{ getTreeNodeCount(node.data) }})</span>
                    <span v-else></span>
                  </template>

                  <template #icon>
                    <el-tooltip
                      v-if="node?.data?.type === 'datasource'"
                      effect="dark"
                      placement="top"
                      :content="getDatasourceIconTip(node.data)"
                    >
                      <span class="tree-db-icon-tip">
                        <Icon
                          :icon="treeNodeIcon(node)"
                          :color="treeNodeColor(node)"
                          class="mr-1"
                          :size="treeNodeSize(node)"
                        />
                      </span>
                    </el-tooltip>
                    <Icon
                      v-else
                      :icon="treeNodeIcon(node)"
                      :color="treeNodeColor(node)"
                      class="mr-1"
                      :size="treeNodeSize(node)"
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
          <div class="list-toolbar">
            <div class="list-toolbar__primary">
              <el-segmented v-model="state.accessStatus" :options="tabs" @change="handleSearch" />
            </div>
            <div class="list-filters">
              <el-input
                v-model.trim="state.keyword"
                class="keyword-input"
                placeholder="请输入表名/表注释"
                clearable
                :disabled="!state.leftSelectNode"
                @clear="handleSearch"
                @keydown.enter="handleSearch"
              >
                <template #suffix>
                  <span
                    class="keyword-search-trigger"
                    role="button"
                    tabindex="0"
                    title="查询"
                    @mousedown.prevent
                    @click.stop="handleSearch"
                    @keydown.enter.prevent="handleSearch"
                  >
                    <Icon icon="search" />
                  </span>
                </template>
              </el-input>
            </div>
          </div>

          <data-table
            ref="tableRef"
            :columns="[]"
            :data="fetchSources"
            :immediate="false"
            row-key="tid"
            stripe
            :show-search="false"
            :show-reset="false"
            flex-type="flex-[1_1_200px]"
            class="source-table"
            :row-class-name="sourceRowClassName"
            :default-sort="{ prop: 'createdTime', order: 'descending' }"
            @expand-change="handleExpandChange"
            @sort-change="handleSourceSortChange"
          >
            <el-table-column type="expand" width="44">
              <template #default="{ row }">
                <div class="task-table-wrap">
                  <div class="child-table-head">
                    <div>
                      <span
                        class="table-name-cn"
                        :title="row.tableNameCn || row.tableComment || row.tableName"
                      >
                        {{ row.tableNameCn || row.tableComment || row.tableName }}
                      </span>
                    <span>{{ row.taskCount || 0 }} 个接入任务</span>
                    </div>
                    <el-tag size="small" effect="plain" :type="row.taskCount > 0 ? 'success' : 'warning'">
                      {{ row.accessStatusText }}
                    </el-tag>
                  </div>

                  <el-table
                    v-if="hasChildTasks(row)"
                    :data="row.tasks"
                    size="small"
                    border
                    class="task-table"
                  >
                    <el-table-column type="index" label="序号" width="62" />
                    <el-table-column prop="taskName" label="任务名称" min-width="190" show-overflow-tooltip>
                      <template #default="{ row: task }">
                        <div class="task-name-cell">
                          <Icon icon="menu-monitor" />
                          <span>{{ task.taskName }}</span>
                        </div>
                      </template>
                    </el-table-column>
                    <el-table-column prop="targetTableName" label="目标表名" min-width="160" show-overflow-tooltip>
                      <template #default="{ row: task }">
                        <span v-if="task.targetTableName">{{ task.targetTableName }}</span>
                        <span v-else class="muted">-</span>
                      </template>
                    </el-table-column>
                    <el-table-column prop="scheduleCycleText" label="调度周期" width="110" />
                    <el-table-column prop="lastRunning" label="上次运行结束时间" width="168">
                      <template #default="{ row: task }">{{ formatDateTime(task.lastRunning) }}</template>
                    </el-table-column>
                    <el-table-column prop="taskStatusText" label="执行状态" width="100" align="center">
                      <template #default="{ row: task }">
                        <el-tag :type="taskStatusType(task)" size="small" effect="light">
                          {{ task.taskStatusText }}
                        </el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column label="操作" fixed="right" width="104" align="center" header-align="center">
                      <template #default="{ row: task }">
                        <el-dropdown @command="(command) => handleTaskMenu(command, task, row)">
                          <el-button link type="primary">查看任务</el-button>
                          <template #dropdown>
                            <el-dropdown-menu>
                              <el-dropdown-item command="setting">查看任务</el-dropdown-item>
                              <el-dropdown-item command="tables">删除表</el-dropdown-item>
                            </el-dropdown-menu>
                          </template>
                        </el-dropdown>
                      </template>
                    </el-table-column>
                  </el-table>

                  <el-empty
                    v-else
                    description="暂无为该数据源创建接入任务"
                    :image-size="72"
                  />
                </div>
              </template>
            </el-table-column>

            <el-table-column prop="tableName" label="数据表名" min-width="230">
              <template #default="{ row }">
                <div class="table-name-cell" @click="openTableDetail(row)">
                  <Icon :icon="tableTypeIcon(row)" />
                  <div>
                    <span
                      class="table-name-cn"
                      :title="row.tableNameCn || row.tableComment || row.tableName"
                    >
                      {{ row.tableNameCn || row.tableComment || row.tableName }}
                    </span>
                    <small>{{ row.tableName }}</small>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="taskCount" label="接入任务" width="100" align="center">
              <template #default="{ row }">
                <span v-if="Number(row.taskCount) > 0">{{ row.taskCount || 0 }} 个</span>
                <span v-else class="task-empty">未接入</span>
              </template>
            </el-table-column>
            <el-table-column prop="datasourceName" label="所属数据源" width="185">
              <template #default="{ row }">
                <div class="source-name-cell is-link" @click="openDatasourceDetail(row)">
                  <span
                    class="source-name-cn"
                    :title="row.datasourceName || row.dbName || '-'"
                  >
                    {{ row.datasourceName || row.dbName || "-" }}
                  </span>
                  <small>{{ row.appName || row.applicationName || row.systemName || "-" }}</small>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="businessType" label="业务类型" width="110" align="center">
              <template #default="{ row }">
                <el-tag
                  size="small"
                  effect="light"
                  :class="['business-type-tag', businessTypeClass(row.businessType)]"
                >
                  {{ row.businessType || "-" }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdTime" label="创建时间" width="168" sortable="custom">
              <template #default="{ row }">{{ formatDateTime(row.createdTime) }}</template>
            </el-table-column>
            <el-table-column prop="accessStatusText" label="状态" width="112" align="center">
              <template #default="{ row }">
                <el-tag v-if="Number(row.taskCount) > 0" type="success" size="small">已接入</el-tag>
                <el-button v-else link type="warning" class="reject-modify-action" :disabled="!getSourceTableId(row)" @click="handleRejectForModify(row)">
                  驳回修改
                </el-button>
              </template>
            </el-table-column>
            <el-table-column label="操作" fixed="right" width="104" align="center" header-align="center">
              <template #default="{ row }">
                <el-button v-if="!row.taskCount" link type="primary" @click="handleTableAction(row, 'createTable')">创建任务</el-button>
                <template v-if="Number(row.taskCount) > 0 && row.tasks?.length">
                  <el-dropdown @command="(command) => handleTaskMenu(command, row.tasks[0], row)">
                    <el-button link type="primary">查看任务</el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="setting">查看任务</el-dropdown-item>
                        <el-dropdown-item command="tables">删除表</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </template>
                <el-button v-else-if="hasTargetTables(row)" link type="primary" @click="openTargetDrawer(row)">
                  查看建表情况
                </el-button>
              </template>
            </el-table-column>
          </data-table>

        </div>
      </el-splitter-panel>
    </el-splitter>

    <access-modal
      v-if="accessVisible"
      :id="id"
      v-model="accessVisible"
      :access-task-id="accessTaskId"
      @close="closeRegisterModal"
    />
    <access-apply
      v-if="applyVisible"
      :id="id"
      v-model="applyVisible"
      :type="applyType"
      default-target-domain-code="yuanshi"
      @save="handleApplySave"
    />
    <schedule-setting
      v-if="scheduleVisible"
      :key="accessTaskId"
      :id="id"
      :access-task-id="accessTaskId"
      @close="closeNifiDesigner"
    />
    <el-drawer
      v-model="targetDrawerVisible"
      title="建表情况"
      direction="rtl"
      size="680px"
      destroy-on-close
      :close-on-click-modal="!deletingAllTargets"
      :close-on-press-escape="!deletingAllTargets"
      :show-close="!deletingAllTargets"
    >
      <div
        v-loading="deletingAllTargets"
        :element-loading-text="deleteAllLoadingText"
        class="target-drawer-content"
      >
        <template v-if="targetDrawerContext">
        <el-descriptions :column="1" border class="mb-4">
          <el-descriptions-item label="来源表">
            {{ targetDrawerContext.tableNameCn || targetDrawerContext.tableComment || targetDrawerContext.tableName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="接入任务">
            {{ targetDrawerTask?.taskName || '尚未创建接入任务' }}
          </el-descriptions-item>
        </el-descriptions>
        <el-table :data="targetDrawerTables" border size="small" empty-text="暂未创建目标表">
          <el-table-column type="index" label="序号" width="64" />
          <el-table-column prop="targetDbName" label="目标数据源" min-width="160" show-overflow-tooltip>
            <template #default="{ row: target }">
              {{ target.targetDbName || target.datasourceName || target.targetDbId || '-' }}
              <small v-if="target.targetDbType" class="target-db-type">{{ target.targetDbType }}</small>
            </template>
          </el-table-column>
          <el-table-column prop="targetTableName" label="目标表" min-width="170" show-overflow-tooltip />
          <el-table-column label="状态" width="94" align="center">
            <template #default="{ row: target }">
              <el-tag size="small" type="success">已创建</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="112" align="center">
            <template #default="{ row: target }">
              <el-button link type="danger" :disabled="deletingAllTargets" @click="deleteSingleTarget(target)">删除目标表</el-button>
            </template>
          </el-table-column>
        </el-table>
        </template>
      </div>
      <template #footer>
        <div class="target-drawer-footer">
          <el-button
            v-if="targetDrawerTask?.tid"
            type="danger"
            plain
            :disabled="deletingAllTargets"
            @click="deleteCurrentFlow"
          >
            删除流程
          </el-button>
          <el-button
            v-if="targetDrawerTables.length"
            type="danger"
            :loading="deletingAllTargets"
            :disabled="deletingAllTargets"
            @click="deleteAllTargets"
          >
            删除全部目标表和流程
          </el-button>
          <el-button :disabled="deletingAllTargets" @click="targetDrawerVisible = false">关闭</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ElMessageBox } from "element-plus";
import { computed, nextTick, onActivated, onMounted, onUnmounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { get } from "lodash-es";

const router = useRouter();
const treeRef = ref();
const tableRef = ref();
const skipFirstActivation = ref(true);
const orgTreeSource = ref([]);
const expandedRows = ref([]);

const applyVisible = ref(false);
const accessVisible = ref(false);
const scheduleVisible = ref(false);
const id = ref("");
const accessTaskId = ref("");
const applyType = ref();
const pendingTaskRow = ref(null);
const targetDrawerVisible = ref(false);
const targetDrawerContext = ref(null);
const targetDrawerTask = ref(null);
const deletingAllTargets = ref(false);
const deleteAllTargetProgress = ref(0);
const deleteAllTargetCount = ref(0);
const deleteAllLoadingText = computed(() => {
  if (!deletingAllTargets.value) return "";
  if (!deleteAllTargetCount.value) return "正在准备删除目标表和流程...";
  return `正在删除第 ${Math.max(1, deleteAllTargetProgress.value)}/${deleteAllTargetCount.value} 个目标表和流程...`;
});

const tabs = [
  { label: "全部", value: "all" },
  { label: "待接入", value: "0" },
  { label: "已接入", value: "1" },
];

const treeData = ref([
  {
    name: "全部",
    label: "全部",
    value: "",
    serialNumber: $user.orgRootSerialNumber,
    children: [],
  },
]);

const treeStatistics = ref({
  total: 0,
  orgCounts: {},
  orgApps: {},
});
const countMap = ref({});

const state = reactive({
  loading: false,
  leftLoading: false,
  keyword: "",
  accessStatus: "all",
  leftSelectNode: treeData.value[0],
  sortField: "createdTime",
  sortDir: "desc",
});

const getTreeNodeCount = (node) => {
  if (!node) return 0;
  if (node.type === "datasource") return Number(node.count || 0);
  if (node.type === "app") return Number(node.count || 0);
  if (!node.value) return Number(treeStatistics.value.total || 0);
  const key = `${node.type || "org"}.${node.value}`;
  const ownCount = Number(get(countMap.value, key, 0)) || 0;
  const orgChildren = (node.children || []).filter((child) => child.type !== "app");
  if (orgChildren.length === 0) return ownCount;
  return ownCount + orgChildren.reduce((total, child) => total + getTreeNodeCount(child), 0);
};

const treeNodeIcon = (node) => {
  if (node?.data?.type === "datasource") {
    const aliases = {
      pg: "postgresql",
      postgres: "postgresql",
      mssql: "sqlserver",
      dm: "dameng",
      kingbase: "kingbase8",
      kingbasees: "kingbase8",
    };
    const supported = new Set([
      "mysql", "oracle", "oceanbasemysql", "oceanbaseoracle", "gaussdb",
      "gbase8a", "sqlserver", "hive", "maxcompute", "vertica", "dameng",
      "postgresql", "kingbase8", "minio", "ftp", "api", "kafka",
    ]);
    const rawType = String(node?.data?.dbType || "").toLowerCase().replace(/[_\-\s]/g, "");
    const icon = aliases[rawType] || rawType;
    return supported.has(icon) ? icon : "db";
  }
  if (node?.data?.type === "app") return "app";
  return node?.expanded ? "folder-open" : "folder";
};

const treeNodeColor = (node) => {
  if (node?.data?.type === "datasource") return "#0ea5e9";
  return node?.data?.type === "app" ? "#7a5af8" : "#0b8bf9";
};

const treeNodeSize = (node) =>
  ["app", "datasource"].includes(node?.data?.type) ? "19" : "23";

const formatDbTypeName = (value) => {
  const raw = String(value || "").trim();
  const key = raw.toLowerCase().replace(/[_\-\s]/g, "");
  const map = {
    mysql: "MySQL",
    oracle: "Oracle",
    oceanbasemysql: "OceanBase MySQL",
    oceanbaseoracle: "OceanBase Oracle",
    gaussdb: "GaussDB",
    gbase8a: "GBase 8a",
    sqlserver: "SQL Server",
    mssql: "SQL Server",
    hive: "Hive",
    maxcompute: "MaxCompute",
    vertica: "Vertica",
    dameng: "达梦",
    dm: "达梦",
    postgresql: "PostgreSQL",
    postgres: "PostgreSQL",
    pg: "PostgreSQL",
    kingbase: "人大金仓",
    kingbase8: "人大金仓",
    kingbasees: "人大金仓",
    minio: "MinIO",
    ftp: "FTP",
    api: "API",
    kafka: "Kafka",
    other: "其他类型",
  };
  return map[key] || raw || "未知类型";
};

const getConnectionText = (data) => {
  const showConnect = String(data?.showConnect ?? data?.show_connect ?? "").toLowerCase();
  if (["0", "false", "no"].includes(showConnect)) {
    return "暂不提供连接信息";
  }
  const status = String(data?.connectionStatus ?? data?.connection_status ?? "").toLowerCase();
  if (["1", "true", "success", "connected", "ok", "normal"].includes(status)) {
    return "已提供连接信息，连接正常";
  }
  if (["0", "false", "fail", "failed", "error", "exception", "abnormal"].includes(status)) {
    return "已提供连接信息，连接异常";
  }
  return showConnect ? "已提供连接信息，连通状态未知" : "连接信息未同步";
};

const getDatasourceIconTip = (data) =>
  `${formatDbTypeName(data?.dbType)} · ${getConnectionText(data)}`;

const hasChildTasks = (row) => Array.isArray(row?.tasks) && row.tasks.length > 0;

// 目标表以来源表 source_table_id 为主关联。兼容尚未创建任务的已物化表，
// 也兼容历史任务中仅保存一个 targetTableId 的记录。
const targetTablesOf = (row, task = null) => {
  const candidates = [
    ...(Array.isArray(row?.targetTables) ? row.targetTables : []),
    ...(Array.isArray(task?.targetTables) ? task.targetTables : []),
  ];
  if (task?.targetTableId || row?.targetTableId || row?.materializedTableId) {
    candidates.push({
      targetTableId: task?.targetTableId || row?.targetTableId || row?.materializedTableId,
      targetTableName: task?.targetTableName || row?.targetTableName || '',
      targetDbId: task?.targetDbId || row?.targetDbId || '',
      targetDbName: task?.targetDbName || row?.targetDbName || '',
      targetDbType: task?.targetDbType || row?.targetDbType || '',
    });
  }
  const seen = new Set();
  return candidates.filter((item) => {
    const targetTableId = item?.targetTableId || item?.tid || item?.tableId;
    if (!targetTableId || seen.has(targetTableId)) return false;
    seen.add(targetTableId);
    return true;
  }).map((item) => ({
    ...item,
    targetTableId: item.targetTableId || item.tid || item.tableId,
    targetTableName: item.targetTableName || item.tableName || item.tableNameCn || '',
  }));
};

const hasTargetTables = (row) => targetTablesOf(row).length > 0;
const targetDrawerTables = computed(() => targetTablesOf(targetDrawerContext.value, targetDrawerTask.value));

const openTargetDrawer = (row, task = null) => {
  targetDrawerContext.value = row || null;
  targetDrawerTask.value = task || row?.tasks?.[0] || null;
  targetDrawerVisible.value = true;
};

const handleTaskMenu = (command, task, row) => {
  if (command === "tables") {
    openTargetDrawer(row, task);
    return;
  }
  if (command === "delete") {
    handleTaskRecovery(task, row, "flow");
    return;
  }
  openTaskSetting(task, row);
};

const appendApplicationNodes = (nodes) =>
  (nodes || []).map((node) => {
    const orgChildren = appendApplicationNodes(node.children || []);
    const appChildren =
      (treeStatistics.value.orgApps?.[node.value] || []).map((app) => ({
            ...app,
            type: "app",
            name: app.label,
            serialNumber: app.value,
            children: (app.datasources || app.children || []).map((datasource) => ({
              ...datasource,
              type: "datasource",
              name: datasource.label,
              serialNumber: datasource.value,
              children: [],
            })),
          }));
    return {
      ...node,
      type: "org",
      children: [...orgChildren, ...appChildren],
    };
  });

const applyDefaultTreeState = () => {
  nextTick(() => {
    nextTick(() => {
      const tree = treeRef.value?.getTree?.();
      if (!tree) return;
      // 与数据源管理页保持一致：首屏仅展开“全部 → 第一层机构 → 内设机构”，
      // 其余部门按需展开，避免树在大组织下占满首屏。
      const initialPath = [];
      let current = treeData.value[0];
      while (current && initialPath.length < 3) {
        initialPath.push(current.value);
        current = (current.children || []).find((item) => item.type === "org");
      }
      const expandInitialPath = (nodes) => {
        (nodes || []).forEach((item) => {
          const treeNode = tree.getNode(item.value);
          if (treeNode) treeNode.expanded = initialPath.includes(item.value);
          expandInitialPath(item.children || []);
        });
      };
      expandInitialPath(treeData.value);
      tree.setCurrentNode?.(treeData.value[0]);
    });
  });
};

const rebuildTree = () => {
  treeData.value[0].children = appendApplicationNodes(orgTreeSource.value);
  applyDefaultTreeState();
};

const statisticsPayload = () => ({
  keyword: "",
  dbType: "",
  orgIds: $user.isAdmin ? [] : [$user.orgId].filter(Boolean),
});

const loadTree = async () => {
  state.leftLoading = true;
  try {
    const [orgTree, statistics] = await Promise.all([
      $common.post("/sym/org/getOrgTree", { parentId: $user.orgRootId }),
      $common.post("/ods/hiveTargetTree", statisticsPayload()),
    ]);
    orgTreeSource.value = orgTree || [];
    treeStatistics.value = {
      total: Number(statistics?.total || 0),
      orgCounts: statistics?.orgCounts || {},
      orgApps: statistics?.orgApps || {},
    };
    countMap.value = { org: treeStatistics.value.orgCounts };
    rebuildTree();
    state.leftSelectNode = treeData.value[0];
  } catch (error) {
    console.error("加载数据源树失败", error);
    $message.error("数据源树加载失败，请重新刷新！");
  } finally {
    state.leftLoading = false;
  }
};

const buildConditions = () => {
  return [{ field: "assetType", value: "table", type: "match" }];
};

const collectDatasourceIds = (node) => {
  if (!node) return [];
  if (node.type === "datasource") return [node.value].filter(Boolean);
  return (node.children || []).flatMap((child) => collectDatasourceIds(child));
};

const fetchSources = async ({ pageNo = 1, pageSize = 20 } = {}) => {
  const selectedNode = state.leftSelectNode || treeData.value[0];
  const datasourceIds = [...new Set(collectDatasourceIds(selectedNode))];
  state.loading = true;
  try {
    const result = await $common.post("/ods/hiveTargetPage", {
      scopeType: selectedNode.type || "all",
      scopeId: selectedNode.value || "",
      datasourceId: selectedNode.type === "datasource" ? selectedNode.value : "",
      datasourceIds,
      pageNum: pageNo,
      pageSize,
      sortField: state.sortField,
      sortDir: state.sortDir,
      keyword: state.keyword,
      accessStatus: state.accessStatus,
      conditions: buildConditions(),
    });
    const page = result?.list ? result : result?.data || {};
    const list = (page.list || []).map((item) => ({
      ...item,
      tasks: item.tasks || [],
      taskCount: Number(item.taskCount || 0),
      accessStatusText: item.accessStatusText || "",
    }));
    return { list, total: Number(page.total || 0) };
  } catch (error) {
    console.error("加载Hive目标表失败", error);
    $message.error("Hive目标表列表加载失败，请稍后重试");
    return { list: [], total: 0 };
  } finally {
    state.loading = false;
  }
};

// Hive 阶段以 OB 阶段已经物化出的表为新的来源表，不能回退到 OB 的来源表。
const getSourceTableId = (row) => row?.tid || row?.tableId || "";

const handleSearch = () => {
  tableRef.value?.refresh?.(true);
};

const handleSourceSortChange = ({ prop, order }) => {
  if (prop !== "createdTime") return;
  if (!order) {
    state.sortField = "createdTime";
    state.sortDir = "desc";
  } else {
    state.sortField = prop;
    state.sortDir = order === "ascending" ? "asc" : "desc";
  }
  tableRef.value?.refresh?.(true);
};

const handleNodeClick = (node) => {
  state.leftSelectNode = node || null;
  expandedRows.value = [];
  state.keyword = "";
  state.accessStatus = "all";
  handleSearch();
};

const handleExpandChange = (_row, rows) => {
  expandedRows.value = rows.map((item) => item.tid);
};

const sourceRowClassName = ({ row }) =>
  expandedRows.value.includes(row.tid) ? "is-expanded-source" : "";

const formatDateTime = (value) => {
  if (value == null || value === "") return "-";
  const numberValue = Number(value);
  const date = Number.isFinite(numberValue) && numberValue > 10000000000
    ? new Date(numberValue)
    : new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  const pad = (num) => String(num).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
};

const openTableDetail = (row) => {
  const tableId = row?.tid || row?.sourceTableId || row?.tableId;
  if (!tableId) {
    $message.warning("无法读取表标识，请选择有效的数据表");
    return;
  }
  router.push({
    path: "/jr/hive-metastore/detail",
    query: {
      id: tableId,
      type: "table",
      title: row.tableNameCn || row.tableComment || row.tableName || "数据表",
      from: "/jr/hive-metastore/list",
    },
  });
};


const businessTypeClass = (type) =>
  ({
    业务表: "is-business",
    日志表: "is-log",
    字典表: "is-dict",
    过程表: "is-process",
    备份表: "is-backup",
    不确定: "is-unconfirmed",
  }[type] || "is-default");

const openDatasourceDetail = (row) => {
  const datasourceId = row?.datasourceId || row?.dbId || row?.sourceDbId;
  if (!datasourceId) {
    $message.warning("无法读取数据源标识，请选择有效的数据源");
    return;
  }
  router.push({
    path: "/jr/hive-metastore/detail",
    query: {
      id: datasourceId,
      type: "db",
      title: row.datasourceName || row.dbName || "数据源",
      from: "/jr/hive-metastore/list",
    },
  });
};

const tableTypeIcon = (row) => {
  const type = String(row?.tableType || "").toLowerCase();
  return type.includes("view") || type === "视图" ? "view" : "table";
};

const taskStatusType = (task) => {
  if (task?.taskStatusText === "执行失败") return "danger";
  if (task?.taskStatusText === "执行中") return "success";
  if (task?.taskStatusText === "停止") return "info";
  return "warning";
};

// Opening an existing task must be a read-only action.  Do not call the
// task-ensure endpoint here: it is allowed to create/rebuild a task and would
// replace a user's saved pipeline with a generated default canvas.
const openTaskSetting = (task, row = null) => {
  const sourceTableId =
    task?.sourceTableId ||
    task?.source_table_id ||
    row?.sourceTableId ||
    row?.tid ||
    row?.tableId ||
    "";
  if (!sourceTableId) {
    $message.warning("无法读取来源表标识，暂不能查看任务");
    return;
  }
  const taskId = task?.tid || task?.id || row?.accessTaskId || "";
  if (!taskId) {
    $message.warning("当前记录没有关联接入任务，无法查看任务");
    return;
  }
  id.value = sourceTableId;
  accessTaskId.value = taskId;
  scheduleVisible.value = true;
};

const openNifiDesigner = (taskId, sourceTableId = "") => {
  if (sourceTableId) id.value = sourceTableId;
  accessTaskId.value = taskId || "";
  if (!accessTaskId.value) {
    $message.warning("接入任务创建失败，未返回任务标识");
    return;
  }
  scheduleVisible.value = true;
};

const ensureAccessTask = async (row) => {
  const payload = {
    tableId: getSourceTableId(row),
    // 只传本阶段物化建表返回的目录。不能回退到 OB 阶段遗留目录，
    // 否则历史 targetTableId 会被错误地当作 Hive 阶段目标表。
    catalogId: row?.hiveCatalogId || row?.catalogId || "__hive_stage__",
  };
  if (!payload.tableId) {
    $message.warning("无法读取来源表标识，暂不能创建接入任务");
    return null;
  }
  const result = await $common.post("/ods/dataAggTaskEnsure", payload);
  const task = result?.task || result?.data?.task || result;
  const taskId = task?.tid || result?.taskId || result?.data?.taskId;
  if (!taskId) {
    $message.warning("接入任务创建失败，请稍后重试");
    return null;
  }
  const pipelineId = task?.pipelineId || task?.pipeline_id || result?.pipelineId || result?.data?.pipelineId || "";
  const datasourceId = row?.datasourceId || row?.dbId || row?.sourceDbId || "";
  // 所有来源都可安全调用：后端会对非 API 拉取来源返回 applicable=false。
  // 这里发生在用户明确点击“创建任务”之后，绝不在浏览/打开页面时隐式创建或启动任务。
  if (datasourceId && pipelineId) {
    await $common.post("/ods/api-pull/bind-task", {
      datasourceId,
      sourceTableId: payload.tableId,
      pipelineId,
    });
  }
  return { ...(task || {}), tid: taskId, pipelineId };
};

const handleTaskRecovery = async (task, row, command, target = null) => {
  const sourceTableId =
    task?.sourceTableId ||
    task?.source_table_id ||
    row?.sourceTableId ||
    row?.tid ||
    "";
  if (!sourceTableId) {
    $message.warning("无法读取来源表标识，暂不能执行重建操作");
    return;
  }
  const deleteTargetTable = command === "target";
  const actionText = deleteTargetTable ? "删除目标表并移除流程" : "删除任务";
  try {
    await ElMessageBox.confirm(
      deleteTargetTable
        ? `将删除目标物理表“${target?.targetTableName || target?.tableName || '当前目标表'}”及其元数据，并移除当前接入流程。该操作不可恢复，确认继续吗？`
        : "将删除当前接入任务和关联流程，已创建的目标表及其数据会保留，可通过“查看建表情况”统一管理。确认继续吗？",
      actionText,
      { type: "warning", confirmButtonText: "确认", cancelButtonText: "取消" },
    );
    await $common.post("/ods/dataAggReset", {
      sourceTableId,
      targetTableId: target?.targetTableId || task?.targetTableId || task?.target_table_id || row?.targetTableId || "",
      targetDbId: target?.targetDbId || "",
      targetTableName: target?.targetTableName || target?.tableName || "",
      deleteTargetTable,
    });
    scheduleVisible.value = false;
    accessTaskId.value = "";
    targetDrawerVisible.value = false;
    $message.success(deleteTargetTable ? "已删除目标表并移除流程，可重新创建任务" : "已删除任务和关联流程，可重新创建任务");
    await tableRef.value?.refresh?.();
  } catch (error) {
    if (error === "cancel" || error === "close") return;
    console.error("重建接入任务失败", error);
    $message.error(error?.message || error || "重建操作失败，请稍后重试");
  }
};

const deleteCurrentFlow = () =>
  handleTaskRecovery(targetDrawerTask.value, targetDrawerContext.value, "flow");

const deleteSingleTarget = (target) =>
  handleTaskRecovery(targetDrawerTask.value, targetDrawerContext.value, "target", target);

const deleteAllTargets = async () => {
  if (deletingAllTargets.value) return;
  const targets = [...targetDrawerTables.value];
  if (!targets.length) return;

  try {
    await ElMessageBox.confirm(
      `将删除 ${targets.length} 个目标物理表、对应元数据和当前接入流程。该操作不可恢复，确认继续吗？`,
      "删除全部目标表和流程",
      { type: "warning", confirmButtonText: "确认删除", cancelButtonText: "取消" },
    );
  } catch (error) {
    if (error !== "cancel" && error !== "close") {
      console.error("确认删除全部目标表失败", error);
      $message.error(error?.message || error || "删除确认失败，请稍后重试");
    }
    return;
  }

  deletingAllTargets.value = true;
  deleteAllTargetCount.value = targets.length;
  deleteAllTargetProgress.value = 0;
  try {
    for (let index = 0; index < targets.length; index += 1) {
      const target = targets[index];
      deleteAllTargetProgress.value = index + 1;
      await $common.post("/ods/dataAggReset", {
        sourceTableId: getSourceTableId(targetDrawerContext.value),
        targetTableId: target.targetTableId,
        targetDbId: target.targetDbId || "",
        targetTableName: target.targetTableName || "",
        deleteTargetTable: true,
      });
    }
    targetDrawerVisible.value = false;
    accessTaskId.value = "";
    $message.success("已删除全部目标表和流程，可重新创建任务");
    await tableRef.value?.refresh?.();
  } catch (error) {
    console.error("删除全部目标表失败", error);
    $message.error(error?.message || error || "删除全部目标表失败，请稍后重试");
  } finally {
    deletingAllTargets.value = false;
    deleteAllTargetProgress.value = 0;
    deleteAllTargetCount.value = 0;
  }
};

const handleCreateTask = async (row) => {
  try {
    if (row?.accessTaskId || Number(row?.taskCount || 0) > 0) {
      openTaskSetting(row.tasks?.[0] || { tid: row.accessTaskId }, row);
      return;
    }
    const targetTableId = row?.targetTableId || row?.materializedTableId;
    if (targetTableId) {
      const task = await ensureAccessTask(row);
      if (task?.tid) {
        $message.success("接入任务已创建，正在打开流程画布");
        tableRef.value?.refresh?.();
        openNifiDesigner(task.tid, row?.sourceTableId || row?.tid || row?.tableId || "");
      }
      return;
    }
    const sourceTableId = getSourceTableId(row);
    if (!sourceTableId) {
      $message.warning("无法读取来源表标识，暂不能创建接入任务");
      return;
    }
    pendingTaskRow.value = row;
    id.value = sourceTableId;
    applyType.value = "add";
    applyVisible.value = true;
  } catch (error) {
    console.error("创建接入任务失败", error);
    $message.error(error?.message || error || "创建接入任务失败");
  }
};

const handleRejectForModify = async (row) => {
  const tableId = getSourceTableId(row);
  if (!tableId) {
    $message.warning("无法读取来源表标识，暂不能驳回修改");
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确认驳回“${row.tableNameCn || row.tableComment || row.tableName || "该数据表"}”的登记信息吗？该表会回到未登记状态，数据源仍保持已注册。`,
      "驳回修改",
      { type: "warning" },
    );
    await $common.post("/ods/dataAggReject", {
      tableId,
      datasourceId: row.datasourceId || row.dbId || "",
    });
    $message.success("已驳回，请在数据源登记中重新标注该表");
    await loadTree();
    tableRef.value?.refresh?.();
  } catch (error) {
    if (error === "cancel" || error === "close") return;
    console.error("驳回登记信息失败", error);
    $message.error(error?.message || error || "驳回修改失败，请稍后重试");
  }
};

const handleTableAction = (row, type) => {
  accessTaskId.value = row.accessTaskId || "";
  id.value = getSourceTableId(row) || row.sourceCatalogId || row.catalogId || "";
  switch (type) {
    case "createTable":
      handleCreateTask(row);
      return;
    case "viewTable":
      applyType.value = "view";
      applyVisible.value = true;
      return;
    case "access":
      accessVisible.value = true;
      return;
    case "schedule":
      scheduleVisible.value = true;
      return;
  }
};

const handleApplySave = async (materializedTarget = null) => {
  applyVisible.value = false;
  const row = pendingTaskRow.value;
  pendingTaskRow.value = null;
  if (!row) {
    tableRef.value?.refresh?.();
    return;
  }
  try {
    const targetTableId = materializedTarget?.targetTableId || materializedTarget?.tid || "";
    if (!targetTableId) {
      throw new Error("目标表未创建成功，不能继续创建接入任务");
    }
    const task = await ensureAccessTask({
      ...row,
      targetTableId,
      hiveCatalogId: materializedTarget?.catalogId || materializedTarget?.sourceCatalogId || "",
    });
    if (task?.tid) {
      $message.success("物化建表完成，接入任务已创建");
      tableRef.value?.refresh?.();
      openNifiDesigner(task.tid, row?.sourceTableId || row?.tid || row?.tableId || "");
    }
  } catch (error) {
    console.error("物化后创建接入任务失败", error);
    $message.error(error?.message || error || "物化完成，但自动创建接入任务失败");
    tableRef.value?.refresh?.();
  }
};

const closeRegisterModal = (refresh) => {
  if (refresh) {
    tableRef.value?.refresh?.();
  }
  accessVisible.value = false;
};

const closeNifiDesigner = () => {
  scheduleVisible.value = false;
  tableRef.value?.refresh?.();
};

const handleMessage = (event) => {
  if (event.data?.type === "NIFI_APP_CLOSE") {
    closeNifiDesigner();
  }
};

onMounted(() => {
  // 首屏右表默认查询“全部”，与左树接口同一轮启动。
  tableRef.value?.refresh?.(true);
  loadTree();
  window.addEventListener("message", handleMessage);
});

onActivated(() => {
  if (skipFirstActivation.value) {
    skipFirstActivation.value = false;
    return;
  }
  tableRef.value?.refresh?.();
});

onUnmounted(() => {
  window.removeEventListener("message", handleMessage);
});
</script>

<style scoped lang="scss">
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

.data-convergence {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  min-width: 0;
  overflow: hidden;

  .left-panel {
    width: 100%;
    height: 100%;
    min-width: 0;
    min-height: 0;
    display: flex;
    flex-direction: column;
    border-right: 1px solid rgba(5, 5, 5, 0.06);
    overflow: hidden;

    .panel-header {
      flex: 0 0 auto;
      border-bottom: 1px solid rgba(5, 5, 5, 0.06);
      padding-bottom: 10px;
      margin: 0 14px 10px 0;

      h3 {
        margin: 0;
        font-size: 16px;
        font-weight: 700;
        color: #464c64;
      }
    }

    .tree-container {
      flex: 1 1 auto;
      min-height: 0;
      height: auto;
      overflow: hidden;

      .tree-container-custom {
        height: 100%;

        :deep(.u-tree-search) {
          margin-right: 14px;
        }

        :deep(.el-tree) {
          padding-right: 14px;
        }
      }

      :deep(.el-tree) {
        --el-tree-node-content-height: 100%;
      }

      :deep(.el-tree-node__content.is-current) {
        background-color: var(--el-color-primary-light-9) !important;
        color: var(--el-color-primary);
      }
    }
  }

  .right-panel {
    width: 100%;
    height: 100%;
    display: flex;
    min-width: 0;
    flex-direction: column;
    overflow: hidden;
  }
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

.target-db-type {
  display: block;
  margin-top: 2px;
  color: #8a98ad;
  font-size: 12px;
}

.target-drawer-content {
  position: relative;
  min-height: 180px;
}

.target-drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}

.list-filters {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex: 0 1 auto;
  min-width: 0;
  margin-left: auto;
}

.keyword-input {
  flex: 0 1 200px;
  width: 200px;
  min-width: 160px;
  max-width: 200px;
}

.keyword-search-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  color: #1677ff;
  cursor: pointer;
  border-radius: 6px;

  &:hover {
    color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
  }
}

.source-table {
  flex: 1;
  min-height: 0;
  width: 100%;
  min-width: 0;
  overflow: hidden;

  :deep(.is-expanded-source) {
    background: #f8fbff;
  }

  :deep(.el-table__expanded-cell) {
    padding: 12px 18px 16px 58px;
    background: #f8fbff;
  }

  :deep(.pagination) {
    container-type: inline-size;
    display: flex;
    flex-wrap: nowrap;
    align-items: center;
    gap: 10px;
    min-width: 0;
    overflow: hidden;
    margin-top: 4px;
  }

  :deep(.pagination > span:first-child),
  :deep(.pagination .el-pagination) {
    flex: 0 0 auto;
    white-space: nowrap;
  }
}

.task-name-cell {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.table-name-cell {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  max-width: 100%;
  color: #17233d;
  cursor: pointer;

  > div {
    flex: 1;
    min-width: 0;
  }

  strong,
  small {
    display: block;
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: #17233d;
    font-size: 14px;
    font-weight: 700;
    line-height: 20px;
  }

  small {
    margin-top: 2px;
    color: #8a98ad;
    font-size: 12px;
    line-height: 18px;
  }

  &:hover strong {
    color: var(--el-color-primary);
  }

  &:hover .table-name-cn {
    color: #0b5fd6;
  }

}

@container (max-width: 680px) {
  .source-table :deep(.pagination > span:first-child),
  .source-table :deep(.pagination .el-pagination__jump) {
    display: none;
  }
}

@container (max-width: 480px) {
  .source-table :deep(.pagination .el-pagination__sizes) {
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

  .keyword-input {
    flex: 1 1 100%;
    width: 100%;
    max-width: none;
  }
}

.business-type-tag {
  border: 0;
  font-weight: 600;

  &.is-business {
    color: #0958d9;
    background: #e6f4ff;
  }

  &.is-log {
    color: #874d00;
    background: #fff7d6;
  }

  &.is-dict {
    color: #237804;
    background: #edf8e8;
  }

  &.is-process {
    color: #531dab;
    background: #f4edff;
  }

  &.is-unconfirmed {
    color: #475467;
    background: #f2f4f7;
  }

  &.is-backup,
  &.is-default {
    color: #434343;
    background: #f5f5f5;
  }
}

.tree-db-icon-tip {
  display: inline-flex;
  align-items: center;
  cursor: help;
}

.table-name-cn {
  display: block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #1f6dd4;
  font-size: 14px;
  font-weight: 400 !important;
  line-height: 20px;
}

.source-name-cell {
  display: block;
  max-width: 100%;
  min-width: 0;
  cursor: default;

  strong,
  small {
    display: block;
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: #17233d;
    font-size: 14px;
    font-weight: 600;
    line-height: 20px;
  }

  small {
    margin-top: 2px;
    color: #8a98ad;
    font-size: 12px;
    line-height: 18px;
  }
}

.source-name-cn {
  display: block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #1f6dd4;
  font-size: 14px;
  font-weight: 400 !important;
  line-height: 20px;
}

.source-name-cell.is-link {
  cursor: pointer;

  &:hover .source-name-cn {
    color: #0b5fd6;
  }
}

.source-table :deep(.table-name-cn),
.source-table :deep(.source-name-cn) {
  color: var(--el-color-primary);
  font-weight: 400 !important;
}

.source-table :deep(.table-name-cell:hover .table-name-cn),
.source-table :deep(.source-name-cell.is-link:hover .source-name-cn),
.source-table :deep(.el-button.is-link:hover span) {
  color: var(--el-color-primary);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.task-table-wrap {
  border: 1px solid #dfe9f7;
  border-radius: 10px;
  background: #fff;
  overflow: hidden;
}

.task-empty {
  color: #9aa6b2;
  font-size: 12px;
}

.reject-modify-action {
  min-height: 20px;
  padding: 0;
  font-size: 14px;
  line-height: 20px;
}

.child-table-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-bottom: 1px solid #edf2f8;
  background: linear-gradient(180deg, #fbfdff, #f6f9fe);

  strong {
    margin-right: 10px;
    color: #17233d;
    font-size: 14px;
  }

  span {
    color: #78869d;
    font-size: 12px;
  }
}

.task-table {
  :deep(.el-button.is-link) {
    padding: 0 4px;
  }
}

.source-select-empty {
  flex: 1;
  min-height: 0;
  border: 1px dashed #d9e4f2;
  border-radius: 12px;
  background: #fbfdff;
}

.muted {
  color: #98a2b3;
}

.table-detail-content {
  min-height: 220px;
}

.detail-table-heading {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;

  strong,
  small {
    display: block;
  }

  strong {
    color: #17233d;
    font-size: 16px;
    line-height: 1.5;
  }

  small {
    margin-top: 2px;
    color: #8a98ad;
    font-size: 12px;
  }
}
</style>
