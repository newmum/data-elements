<template>
  <div class="data-convergence card-container datasource-explorer-page flex flex-col px-5 pt-5 pb-2 w-full h-full">
    <el-splitter class="datasource-explorer-splitter">
      <el-splitter-panel size="300px" min="10%">
        <div class="left-panel">
          <div class="panel-header">
          <h3>接入任务</h3>
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
        <div ref="sourcePanelRef" class="right-panel pl-4" :style="{ '--task-links-width': taskLinksWidth ? `${taskLinksWidth}px` : '100%' }">
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
            :expand-row-keys="expandedRows"
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
                <div class="task-links-wrap" :class="{ 'task-links-wrap--compact': taskLinksWidth > 0 && taskLinksWidth < 560 }">
                  <div v-if="hasChildTasks(row)" class="task-links">
                    <article v-for="route in taskLinkRoutes(row)" :key="route.key" class="task-link-card" :style="{ '--task-route-min-width': `${route.stages.length * 160 + (route.stages.length - 1) * 120}px` }">
                      <div class="task-link-card__route" aria-label="数据链路">
                        <template v-for="(stage, stageIndex) in route.stages" :key="stageIndex">
                          <div v-if="stageIndex" class="task-link-arrow" :aria-label="route.links[stageIndex - 1].text">
                            <span class="task-link-arrow__line" aria-hidden="true"></span>
                            <el-tag class="task-link-arrow__status" size="small" effect="light" :type="route.links[stageIndex - 1].tone === 'idle' ? 'info' : route.links[stageIndex - 1].tone">{{ route.links[stageIndex - 1].text }}</el-tag>
                            <span class="task-link-arrow__line task-link-arrow__line--end" aria-hidden="true"></span>
                          </div>
                          <div class="task-targets">
                            <div v-for="endpoint in stage" :key="endpoint.id" class="task-endpoint" :class="stageIndex ? 'task-endpoint--target' : 'task-endpoint--source'">
                              <Icon class="task-endpoint__icon" :icon="chainDatabaseIcon(endpoint.dbType)" />
                              <div class="task-endpoint__names">
                                <button type="button" class="task-endpoint__database" :disabled="!endpoint.dbId" :title="endpoint.database" @click="openDatasourceDetail({ datasourceId: endpoint.dbId, datasourceName: endpoint.database })">{{ endpoint.database }}</button>
                                <button type="button" class="task-endpoint__table" :disabled="!endpoint.id || endpoint.id === 'unknown'" :title="[endpoint.table, endpoint.remark].filter(Boolean).join(' · ')" @click="openTableDetail({ tid: endpoint.id, tableName: endpoint.table, tableNameCn: endpoint.remark })">{{ endpoint.table }}</button>
                              </div>
                            </div>
                          </div>
                        </template>
                      </div>
                      <div v-if="route.warning" class="task-link-card__warning">{{ route.warning }}</div>

                    </article>
                  </div>

                  <el-empty
                    v-else
                    description="该数据表尚未创建接入任务"
                    :image-size="72"
                  />
                </div>
              </template>
            </el-table-column>

            <el-table-column prop="tableName" label="数据表" min-width="230">
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
                  <small :title="row.orgName || '-'">{{ row.orgName || "-" }}</small>
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
                <el-tag
                  v-if="Number(row.taskCount) > 0"
                  type="success"
                  size="small"
                >
                  已接入
                </el-tag>
                <el-button
                  v-else
                  link
                  type="warning"
                  class="reject-modify-action"
                  :disabled="!getSourceTableId(row)"
                  @click="handleRejectForModify(row)"
                >
                  驳回修改
                </el-button>
              </template>
            </el-table-column>
            <el-table-column label="操作" fixed="right" width="104" align="center" header-align="center">
              <template #default="{ row }">
                <el-button
                  v-if="!row.taskCount"
                  link
                  type="primary"
                  :loading="creatingTaskId === getSourceTableId(row)"
                  :disabled="Boolean(creatingTaskId) || !getSourceTableId(row)"
                  @click="handleTableAction(row, 'createTable')"
                >
                  创建任务
                </el-button>
                <template v-if="Number(row.taskCount) > 0 && row.tasks?.length">
                  <el-dropdown @command="(command) => handleRowTaskMenu(command, row)">
                    <el-button link type="primary">查看任务</el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item :command="{ action: 'setting', taskId: row.tasks[0].tid }">查看任务</el-dropdown-item>
                        <el-dropdown-item :command="{ action: 'tables', taskId: row.tasks[0].tid }">删除任务</el-dropdown-item>
                        
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </template>
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
      :existing-targets="pendingTaskRow ? targetTablesOf(pendingTaskRow) : []"
      @save="handleApplySave"
      @target-deleted="handleTargetDeleted"
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
          <div class="target-source-summary">
            <span class="target-source-label">来源表</span>
            <strong>{{ targetDrawerContext.tableNameCn || targetDrawerContext.tableComment || targetDrawerContext.tableName || '-' }}</strong>
            <small>{{ qualifiedTableName(targetDrawerContext.tableName, targetDrawerContext.schemaName) }}</small>
          </div>
          <div class="target-section-summary">
            <span>接入任务 · {{ targetDrawerSections.filter(section => section.task).length }} 个</span>
            <span>目标表 · {{ targetDrawerSections.reduce((total, section) => total + section.targets.length, 0) }} 张</span>
          </div>
          <el-alert v-if="targetDrawerSections.some(section => section.targets.some(isHiveTarget))" type="info" :closable="false" show-icon class="mb-4"
            title="Hive 仅移除接入登记和流程，物理表及数据保留；后续可校验并复用已有表创建流程。" />
          <section v-for="(section, index) in targetDrawerSections" :key="section.key" class="target-task-section">
            <div class="target-task-heading">
              <span class="target-task-number">{{ index + 1 }}</span>
              <h4>{{ section.title }}</h4>
              <el-tag v-if="section.task" size="small" :type="section.status.tone === 'idle' ? 'info' : section.status.tone">{{ section.status.text }}</el-tag>
            </div>
            <div v-if="section.task" class="target-task-source">来源表：{{ taskSourceTableName(section.task, targetDrawerContext) }}</div>
            <el-table :data="section.targets" border size="small" empty-text="此任务暂未创建目标表">
              <el-table-column type="index" label="序号" width="56" />
              <el-table-column prop="targetDbName" label="目标数据源" min-width="135" show-overflow-tooltip>
                <template #default="{ row: target }">
                  {{ target.targetDbName || target.datasourceName || target.targetDbId || '-' }}
                  <small v-if="target.targetDbType" class="target-db-type">{{ target.targetDbType }}</small>
                </template>
              </el-table-column>
              <el-table-column prop="targetTableName" label="目标表" min-width="170" show-overflow-tooltip />
              <el-table-column label="状态" width="76" align="center">
                <template #default><el-tag size="small" type="success">已创建</el-tag></template>
              </el-table-column>
              <el-table-column label="操作" width="132" align="center">
                <template #default="{ row: target }">
                  <el-button link type="danger" :disabled="deletingAllTargets" @click="deleteSingleTarget(section, target)">{{ isHiveTarget(target) ? "移除登记和流程" : "删除目标表" }}</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div v-if="section.task || section.targets.length" class="target-task-actions">
              <el-button v-if="section.task" size="small" type="danger" plain :disabled="deletingAllTargets" @click="deleteCurrentFlow(section)">删除此任务流程</el-button>
              <el-button v-if="section.targets.length" size="small" type="danger" plain :disabled="deletingAllTargets" @click="deleteAllTargets(section)">清理此组目标和流程</el-button>
            </div>
          </section>
          <el-empty v-if="!targetDrawerSections.length" description="尚未创建接入任务或目标表" :image-size="80" />
        </template>
      </div>
      <template #footer>
        <div class="target-drawer-footer">
          <el-button v-if="targetDrawerSections.some(section => section.targets.length || section.task?.tid)" type="danger"
            :loading="deletingAllTargets" :disabled="deletingAllTargets" @click="deleteAllTargets()">清理全部目标和流程</el-button>
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
const sourcePanelRef = ref();
const taskLinksWidth = ref(0);
let taskLinksObserver;
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
const creatingTaskId = ref("");
const targetDrawerVisible = ref(false);
const targetDrawerContext = ref(null);
const deletingAllTargets = ref(false);
const deleteAllTargetProgress = ref(0);
const deleteAllTargetCount = ref(0);
const deleteAllLoadingText = computed(() => {
  if (!deletingAllTargets.value) return "";
  if (!deleteAllTargetCount.value) return "正在准备清理目标表和流程...";
  return `正在清理第 ${Math.max(1, deleteAllTargetProgress.value)}/${deleteAllTargetCount.value} 项目标表或流程...`;
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

const qualifiedTableName = (name, schema) => {
  const table = String(name || '').trim();
  const owner = String(schema || '').trim();
  return table && owner && !table.includes('.') ? `${owner}.${table}` : table;
};

// 级联任务使用接口返回的真实上一跳表，不能用展开行的原始来源覆盖它。
const taskSourceTableName = (task, row) => qualifiedTableName(
  task.sourceTableName || (String(task.sourceTableId) === String(getSourceTableId(row)) ? row.tableName : ''),
  task.sourceSchemaName,
) || '来源表名称缺失';

const taskLinkTargets = (task) => Array.isArray(task?.targetTables) && task.targetTables.length
  ? task.targetTables : [task];

// 每段连接线对应本段任务，不把上一跳或整行状态套到所有连接线上。
const taskLinkStatus = (task) => {
  // 当前运行态与最近执行结果分开；SUCCESS 不意味着任务已经停止。
  const current = String(task?.currentStatus || '').trim().toUpperCase();
  const label = String(task?.taskStatusText || '').trim();
  if (current.includes('FAIL') || current.includes('ERROR')) {
    return { text: '执行异常', tone: 'danger' };
  }
  if (current === 'RUNNING') return { text: '运行中', tone: 'success' };
  if (['STOPPED', 'STOPPING', 'DISABLED', 'NOT_STARTED', 'READY', 'DRAFT', 'SAVED'].includes(current)) {
    return { text: '未启动', tone: 'idle' };
  }
  if (!current && String(task?.taskStatus) === '2') return { text: '执行异常', tone: 'danger' };
  if (!current && String(task?.taskStatus) === '1') return { text: '运行中', tone: 'success' };
  if (['执行失败', '运行异常', '执行异常'].includes(label)) return { text: '执行异常', tone: 'danger' };
  if (['执行中', '运行中'].includes(label)) return { text: '运行中', tone: 'success' };
  // 执行成功只代表历史执行结果；没有运行态时不能画成正在运行。
  return { text: '未启动', tone: 'idle' };
};

// 只使用当前分页已授权的批量元数据。表 ID 决定链路，名称相同不能当作关联。
const taskLinkRoutes = (row) => {
  const tasks = Array.isArray(row?.tasks) ? row.tasks.slice(0, 100) : [];
  const rootId = String(getSourceTableId(row) || '');
  const sourceId = (task) => String(task.sourceTableId || rootId);
  const targetId = (target) => String(target.targetTableId || target.tid || target.tableId || '');
  const bySource = new Map();
  const seenTasks = new Set();
  tasks.forEach((task) => {
    const id = String(task.tid || task.id || '');
    if (!id || seenTasks.has(id)) return;
    seenTasks.add(id);
    const key = sourceId(task);
    if (!bySource.has(key)) bySource.set(key, []);
    bySource.get(key).push(task);
  });
  const routes = [];
  const endpoint = (target) => ({
    id: targetId(target) || 'unknown', dbId: target.targetDbId || '', dbType: target.targetDbType || '', database: target.targetDbName || '目标库名称缺失',
    table: qualifiedTableName(target.targetTableName, target.targetSchemaName) || '目标表名称缺失',
    remark: target.targetTableNameCn || '',
  });
  const append = (stages, pathTasks, warning = '') => {
    if (routes.length >= 100) return;
    routes.push({ key: `${pathTasks.map(task => task.tid || task.id).join('/')}:${stages.map(stage => stage.map(item => item.id).join(',')).join('/')}`,
      stages, tasks: pathTasks, links: pathTasks.map(taskLinkStatus), warning });
  };
  const walk = (task, stages, pathTasks, visitedTables) => {
    if (routes.length >= 100) return;
    const nextTasks = [...pathTasks, task];
    const targets = taskLinkTargets(task);
    const next = targets.map(target => ({ target, children: bySource.get(targetId(target)) || [] }));
    // 同一任务的多个最终目标保持并列，避免把并行写入画成顺序写入。
    if (next.every(item => !item.children.length)) {
      append([...stages, targets.map(endpoint)], nextTasks);
      return;
    }
    for (const { target, children } of next) {
      const id = targetId(target);
      const extended = [...stages, [endpoint(target)]];
      if (visitedTables.has(id) || nextTasks.length >= 16) {
        append(extended, nextTasks, '链路存在循环或超过展示上限，请查看各任务配置。');
      } else if (!children.length) {
        append(extended, nextTasks);
      } else {
        for (const child of children) walk(child, extended, nextTasks, new Set([...visitedTables, id]));
      }
    }
  };
  for (const task of bySource.get(rootId) || []) {
    walk(task, [[{ id: rootId, dbId: task.sourceDbId || row.datasourceId || '', dbType: task.sourceDbType || row.dbType || '', database: task.sourceDbName || row.datasourceName || '来源库名称缺失',
      table: taskSourceTableName(task, row), remark: task.sourceTableNameCn || row.tableNameCn || '' }]], [], new Set([rootId]));
  }
  return routes;
};

// 目标表以来源表 source_table_id 为主关联。兼容尚未创建任务的已物化表，
// 也兼容历史任务中仅保存一个 targetTableId 的记录。
const targetTablesOf = (row, task = null) => {
  const candidates = [
    ...(!task && Array.isArray(row?.targetTables) ? row.targetTables : []),
    ...(Array.isArray(task?.targetTables) ? task.targetTables : []),
  ];
  if (task?.targetTableId || (!task && (row?.targetTableId || row?.materializedTableId))) {
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
    if (!targetTableId || seen.has(String(targetTableId))) return false;
    seen.add(String(targetTableId));
    return true;
  }).map((item) => ({
    ...item,
    targetTableId: item.targetTableId || item.tid || item.tableId,
    targetTableName: item.targetTableName || item.tableName || item.tableNameCn || '',
  }));
};

const hasTargetTables = (row) => targetTablesOf(row).length > 0;
// 全部任务来自当前分页的已授权批量数据；展示和分组均不发起补查。
const targetTaskSectionsOf = (row) => {
  const seen = new Set();
  const tasks = (Array.isArray(row?.tasks) ? row.tasks : []).filter(task => {
    const id = String(task?.tid || '');
    if (!id || seen.has(id)) return false;
    seen.add(id);
    return true;
  });
  const sections = tasks.map(task => {
    const targets = targetTablesOf(row, task);
    const databases = [...new Set(targets.map(target => target.targetDbName || target.datasourceName).filter(Boolean))];
    return { key: `task:${task.tid}`, task, targets, status: taskLinkStatus(task),
      title: `${task.sourceDbName || row?.datasourceName || '来源库'} → ${databases.join('、') || task.targetDbName || '目标库'}` };
  });
  const claimed = new Set(sections.flatMap(section => section.targets.map(target => String(target.targetTableId))));
  const unassigned = targetTablesOf(row).filter(target => !claimed.has(String(target.targetTableId)));
  if (unassigned.length) sections.push({ key: 'unassigned', task: null, targets: unassigned, title: '已建目标表（尚未关联任务）' });
  return sections;
};
const targetDrawerSections = computed(() => targetTaskSectionsOf(targetDrawerContext.value));

const openTargetDrawer = (row) => {
  targetDrawerContext.value = row || null;
  targetDrawerVisible.value = true;
};

// 操作再次从当前上下文定位任务，不能借用别组来源表或任意传入的目标 ID。
const currentTargetSection = (section) => targetDrawerSections.value.find(item => item.key === section?.key) || null;

const handleRowTaskMenu = (command, row) => {
  const task = (row?.tasks || []).find(item => String(item.tid) === String(command?.taskId));
  if (task) handleTaskMenu(command.action, task, row);
};

const chainDatabaseIcon = (value) => {
  const key = String(value || '').toLowerCase().replace(/[\s_-]/g, '');
  const aliases = { pg: 'postgresql', postgres: 'postgresql', mssql: 'sqlserver', dm: 'dameng',
    kingbase: 'kingbase8', kingbasees: 'kingbase8', oceanbase: 'oceanbasemysql',
    mrshive: 'hive', huaweihive: 'hive', huawei: 'hive', es: 'elasticsearch' };
  const icon = aliases[key] || key;
  return ['mysql','oracle','oceanbasemysql','oceanbaseoracle','gaussdb','gbase8a','gbase8s',
    'sqlserver','hive','dameng','postgresql','kingbase8','minio','ftp','sftp','api','kafka',
    'elasticsearch','db2','doris','clickhouse','hbase','hdfs','highgo','oscar','starrocks',
    'maxcompute','vertica'].includes(icon) ? icon : 'database-network';
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
      $common.post("/ods/dataAggTreeStatistics", statisticsPayload()),
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
    const result = await $common.post("/ods/dataAggPage", {
      viewLevel: "table",
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
    console.error("加载数据列表失败", error);
    $message.error("数据表列表加载失败，请稍后重试");
    return { list: [], total: 0 };
  } finally {
    state.loading = false;
  }
};

const getSourceTableId = (row) => row?.sourceTableId || row?.tid || row?.tableId || "";

const handleSearch = () => {
  tableRef.value?.refresh?.(true);
};

const handleSourceSortChange = ({ prop, order }) => {
  if (order && !["tableName", "taskCount", "createdTime", "updatedTime", "fieldCount", "recordCount"].includes(prop)) return;
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
    path: "/jr/data-convergence/detail",
    query: {
      id: tableId,
      type: "table",
      title: row.tableNameCn || row.tableComment || row.tableName || "数据表",
      from: "/jr/data-convergence/list",
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
    path: "/jr/data-convergence/detail",
    query: {
      id: datasourceId,
      type: "db",
      title: row.datasourceName || row.dbName || "数据源",
      from: "/jr/data-convergence/list",
    },
  });
};

const tableTypeIcon = (row) => {
  const type = String(row?.tableType || "").toLowerCase();
  return type.includes("view") || type === "视图" ? "view" : "table";
};

const taskStatusType = (task) => {
  if (["执行失败", "运行异常"].includes(task?.taskStatusText)) return "danger";
  if (["执行中", "运行中", "执行成功"].includes(task?.taskStatusText)) return "success";
  if (["停止", "已停止", "未启用"].includes(task?.taskStatusText)) return "info";
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

const ensureAccessTask = async (row, forceRebuild = false) => {
  const payload = {
    tableId: getSourceTableId(row),
    catalogId: row?.sourceCatalogId || row?.catalogId || "",
    ...(Array.isArray(row?.targetTableIds) && row.targetTableIds.length
      ? { targetTableIds: row.targetTableIds }
      : (row?.targetTableId ? { targetTableId: row.targetTableId } : {})),
    ...(forceRebuild ? { forceRebuild: true } : {}),
    ...(row?.sourceFileConfig ? { sourceFileConfig: row.sourceFileConfig } : {}),
  };
  if (!payload.tableId) {
    $message.warning("无法读取来源表标识，暂不能创建接入任务");
    return null;
  }
  const result = await $common.post("/ods/dataAggTaskEnsure", payload, { _hiddenErrorMsg: true });
  const response = result?.data || result;
  const tasks = Array.isArray(response?.tasks) ? response.tasks : [];
  const task = tasks[0] || response?.task || response;
  const taskId = task?.tid || response?.taskId;
  if (!taskId) {
    $message.warning("接入任务创建失败，请稍后重试");
    return null;
  }
  const pipelineId = task?.pipelineId || task?.pipeline_id || result?.pipelineId || result?.data?.pipelineId || "";
  const repairRequired = Boolean(result?.repairRequired ?? result?.data?.repairRequired);
  const repairReasons = Array.isArray(result?.repairReasons)
    ? result.repairReasons
    : (Array.isArray(result?.data?.repairReasons) ? result.data.repairReasons : []);
  if (repairRequired) {
    return { ...(task || {}), tid: taskId, pipelineId, tasks, repairRequired, repairReasons };
  }
  const datasourceId = row?.datasourceId || row?.dbId || row?.sourceDbId || "";
  // 所有来源都可安全调用：后端会对非 API 拉取来源返回 applicable=false。
  // 这里发生在用户明确点击“创建任务”之后，绝不在浏览/打开页面时隐式创建或启动任务。
  let bindingWarning = "";
  if (datasourceId && pipelineId) {
    try {
      await $common.post("/ods/api-pull/bind-task", {
        datasourceId,
        sourceTableId: payload.tableId,
        pipelineId,
      });
    } catch (error) {
      // API 表规则绑定只是附加关联，不能阻止已创建的任务打开画布。
      console.warn("接入任务已创建，但 API 表规则绑定失败", error);
      bindingWarning = "任务已创建，但 API 表规则绑定失败；可先在画布中配置流程";
    }
  }
  return {
    ...(task || {}),
    tid: taskId,
    pipelineId,
    bindingWarning,
    tasks: tasks.length ? tasks : [{ ...(task || {}), tid: taskId, pipelineId }],
    createdCount: Number(response?.createdCount || 0),
    repairRequired,
    repairReasons,
  };
};

// 页面只负责说明策略，删除类型与权限由服务端登记记录决定。
const isHiveTarget = (target) => ['hive', 'mrshive', 'huaweihive', 'huaweimrshive'].includes(
  String(target?.targetDatabaseType || target?.targetDbType || target?.dbType || '')
    .trim().toLowerCase().replace(/[_-]/g, ''),
);
const targetCleanupConfirmText = (target, preview = {}) => {
  const name = target?.targetTableName || target?.tableName || '当前目标表';
  if (preview.targetHivePhysicalRetained === true || isHiveTarget(target)) {
    return `将移除“${name}”的接入流程和目标登记，Hive 物理表及数据保留。后续可在物化建表时确认复用已有表并重新创建流程。确认继续吗？`;
  }
  if (preview.targetCleanupPending === true) {
    return `目标库暂时无法检查，将取消可清理的接入流程，并保留“${name}”的目标登记供重试。确认继续吗？`;
  }
  return `将删除目标物理表“${name}”及其元数据，并移除接入流程。该操作不可恢复；若目标库删除失败会保留登记供重试。确认继续吗？`;
};
const targetCleanupSummary = (outcomes) => {
  const pending = outcomes.filter((item) => item.targetCleanupPending === true || Number(item.processGroupCleanupPendingCount || 0) > 0).length;
  const hive = outcomes.filter((item) => item.targetHivePhysicalRetained === true).length;
  const message = outcomes.length === 1 && outcomes[0].message
    ? outcomes[0].message
    : `已处理 ${outcomes.length} 个目标的接入清理${hive ? `；${hive} 个 Hive 物理表及数据保留` : ''}${pending ? `；${pending} 个目标有清理待重试，相关登记或流程记录已保留` : ''}`;
  return { pending, message };
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
  const actionText = deleteTargetTable ? (isHiveTarget(target) ? "移除 Hive 接入登记和流程" : "删除目标表并移除流程") : "删除任务";
  try {
    await ElMessageBox.confirm(
      deleteTargetTable
        ? targetCleanupConfirmText(target)
        : "将删除当前接入任务和关联流程，已创建的目标表及其数据会保留，可通过“查看建表情况”统一管理。确认继续吗？",
      actionText,
      { type: "warning", confirmButtonText: "确认", cancelButtonText: "取消" },
    );
    const response = await $common.post("/ods/dataAggReset", {
      sourceTableId,
      ...(!deleteTargetTable && task?.tid ? { taskId: task.tid } : {}),
      targetTableId: target?.targetTableId || task?.targetTableId || task?.target_table_id || row?.targetTableId || "",
      targetDbId: target?.targetDbId || "",
      targetTableName: target?.targetTableName || target?.tableName || "",
      deleteTargetTable,
    });
    scheduleVisible.value = false;
    accessTaskId.value = "";
    targetDrawerVisible.value = false;
    const outcome = response?.data || response || {};
    const summary = targetCleanupSummary([outcome]);
    $message[summary.pending ? 'warning' : 'success'](summary.message || '接入清理已处理');
    await tableRef.value?.refresh?.();
  } catch (error) {
    if (error === "cancel" || error === "close") return;
    console.error("重建接入任务失败", error);
    $message.error(error?.message || error || "重建操作失败，请稍后重试");
  }
};

const deleteCurrentFlow = (section) => {
  const current = currentTargetSection(section);
  if (!deletingAllTargets.value && current?.task) return handleTaskRecovery(current.task, targetDrawerContext.value, "flow");
};

const deleteSingleTarget = (section, target) => {
  const current = currentTargetSection(section);
  const owned = current?.targets.find(item => String(item.targetTableId) === String(target?.targetTableId)
    && String(item.targetDbId || '') === String(target?.targetDbId || ''));
  if (!deletingAllTargets.value && owned) return handleTaskRecovery(current.task, targetDrawerContext.value, "target", owned);
};

// 操作前置元数据已由分页接口按租户和角色范围批量返回，禁止逐任务补查。
// 以真实表 ID 排依赖：先处理末端目标及流程，再处理其上游来源目标。
const targetCleanupPlan = (sections, row) => {
  const entries = new Map();
  for (const section of sections) {
    const sourceTableId = String(section.task?.sourceTableId || section.task?.source_table_id || getSourceTableId(row) || '');
    if (!sourceTableId) throw new Error('来源表标识缺失，请刷新后重试');
    for (const target of section.targets) {
      const key = `target:${target.targetTableId}`;
      const prior = entries.get(key);
      if (prior && (prior.sourceTableId !== sourceTableId || String(prior.target.targetDbId || '') !== String(target.targetDbId || ''))) {
        throw new Error('目标表关联到不同来源，请刷新并核对任务关系后重试');
      }
      if (!prior) entries.set(key, { key, sourceTableId, target, task: section.task, dependencies: [] });
    }
    if (!section.targets.length && section.task?.tid) {
      const key = `flow:${section.task.tid}`;
      entries.set(key, { key, sourceTableId, target: null, task: section.task, dependencies: [] });
    }
  }
  const bySource = new Map();
  for (const entry of entries.values()) {
    if (!bySource.has(entry.sourceTableId)) bySource.set(entry.sourceTableId, []);
    bySource.get(entry.sourceTableId).push(entry);
  }
  const ordered = [], visited = new Set(), visiting = new Set();
  const visit = (entry) => {
    if (visited.has(entry.key)) return;
    if (visiting.has(entry.key)) throw new Error('目标链路存在循环，请先核对任务关系');
    visiting.add(entry.key);
    const children = entry.target ? (bySource.get(String(entry.target.targetTableId)) || []) : [];
    entry.dependencies = children.map(child => child.key);
    children.forEach(visit);
    visiting.delete(entry.key);
    visited.add(entry.key);
    ordered.push(entry);
  };
  [...entries.values()].forEach(visit);
  return ordered;
};

const deleteAllTargets = async (section = null) => {
  if (deletingAllTargets.value) return;
  const selected = section ? currentTargetSection(section) : null;
  if (section && !selected) return;
  const sections = selected ? [selected] : [...targetDrawerSections.value];
  let plan;
  deletingAllTargets.value = true;
  try {
    plan = targetCleanupPlan(sections, targetDrawerContext.value);
    if (!plan.length) { deletingAllTargets.value = false; return; }
    const targetCount = plan.filter(item => item.target).length;
    const flowCount = sections.filter(item => item.task?.tid).length;
    await ElMessageBox.confirm(
      `将清理${selected ? `“${selected.title}”下的` : '当前来源表关联的全部'} ${targetCount} 张目标表及 ${flowCount} 个任务流程。Hive 物理表及数据保留，其他数据库会执行删表。按链路末端到上游处理，失败的目标及其上游保留供重试。确认继续吗？`,
      selected ? "清理此组目标和流程" : "清理全部目标和流程",
      { type: "warning", confirmButtonText: "确认清理", cancelButtonText: "取消" },
    );
  } catch (error) {
    deletingAllTargets.value = false;
    if (error !== "cancel" && error !== "close") $message.error(error?.message || "清理确认失败，请稍后重试");
    return;
  }

  deletingAllTargets.value = true;
  deleteAllTargetCount.value = plan.length;
  deleteAllTargetProgress.value = 0;
  const outcomes = [], blocked = new Set();
  let failed = 0, skipped = 0;
  try {
    for (const [index, entry] of plan.entries()) {
      deleteAllTargetProgress.value = index + 1;
      if (entry.dependencies.some(key => blocked.has(key))) {
        blocked.add(entry.key);
        skipped += 1;
        continue;
      }
      try {
        const target = entry.target;
        const response = await $common.post("/ods/dataAggReset", {
          sourceTableId: entry.sourceTableId,
          ...(target ? { targetTableId: target.targetTableId, targetDbId: target.targetDbId || "",
            targetTableName: target.targetTableName || "", deleteTargetTable: true }
            : { taskId: entry.task.tid, deleteTargetTable: false }),
        }, { _hiddenErrorMsg: true }, 120 * 1000);
        const outcome = response?.data || response || {};
        outcomes.push(outcome);
        if (outcome.targetCleanupPending === true || Number(outcome.processGroupCleanupPendingCount || 0) > 0) blocked.add(entry.key);
      } catch (error) {
        // 某一目标异常不阻止独立分支；依赖该目标的上游不得继续删表。
        blocked.add(entry.key);
        failed += 1;
        console.warn('目标清理未完成，已保留相关登记供重试', error?.errorCode || error?.code || 'REQUEST_FAILED');
      }
    }
    targetDrawerVisible.value = false;
    accessTaskId.value = "";
    const summary = targetCleanupSummary(outcomes);
    const completed = outcomes.length - summary.pending;
    const hive = outcomes.filter(item => item.targetHivePhysicalRetained === true).length;
    const pending = summary.pending + failed + skipped;
    const message = `已完成 ${completed}/${plan.length} 项目标和流程清理${hive ? `；${hive} 个 Hive 物理表及数据保留` : ''}${pending ? `；${pending} 项待重试${skipped ? `（其中 ${skipped} 项上游因下游未完成而保留）` : ''}` : ''}`;
    $message[pending ? 'warning' : 'success'](message);
    await tableRef.value?.refresh?.();
  } catch (error) {
    $message.error(error?.message || "清理完成后列表刷新失败，请刷新页面核对结果");
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
    creatingTaskId.value = "";
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
    const selectedTargets = Array.isArray(materializedTarget?.targetTables)
      ? materializedTarget.targetTables : [materializedTarget];
    const targetTableIds = [...new Set(selectedTargets.map(
      (target) => target?.targetTableId || target?.tid || "",
    ).filter(Boolean))];
    if (!targetTableIds.length) {
      throw new Error("目标表未创建成功，不能继续创建接入任务");
    }
    const sourceFileConfig = materializedTarget?.sourceFileConfig;
    let task = await ensureAccessTask({ ...row, targetTableIds, sourceFileConfig });
    if (task?.tid) {
      if (task.repairRequired) {
        const reasons = task.repairReasons.length
          ? task.repairReasons.join("；")
          : "已有接入画布需要更新";
        try {
          await ElMessageBox.confirm(
            `目标表已物化，但已有接入画布需要更新：${reasons}。重新生成会按每个目标表创建独立任务，并替换所选旧画布的人工配置；目标表及数据会保留。是否重新生成？`,
            "接入画布需要更新",
            { type: "warning", confirmButtonText: "重新生成", cancelButtonText: "保留旧画布" },
          );
        } catch (decision) {
          if (decision !== "cancel" && decision !== "close") throw decision;
          tableRef.value?.refresh?.();
          return;
        }
        const rebuilt = await ensureAccessTask({ ...row, targetTableIds, sourceFileConfig }, true);
        if (!rebuilt?.tid || rebuilt.repairRequired) {
          throw new Error("接入画布重新生成失败，请在任务设置中检查后重试");
        }
        task = rebuilt;
      }
      const tasks = task.tasks || [task];
      $message.success(tasks.length > 1
        ? `物化建表完成，已准备 ${tasks.length} 个级联接入任务，正在打开第一段画布`
        : (materializedTarget?.reusedExisting ? "已复用现有目标表并创建接入任务" : "物化建表完成，接入任务已创建"));
      expandedRows.value = [...new Set([...expandedRows.value, row.tid])];
      // 直接使用创建响应中的任务打开画布，不等待分页刷新，也不依赖排序后的行位置。
      const firstTask = tasks.find(item => String(item.sourceTableId || item.source_table_id || '') === String(getSourceTableId(row))) || tasks[0];
      openNifiDesigner(firstTask.tid || firstTask.id, firstTask.sourceTableId || firstTask.source_table_id || getSourceTableId(row));
      void Promise.resolve(tableRef.value?.refresh?.()).catch(error => console.warn("任务已创建，列表刷新失败", error));
    }
  } catch (error) {
    console.error("物化后创建接入任务失败", error);
    $message.error(`目标表已保留，接入任务创建未完成：${error?.message || error || '请稍后重试'}。处理后可在列表点击“创建任务”重试，无需重复物化或删表。`);
    tableRef.value?.refresh?.();
  }
};

const handleTargetDeleted = (target) => {
  if (pendingTaskRow.value) {
    pendingTaskRow.value = {
      ...pendingTaskRow.value,
      targetTables: targetTablesOf(pendingTaskRow.value).filter(
        (item) => String(item.targetTableId) !== String(target?.targetTableId),
      ),
      targetTableId: String(pendingTaskRow.value.targetTableId || '') === String(target?.targetTableId || '')
        ? '' : pendingTaskRow.value.targetTableId,
    };
  }
  tableRef.value?.refresh?.();
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
  // 展开单元格随主表横向滚动而变宽；链路按可见面板宽度排布。
  taskLinksObserver = new ResizeObserver(([entry]) => {
    taskLinksWidth.value = Math.max(0, entry.contentRect.width - 28);
  });
  if (sourcePanelRef.value) taskLinksObserver.observe(sourcePanelRef.value);
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
  taskLinksObserver?.disconnect();
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

.target-source-summary {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 14px 16px;
  background: var(--el-fill-color-light);
  border-radius: 6px;

  strong { color: var(--el-text-color-primary); font-size: 14px; line-height: 1.6; }
  small { color: var(--el-text-color-secondary); overflow-wrap: anywhere; }
}

.target-source-label { color: var(--el-text-color-secondary); font-size: 12px; }
.target-section-summary { display: flex; gap: 20px; margin: 18px 0; color: var(--el-text-color-secondary); font-size: 13px; }
.target-task-section {
  padding: 0 0 22px;
  & + & { padding-top: 20px; border-top: 1px solid var(--el-border-color-lighter); }
}
.target-task-heading {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-bottom: 9px;
  h4 { flex: 1; min-width: 0; margin: 0; color: var(--el-text-color-primary); font-size: 14px; line-height: 1.6; overflow-wrap: anywhere; }
}
.target-task-number { display: inline-flex; justify-content: center; align-items: center; flex: 0 0 22px; height: 22px; border-radius: 50%; color: var(--el-color-primary); background: var(--el-color-primary-light-9); font-size: 12px; }
.target-task-source { margin: 0 0 10px 31px; color: var(--el-text-color-secondary); font-size: 12px; overflow-wrap: anywhere; }
.target-task-actions { display: flex; justify-content: flex-end; gap: 8px; flex-wrap: wrap; margin-top: 12px; .el-button + .el-button { margin-left: 0; } }

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
    background: var(--el-fill-color-extra-light);
  }

  :deep(.el-table__expanded-cell) {
    padding: 12px 16px 16px 12px;
    background: var(--el-fill-color-extra-light);
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

.task-links-wrap {
  width: var(--task-links-width, 100%);
  max-width: 100%;
  box-sizing: border-box;
  min-width: 0;
  padding: 0;
  background: var(--el-fill-color-extra-light);
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

.task-links { display: flex; flex-direction: column; gap: 14px; }
.task-link-card { min-width: 0; padding: 0; border: 0; background: transparent; overflow-x: auto; }
.task-link-card__route { display: flex; align-items: center; gap: 12px; width: 100%; min-width: var(--task-route-min-width); }
.task-targets { display: flex; flex-direction: column; gap: 10px; flex: 0 1 240px; min-width: 160px; }
.task-endpoint { display: flex; align-items: center; gap: 10px; min-width: 0; }
.task-endpoint__icon { flex-shrink: 0; width: 28px; height: 28px; font-size: 28px; }
.task-endpoint__names { flex: 1; min-width: 0; }
.task-endpoint__database, .task-endpoint__table { display: block; max-width: 100%; padding: 0; border: 0; background: transparent; color: var(--el-text-color-primary); font: inherit; text-align: left; cursor: pointer; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.task-endpoint__database { font-size: 13px; font-weight: 600; line-height: 20px; }
.task-endpoint__table { margin-top: 4px; color: var(--el-text-color-regular); font: 12px/1.5 Consolas, monospace; }
.task-endpoint__database:hover:not(:disabled), .task-endpoint__table:hover:not(:disabled) { color: var(--el-color-primary); text-decoration: underline; }
.task-endpoint button:disabled { cursor: default; }
/* 箭头与状态在整组数据库/表名之间上下居中，颜色由状态标签表达。 */
.task-link-arrow { display: flex; flex: 1 0 96px; align-items: center; align-self: center; gap: 6px; min-width: 96px; max-width: 128px; height: 20px; color: var(--el-border-color-dark); }
.task-link-arrow__status { flex: 0 0 auto; height: 20px; padding: 0 7px; font-size: 12px; line-height: 18px; white-space: nowrap; }
.task-link-arrow__line { position: relative; display: block; flex: 1 1 0; min-width: 6px; height: 1px; background: currentColor; }
.task-link-arrow__line--end::after { position: absolute; top: -2px; right: 1px; width: 5px; height: 5px; border-top: 1px solid currentColor; border-right: 1px solid currentColor; transform: rotate(45deg); content: ''; }
.task-link-card__warning { margin-top: 8px; color: var(--el-color-warning); font-size: 12px; }
.task-links-wrap--compact .task-targets { flex-basis: 180px; }

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
