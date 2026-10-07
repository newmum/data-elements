<template>
  <div class="access-monitor-page">
    <el-splitter class="access-monitor-splitter">
      <el-splitter-panel size="260px" min="190px" max="320px">
        <aside class="access-node-tree-panel">
          <div class="access-node-tree-panel__header">
            <h3>数据接入节点</h3>
            <span>{{ nodeCount }} 个节点</span>
          </div>
          <el-input
            v-model.trim="nodeSearchKeyword"
            class="access-node-search"
            clearable
            placeholder="搜索所属网络或节点"
          >
            <template #prefix><Icon icon="el-icon-search" /></template>
          </el-input>

          <el-scrollbar class="access-node-groups" v-loading="nodeTreeLoading">
            <div v-if="visibleNodeGroups.length" class="access-node-group-list">
              <section
                v-for="network in visibleNodeGroups"
                :key="network.value"
                class="access-network-card"
                :class="[{ 'is-selected': isNetworkSelected(network) }, networkTone(network)]"
                @click="selectNetwork(network)"
              >
                <div class="access-network-card__head">
                  <span class="access-network-card__icon"><Icon :icon="networkIcon(network)" :size="20" /></span>
                  <strong>{{ network.label }}</strong>
                  <span class="access-network-card__count">{{ network.children.length }} 个节点</span>
                </div>
                <button
                  v-for="node in network.children"
                  :key="node.value"
                  type="button"
                  class="access-node-row"
                  :class="{ 'is-selected': selectedNodeId === node.value }"
                  @click.stop="selectNode(network, node)"
                >
                  <el-icon :size="17" class="access-node-row__icon"><Connection /></el-icon>
                  <span class="access-node-row__name">{{ node.label }}</span>
                  <span class="access-node-row__state" :class="{ 'is-offline': !node.enabled }">
                    <i></i>{{ node.enabled ? '已启用' : '未启用' }}
                  </span>
                  <el-tag v-if="node.isDefault" type="primary" size="small" effect="plain">默认</el-tag>
                </button>
              </section>
            </div>
            <el-empty v-else-if="!nodeTreeLoading" :image-size="62" description="未找到数据接入节点" />
          </el-scrollbar>
        </aside>
      </el-splitter-panel>

      <el-splitter-panel min="60%">
        <section class="access-monitor-content">
    <div class="access-toolbar">
      <el-segmented v-model="activeTab" :options="tabs" @change="handleTabChange" />

      <div class="toolbar-actions">
        <el-input
          v-model="searchVal"
          clearable
          class="task-search"
          placeholder="请输入任务名称、来源表或目标表"
          @clear="refreshTable"
          @keyup.enter="refreshTable"
        >
          <template #suffix>
            <Icon icon="el-icon-search" class="search-icon" @click="refreshTable" />
          </template>
        </el-input>
        <el-button :loading="cleaningHistory" @click="cleanupMonitorHistory">清理监控历史</el-button>
        <el-button type="primary" icon="plus" @click="createTask">创建任务</el-button>
      </div>
    </div>

    <data-table
      ref="tableRef"
      :columns="[]"
      :data="fetchTasks"
      row-key="tid"
      height="100%"
      flex-type="flex-[1_1_200px]"
      :show-search="false"
      :show-reset="false"
      :page-sizes="[10, 20, 50, 100]"
      class="task-table"
      empty-text="暂无接入任务"
    >
      <el-table-column type="expand" width="44">
        <template #default="{ row }">
          <div class="sync-detail-panel">
            <div class="sync-detail-card">
              <div class="sync-detail-title">
                <div>
                  <strong>表同步情况</strong>
                  <span>按表查看来源、目标、字段映射与最近运行结果</span>
                </div>
                <el-tag size="small" effect="plain">{{ row.syncTables?.length || 0 }} 张表</el-tag>
              </div>

              <div v-if="(row.syncTables || []).length" class="sync-flow-list">
                <article
                  v-for="(item, index) in row.syncTables"
                  :key="item.tid || item.id || index"
                  class="sync-flow-card"
                >
                  <div class="sync-flow-head">
                    <span class="sync-flow-index">{{ index + 1 }}</span>
                    <strong>{{ tableDisplayName(item) }}</strong>
                    <el-tag :type="statusTagType(item.syncStatus ?? item.taskStatus)" size="small" effect="light">
                      {{ item.syncStatusText || statusText(item.syncStatus ?? item.taskStatus) }}
                    </el-tag>
                  </div>

                  <div class="sync-route">
                    <div class="sync-node sync-node-source">
                      <span class="sync-node-label">来源表</span>
                      <strong :title="item.sourceTableName || '-'">{{ item.sourceTableName || "-" }}</strong>
                      <span :title="item.sourceDbName || ''">{{ item.sourceDbName || "未配置来源库" }}</span>
                    </div>
                    <div class="sync-arrow">
                      <Icon icon="el-icon-right" />
                      <span>字段映射</span>
                    </div>
                    <div class="sync-node sync-node-target">
                      <span class="sync-node-label">目标表</span>
                      <strong :title="item.targetTableName || '-'">{{ item.targetTableName || "-" }}</strong>
                      <span :title="item.targetDbName || ''">{{ item.targetDbName || "未配置目标库" }}</span>
                    </div>
                  </div>

                  <div class="sync-meta-grid">
                    <div>
                      <label>主键</label>
                      <span>{{ item.primaryKey || "-" }}</span>
                    </div>
                    <div>
                      <label>增量字段</label>
                      <span>{{ item.incrementKey || item.incrementField || "-" }}</span>
                    </div>
                    <div>
                      <label>最新运行时间</label>
                      <span>{{ formatDateTime(item.lastRunning || item.latestRunTime) }}</span>
                    </div>
                    <div>
                      <label>结束时间</label>
                      <span>{{ formatDateTime(item.endRunning || item.endTime) }}</span>
                    </div>
                    <div>
                      <label>执行耗时</label>
                      <span>{{ item.scheduleRunning || item.duration || "-" }}</span>
                    </div>
                    <div>
                      <label>运行结果</label>
                      <span>{{ item.monitorMsg || item.resultMsg || item.latestResult || "-" }}</span>
                    </div>
                  </div>
                </article>
              </div>
              <el-empty v-else description="该任务暂无表同步明细" :image-size="82" />
            </div>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="序号" type="index" width="70" align="center" />
      <el-table-column prop="taskName" label="任务名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="taskStatus" label="当前状态" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.taskStatus)" effect="light">
            {{ statusText(row.taskStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastRunning" label="最新运行时间" width="175">
        <template #default="{ row }">{{ formatDateTime(row.lastRunning || row.latestRunTime) }}</template>
      </el-table-column>
      <el-table-column prop="endRunning" label="结束时间" width="175">
        <template #default="{ row }">{{ formatDateTime(row.endRunning || row.endTime) }}</template>
      </el-table-column>
      <el-table-column prop="scheduleRunning" label="执行时间" width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.scheduleRunning || "-" }}</template>
      </el-table-column>
      <el-table-column prop="scheduleFrequency" label="调度频率" width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.scheduleFrequency || "-" }}</template>
      </el-table-column>
      <el-table-column prop="sourceDbName" label="来源数据库" min-width="150" show-overflow-tooltip />
      <el-table-column prop="sourceTableSummary" label="来源数据表" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ row.sourceTableSummary || row.sourceTableName || "-" }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="targetDbName" label="目标数据库" min-width="150" show-overflow-tooltip />
      <el-table-column prop="targetTableSummary" label="目标数据表" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ row.targetTableSummary || row.targetTableName || "-" }}</span>
        </template>
      </el-table-column>
      <el-table-column
        prop="operation"
        label="操作"
        width="190"
        fixed="right"
        align="center"
        class-name="operation-column"
        :show-overflow-tooltip="false"
      >
        <template #default="{ row }">
          <div class="operation-buttons task-actions">
            <el-button
              v-if="[1].includes(Number(row.taskStatus))"
              type="primary"
              link
              :loading="isOperationLoading(row, 'stop')"
              :disabled="isRowOperating(row)"
              @click="handleOperation('stop', row)"
            >
              停止
            </el-button>
            <el-button
              v-if="[0, 2].includes(Number(row.taskStatus))"
              type="primary"
              link
              :loading="isOperationLoading(row, 'run')"
              :disabled="isRowOperating(row)"
              @click="handleOperation('run', row)"
            >
              运行
            </el-button>
            <el-button
              type="primary"
              link
              :disabled="isRowOperating(row)"
              @click="handleOperation('canvas', row)"
            >
              流程画布
            </el-button>
            <el-dropdown
              trigger="click"
              :disabled="isRowOperating(row)"
              @command="(command) => handleOperation(command, row)"
            >
              <el-button type="primary" link :disabled="isRowOperating(row)">
                更多
                <Icon icon="el-icon-arrow-down" class="ml-1" />
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="delay">延时监控</el-dropdown-item>
                  <el-dropdown-item command="log">异常日志</el-dropdown-item>
                  <el-dropdown-item
                    v-if="[0, 2].includes(Number(row.taskStatus))"
                    command="delete"
                    class="danger-item"
                  >
                    删除任务
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </template>
      </el-table-column>
    </data-table>

    <el-drawer
      v-model="deleteDrawerVisible"
      title="建表情况"
      direction="rtl"
      size="680px"
      destroy-on-close
      :close-on-click-modal="!deletingTargets"
      :close-on-press-escape="!deletingTargets"
      :show-close="!deletingTargets"
    >
      <div v-loading="deletingTargets" class="task-target-drawer">
        <el-descriptions :column="1" border class="mb-4">
          <el-descriptions-item label="来源表">
            {{ deleteDrawerRow?.sourceTableName || (deleteDrawerRow?.sourceTableId ? '当前来源表' : '独立创建的任务（无来源表关联）') }}
          </el-descriptions-item>
          <el-descriptions-item label="接入任务">{{ deleteDrawerRow?.taskName || '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-alert
          v-if="deleteDrawerRow && !deleteDrawerRow.sourceTableId"
          title="此任务没有来源表关联，无法安全确认目标表归属；只支持删除任务和关联流程，不会删除物理表。"
          type="warning"
          :closable="false"
          class="mb-4"
        />
        <el-table :data="deleteDrawerTargets" border size="small" empty-text="暂未找到与此任务关联的目标表">
          <el-table-column type="index" label="序号" width="64" />
          <el-table-column prop="targetDbName" label="目标数据源" min-width="160" show-overflow-tooltip>
            <template #default="{ row: target }">{{ target.targetDbName || target.targetDbId || '-' }}</template>
          </el-table-column>
          <el-table-column label="目标表" min-width="170" show-overflow-tooltip>
            <template #default="{ row: target }">
              {{ target.targetTableName || target.targetTableDisplayName || '-' }}
              <el-tag v-if="!target.targetTableName" size="small" type="warning">待确认真实表名</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="94" align="center">
            <template #default><el-tag size="small" type="success">已创建</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="112" align="center">
            <template #default="{ row: target }">
              <el-button link type="danger" :disabled="deletingTargets || !canDeleteTarget(target)" @click="deleteSingleTarget(target)">删除目标表</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <div class="task-target-drawer-footer">
          <el-button type="danger" plain :disabled="deletingTargets" @click="deleteCurrentFlow">删除流程</el-button>
          <el-button v-if="deleteDrawerTargets.some(canDeleteTarget)" type="danger" :loading="deletingTargets" @click="deleteAllTargets">删除全部目标表和流程</el-button>
          <el-button :disabled="deletingTargets" @click="deleteDrawerVisible = false">关闭</el-button>
        </div>
      </template>
    </el-drawer>

    <schedule-setting
      v-if="scheduleVisible"
      :id="id"
      :access-task-id="accessTaskId"
      @close="closeDesigner"
    />

    <delay-monitor
      v-if="delayMonitorVisible"
      v-model="delayMonitorVisible"
      :task-id="accessTaskId"
      :task-name="taskName"
    />

    <error-log-panel v-model="errorLogVisible" :pipeline-id="pipelineId" :task-name="taskName" />
        </section>
      </el-splitter-panel>
    </el-splitter>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";

const scheduleVisible = ref(false);
const id = ref("");
const accessTaskId = ref("");
const delayMonitorVisible = ref(false);
const taskName = ref("");
const errorLogVisible = ref(false);
const pipelineId = ref("");
const activeTab = ref("all");
const searchVal = ref("");
const tableRef = ref();
const operationState = ref({});
const deleteDrawerVisible = ref(false);
const deleteDrawerRow = ref(null);
const deletingTargets = ref(false);
const cleaningHistory = ref(false);
const deleteDrawerTargets = computed(() => {
  const row = deleteDrawerRow.value;
  if (!row) return [];
  const candidates = [...(Array.isArray(row.syncTables) ? row.syncTables : []), row];
  const seen = new Set();
  return candidates.filter((item) => {
    const id = String(item?.targetTableId || '').trim();
    if (!id || seen.has(id)) return false;
    seen.add(id);
    return true;
  }).map((item) => ({
    targetTableId: item.targetTableId,
    targetDbId: item.targetDbId || row.targetDbId || '',
    targetDbName: item.targetDbName || row.targetDbName || '',
    // /ods/task/page 的 targetTableName 是中文展示名；删除必须使用元数据中的物理表名。
    targetTableName: item.targetPhysicalTableName || row.targetPhysicalTableName || '',
    targetTableDisplayName: item.targetTableName || row.targetTableName || '',
  }));
});
const nodeTreeLoading = ref(false);
const nodeTreeData = ref([]);
const nodeSearchKeyword = ref("");
const selectedNetworkCode = ref("");
const selectedNodeId = ref("");

const nodeCount = computed(() =>
  nodeTreeData.value.reduce((total, network) => total + (network.children?.length || 0), 0)
);

const visibleNodeGroups = computed(() => {
  const keyword = nodeSearchKeyword.value.trim().toLowerCase();
  if (!keyword) return nodeTreeData.value;
  return nodeTreeData.value
    .map((network) => ({
      ...network,
      children: network.children.filter((node) =>
        `${network.label} ${node.label} ${node.code || ""}`.toLowerCase().includes(keyword)
      ),
    }))
    .filter((network) => network.children.length);
});

const tabs = [
  { label: "全部", value: "all" },
  { label: "运行中", value: "1" },
  { label: "未启用", value: "0" },
  { label: "运行异常", value: "2" },
];

const normalizeListData = (response) => {
  const data = response?.data || response || {};
  return {
    list: Array.isArray(data.list) ? data.list : [],
    total: Number(data.total || 0),
  };
};

const fetchTasks = async ({ pageNo = 1, pageSize = 20 } = {}) => {
  const response = await $common.post("/ods/task/page", {
    taskStatus: activeTab.value === "all" ? "all" : activeTab.value,
    taskName: searchVal.value,
    nifiNetworkCode: selectedNetworkCode.value,
    nifiNodeId: selectedNodeId.value,
    pageNum: pageNo,
    pageSize,
  });
  const data = normalizeListData(response);
  return {
    list: data.list.map((item) => {
      const syncTables = Array.isArray(item.syncTables) ? item.syncTables : [];
      return {
        ...item,
        syncTables,
        sourceTableSummary:
          syncTables.length > 1 ? `${syncTables.length} 张表` : item.sourceTableName,
        targetTableSummary:
          syncTables.length > 1 ? `${syncTables.length} 张表` : item.targetTableName,
      };
    }),
    total: data.total,
  };
};

const loadNodeTree = async () => {
  nodeTreeLoading.value = true;
  try {
    const response = await $common.post("/ods/nifi-node/access-options", {});
    const payload = response?.data || response || {};
    const networks = Array.isArray(payload.networks) ? payload.networks : [];
    const nodes = Array.isArray(payload.list) ? payload.list : [];
    nodeTreeData.value = networks
      .map((network) => {
        const value = String(network.value || network.dictCode || network.code || "").trim();
        const label = network.label || network.dictName || network.name || value;
        const children = nodes
          .filter((node) => String(node.networkCode || node.network_code || "").trim() === value)
          .map((node) => ({
            label: node.nodeName || node.node_name || node.name || node.nodeCode || node.node_code,
            value: String(node.nodeId || node.tid || node.id || node.value || "").trim(),
            type: "node",
            networkCode: value,
            code: node.nodeCode || node.node_code || "",
            enabled: node.enabled !== false && Number(node.enabled ?? 1) !== 0,
            isDefault: node.isDefault === true || Number(node.isDefault ?? node.is_default ?? 0) === 1,
          }))
          .filter((node) => node.label && node.value);
        return { label, value, icon: String(network.icon || "").trim(), type: "network", children };
      })
      .filter((network) => network.label && network.value && network.children.length);
  } catch (error) {
    nodeTreeData.value = [];
    ElMessage.error("数据接入节点加载失败，请稍后重试");
  } finally {
    nodeTreeLoading.value = false;
  }
};

const networkTone = (network) => {
  const value = String(network.value || "").toUpperCase();
  if (value.includes("HLW") || network.label.includes("互联网")) return "tone-internet";
  if (value.includes("ZWW") || network.label.includes("专网")) return "tone-private";
  return "tone-police";
};

const networkIcon = (network) => network.icon || (networkTone(network) === "tone-internet"
  ? "menu-earth"
  : networkTone(network) === "tone-private" ? "database-network" : "safe");

const isNetworkSelected = (network) => selectedNetworkCode.value === network.value;

const selectNetwork = (network) => {
  selectedNetworkCode.value = network.value || "";
  selectedNodeId.value = "";
  refreshTable();
};

const selectNode = (network, node) => {
  selectedNetworkCode.value = network.value || "";
  selectedNodeId.value = node.value || "";
  refreshTable();
};

const refreshTable = async () => {
  return tableRef.value?.refresh?.(true);
};

const cleanupMonitorHistory = async () => {
  if (cleaningHistory.value) return;
  try {
    const { value } = await ElMessageBox.prompt(
      "输入保留天数，仅清理更早的监控历史快照。任务配置和最新监控状态不会删除。",
      "清理监控历史",
      {
        inputValue: "30",
        inputPattern: /^(?:[1-9]\d{0,2}|1000)$/,
        inputErrorMessage: "请输入 1–1000 天",
        confirmButtonText: "预览清理范围",
      }
    );
    const retentionDays = Number(value);
    cleaningHistory.value = true;
    const previewResponse = await $common.post("/ods/task/monitorSnapCleanup", {
      retentionDays,
      dryRun: true,
    });
    const preview = previewResponse?.data || previewResponse || {};
    const count = Number(preview.count || 0);
    if (!count) {
      ElMessage.info("当前没有需要清理的监控历史快照");
      return;
    }
    await ElMessageBox.confirm(
      `将清理 ${preview.cutoff} 之前的 ${count} 条监控历史快照。此操作不可撤销，确认清理吗？`,
      "确认清理监控历史",
      { type: "warning", confirmButtonText: "确认清理", cancelButtonText: "取消" }
    );
    const resultResponse = await $common.post("/ods/task/monitorSnapCleanup", {
      retentionDays,
      dryRun: false,
      confirm: true,
    });
    const result = resultResponse?.data || resultResponse || {};
    ElMessage.success(`已清理 ${Number(result.deleted || 0)} 条监控历史快照`);
  } catch (error) {
    if (error === "cancel" || error === "close" || error?.action === "cancel") return;
    ElMessage.error(operationErrorMessage(error, "清理监控历史失败"));
  } finally {
    cleaningHistory.value = false;
  }
};

const createTask = () => {
  scheduleVisible.value = true;
  id.value = "";
  accessTaskId.value = "";
};

const handleTabChange = () => {
  refreshTable();
};

const formatDateTime = (cellValue) => {
  if (cellValue == null || cellValue === "") return "-";
  const date = new Date(
    typeof cellValue === "string" && /^\d+$/.test(cellValue) ? Number(cellValue) : cellValue
  );
  if (Number.isNaN(date.getTime())) return String(cellValue);
  const pad = (value) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours()
  )}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
};

const statusText = (status) => {
  const value = Number(status);
  if (value === 1) return "运行中";
  if (value === 2) return "运行异常";
  return "未启用";
};

const statusTagType = (status) => {
  const value = Number(status);
  if (value === 1) return "success";
  if (value === 2) return "danger";
  return "info";
};

const tableDisplayName = (item) => {
  return item.sourceTableComment || item.tableComment || item.sourceTableName || item.targetTableName || "同步表";
};

const operationKey = (row) => String(row?.tid || row?.pipelineId || "");

const isRowOperating = (row) => Boolean(operationState.value[operationKey(row)]);

const isOperationLoading = (row, type) => operationState.value[operationKey(row)] === type;

const setOperationState = (row, type) => {
  const key = operationKey(row);
  if (!key) return;
  operationState.value = {
    ...operationState.value,
    [key]: type || undefined,
  };
};

const requireOperationId = (value, message) => {
  if (value == null || String(value).trim() === "") {
    throw new Error(message);
  }
  return String(value).trim();
};

const operationErrorMessage = (error, fallback) => {
  return (
    error?.userMessage ||
    error?.message ||
    error?.msg ||
    error?.response?.data?.message ||
    error?.response?.data?.msg ||
    error?.response?.data?.error ||
    fallback
  );
};

const handleOperation = async (type, row) => {
  if (isRowOperating(row)) return;
  try {
    switch (type) {
      case "stop": {
        const currentPipelineId = requireOperationId(
          row.pipelineId,
          "当前任务没有关联流程，无法停止"
        );
        setOperationState(row, "stop");
        await $request.post(
          `/nifi/api/pipelines/${currentPipelineId}/stop`,
          {},
          { responsePolicy: "raw", errorPolicy: "silent", timeout: 120 * 1000 }
        );
        ElMessage.success("任务已停止");
        await refreshTable();
        break;
      }
      case "run": {
        const currentPipelineId = requireOperationId(
          row.pipelineId,
          "当前任务没有关联流程，请先打开流程画布完成配置"
        );
        setOperationState(row, "run");
        await $request.post(
          `/nifi/api/pipelines/${currentPipelineId}/start`,
          {},
          { responsePolicy: "raw", errorPolicy: "silent", timeout: 120 * 1000 }
        );
        ElMessage.success("任务已启动");
        await refreshTable();
        break;
      }
      case "canvas":
        scheduleVisible.value = true;
        id.value = row.dataCatalogId;
        accessTaskId.value = row.tid;
        break;
      case "delay":
        accessTaskId.value = requireOperationId(row.tid, "缺少任务编号，无法查看延时监控");
        taskName.value = row.taskName || "";
        delayMonitorVisible.value = true;
        break;
      case "log":
        pipelineId.value = requireOperationId(
          row.pipelineId,
          "当前任务没有关联流程，暂无异常日志"
        );
        taskName.value = row.taskName || "";
        errorLogVisible.value = true;
        break;
      case "delete": {
        requireOperationId(row.tid, "缺少任务编号，无法删除");
        deleteDrawerRow.value = row;
        deleteDrawerVisible.value = true;
        break;
      }
    }
  } catch (error) {
    if (error === "cancel" || error === "close" || error?.action === "cancel") return;
    const labels = {
      run: "启动任务失败",
      stop: "停止任务失败",
      delete: "删除任务失败",
      delay: "打开延时监控失败",
      log: "打开异常日志失败",
      canvas: "打开流程画布失败",
    };
    ElMessage.error(operationErrorMessage(error, labels[type] || "操作失败"));
  } finally {
    if (["run", "stop"].includes(type)) {
      setOperationState(row, "");
    }
  }
};

const canDeleteTarget = (target) => Boolean(
  deleteDrawerRow.value?.sourceTableId && target?.targetTableId
  && target?.targetDbId && target?.targetTableName
);

const deleteCurrentFlow = async () => {
  const row = deleteDrawerRow.value;
  if (!row || deletingTargets.value) return;
  const sourceTableId = String(row.sourceTableId || '').trim();
  let deleteBySource = false;
  let taskCount = 1;
  try {
    if (row.recordSource !== 'pipeline' && sourceTableId) {
      const previewResponse = await $common.post('/ods/dataAggReset', {
        sourceTableId, deleteTargetTable: false, dryRun: true,
      }, { _hiddenErrorMsg: true });
      const preview = previewResponse?.data || previewResponse || {};
      if (String(preview.sourceTableId || '') !== sourceTableId) {
        throw new Error('无法确认来源表与待删除任务的关联，请刷新后重试');
      }
      taskCount = Number(preview.taskCount || 0);
      deleteBySource = taskCount > 0;
    }
    await ElMessageBox.confirm(
      `${deleteBySource && taskCount > 1 ? `该来源表关联 ${taskCount} 个接入任务，将一并删除。` : '将删除当前接入任务和关联流程。'}已创建的目标表及其数据会保留，可在“建表情况”中单独删除。确认继续吗？`,
      '删除任务', { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' },
    );
    deletingTargets.value = true;
    if (row.recordSource === 'pipeline') {
      const pipelineId = requireOperationId(row.pipelineId, '缺少流程编号，无法删除');
      await $request.post(`/nifi/api/pipelines/delete/${pipelineId}`, {},
        { responsePolicy: 'raw', errorPolicy: 'silent', timeout: 120 * 1000 });
    } else if (deleteBySource) {
      await $common.post('/ods/dataAggReset', { sourceTableId, deleteTargetTable: false },
        { _hiddenErrorMsg: true }, 120 * 1000);
    } else {
      await $common.post('/ods/task/deleteById', { tid: row.tid },
        { _hiddenErrorMsg: true }, 120 * 1000);
    }
    deleteDrawerVisible.value = false;
    ElMessage.success('已删除任务和关联流程，目标表及数据已保留');
    await refreshTable();
  } catch (error) {
    if (error !== 'cancel' && error !== 'close' && error?.action !== 'cancel') {
      ElMessage.error(operationErrorMessage(error, '删除任务失败'));
    }
  } finally {
    deletingTargets.value = false;
  }
};

const deleteTargetPayload = (target) => ({
  sourceTableId: deleteDrawerRow.value.sourceTableId,
  targetTableId: target.targetTableId,
  targetDbId: target.targetDbId,
  targetTableName: target.targetTableName,
  deleteTargetTable: true,
});

const deleteSingleTarget = async (target) => {
  if (!canDeleteTarget(target) || deletingTargets.value) return;
  try {
    const payload = deleteTargetPayload(target);
    const previewResponse = await $common.post('/ods/dataAggReset',
      { ...payload, dryRun: true }, { _hiddenErrorMsg: true });
    const preview = previewResponse?.data || previewResponse || {};
    if (String(preview.targetTableId || '') !== String(target.targetTableId)) {
      throw new Error('目标表归属校验失败，已停止删除');
    }
    await ElMessageBox.confirm(
      `将删除目标物理表“${target.targetTableName}”及其元数据，并移除当前接入流程。该操作不可恢复，确认继续吗？`,
      '删除目标表并移除流程',
      { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' },
    );
    deletingTargets.value = true;
    const result = await $common.post('/ods/dataAggReset', payload,
      { _hiddenErrorMsg: true }, 120 * 1000);
    const outcome = result?.data || result || {};
    if (outcome.targetPhysicalCleanupSkipped && preview.targetPhysicalExists) {
      throw new Error('目标物理表未确认删除，请检查目标库状态');
    }
    deleteDrawerVisible.value = false;
    ElMessage.success('已删除目标表并移除流程');
    await refreshTable();
  } catch (error) {
    if (error !== 'cancel' && error !== 'close' && error?.action !== 'cancel') {
      ElMessage.error(operationErrorMessage(error, '删除目标表失败'));
    }
  } finally {
    deletingTargets.value = false;
  }
};

const deleteAllTargets = async () => {
  const targets = deleteDrawerTargets.value.filter(canDeleteTarget);
  if (!targets.length || deletingTargets.value) return;
  try {
    const previews = [];
    for (const target of targets) {
      const response = await $common.post('/ods/dataAggReset',
        { ...deleteTargetPayload(target), dryRun: true }, { _hiddenErrorMsg: true });
      const preview = response?.data || response || {};
      if (String(preview.targetTableId || '') !== String(target.targetTableId)) {
        throw new Error(`目标表“${target.targetTableName}”归属校验失败，已停止删除`);
      }
      previews.push(preview);
    }
    await ElMessageBox.confirm(
      `将删除 ${targets.length} 个目标物理表、对应元数据和当前接入流程。该操作不可恢复，确认继续吗？`,
      '删除全部目标表和流程',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    );
    deletingTargets.value = true;
    for (const target of targets) {
      await $common.post('/ods/dataAggReset', deleteTargetPayload(target),
        { _hiddenErrorMsg: true }, 120 * 1000);
    }
    deleteDrawerVisible.value = false;
    ElMessage.success('已删除全部目标表和流程');
    await refreshTable();
  } catch (error) {
    if (error !== 'cancel' && error !== 'close' && error?.action !== 'cancel') {
      ElMessage.error(operationErrorMessage(error, '删除全部目标表失败'));
    }
  } finally {
    deletingTargets.value = false;
  }
};

const handleMessage = (event) => {
  const { type } = event.data || {};
  if (type === "NIFI_APP_CLOSE") {
    closeDesigner();
  }
};

const closeDesigner = () => {
  scheduleVisible.value = false;
  refreshTable();
};

onMounted(async () => {
  window.addEventListener("message", handleMessage);
  await loadNodeTree();
  refreshTable();
});

onUnmounted(() => {
  window.removeEventListener("message", handleMessage);
});
</script>

<style scoped>
.task-target-drawer-footer {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
}

.task-target-drawer-footer .el-button + .el-button {
  margin-left: 0;
}

.access-monitor-page {
  height: 100%;
  min-width: 0;
  padding: 20px;
  background: #fff;
  border-radius: 10px;
  overflow: hidden;
}

.access-monitor-splitter {
  height: 100%;
  min-width: 0;
}

.access-node-tree-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 4px 16px 4px 0;
  overflow: hidden;
  border-right: 1px solid #e8eef7;
}

.access-node-tree-panel__header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding: 4px 8px 10px;
}

.access-node-tree-panel__header h3 {
  margin: 0;
  color: #1f2937;
  font-size: 16px;
  line-height: 24px;
}

.access-node-tree-panel__header span {
  color: #98a2b3;
  font-size: 12px;
}

.access-node-search {
  margin: 0 8px 12px;
  width: calc(100% - 16px);
}

.access-node-search :deep(.el-input__wrapper) {
  min-height: 34px;
  background: #fff;
  box-shadow: 0 0 0 1px #e3e9f2 inset;
}

.access-node-groups {
  min-height: 0;
  flex: 1;
  padding: 0 8px 4px;
}

.access-node-group-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.access-network-card {
  overflow: hidden;
  border: 1px solid #e8edf5;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  transition: border-color .2s ease, box-shadow .2s ease, background .2s ease;
}

.access-network-card:hover {
  border-color: #b8d3ff;
  box-shadow: 0 4px 12px rgba(31, 91, 182, .08);
}

.access-network-card.is-selected {
  border-color: #85b6ff;
  box-shadow: inset 3px 0 0 #1677ff, 0 4px 12px rgba(22, 119, 255, .08);
}

.access-network-card__head {
  display: flex;
  align-items: center;
  min-height: 43px;
  gap: 8px;
  padding: 8px 10px;
  color: #1f2937;
  background: #fbfcfe;
}

.access-network-card.is-selected .access-network-card__head {
  background: #f2f7ff;
}

.access-network-card__head strong {
  overflow: hidden;
  font-size: 14px;
  line-height: 20px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.access-network-card__icon {
  display: inline-flex;
  width: 26px;
  height: 26px;
  align-items: center;
  justify-content: center;
  border-radius: 7px;
  color: #1677ff;
  background: #eaf3ff;
}

.access-network-card__count {
  flex: none;
  margin-left: auto;
  color: #6994d5;
  font-size: 12px;
}

.tone-internet .access-network-card__icon {
  color: #12a874;
  background: #eaf9f2;
}

.tone-internet .access-network-card__count { color: #168b63; }

.tone-private .access-network-card__icon {
  color: #1b67f8;
  background: #edf4ff;
}

.tone-private .access-network-card__count { color: #2872da; }

.access-node-row {
  display: flex;
  width: 100%;
  min-height: 40px;
  align-items: center;
  gap: 7px;
  padding: 8px 10px 8px 15px;
  overflow: hidden;
  color: #526174;
  border: 0;
  border-top: 1px solid #f0f3f7;
  background: #fff;
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.access-node-row:hover,
.access-node-row.is-selected {
  color: #145fc7;
  background: #f5f9ff;
}

.access-node-row__name {
  min-width: 0;
  overflow: hidden;
  color: inherit;
  font-size: 13px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.access-node-row__state {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 4px;
  margin-left: auto;
  color: #18a66e;
  font-size: 11px;
}

.access-node-row__state i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}

.access-node-row__state.is-offline { color: #98a2b3; }

.access-node-row :deep(.el-tag) {
  flex: none;
  height: 20px;
  padding: 0 4px;
  font-size: 11px;
}

.access-monitor-content {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-width: 0;
  padding-left: 16px;
  overflow: hidden;
}

.access-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.toolbar-actions {
  display: flex;
  min-width: 0;
  flex: 1 1 auto;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}

.task-search {
  width: clamp(150px, 20vw, 300px);
  min-width: 0;
}

.search-icon {
  color: #1677ff;
  cursor: pointer;
}

.task-table {
  min-width: 0;
  flex: 1;
}

.task-table :deep(.operation-column .cell) {
  overflow: visible;
}

.task-actions {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  width: 100%;
  white-space: nowrap;
}

.task-actions :deep(.el-button) {
  height: auto;
  min-height: 28px;
  padding: 0 2px;
  margin-left: 0;
  font-size: 14px;
  font-weight: 400;
  line-height: 28px;
}

.task-actions :deep(.el-dropdown) {
  display: inline-flex;
  align-items: center;
  line-height: 1;
}

@media (max-width: 900px) {
  .access-monitor-page {
    padding: 12px;
  }

  .access-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .toolbar-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .task-search {
    flex: 1;
    width: min(100%, 360px);
  }
}

@media (max-width: 1180px) {
  .access-toolbar { gap: 10px; }
  .toolbar-actions { gap: 8px; }
  .task-search { width: clamp(132px, 18vw, 220px); }
}

.sync-detail-panel {
  padding: 14px 0 16px 58px;
  background: linear-gradient(180deg, #f7fbff 0%, #ffffff 100%);
}

.sync-detail-card {
  width: min(920px, calc(100vw - 220px));
  padding: 16px;
  background: #fff;
  border: 1px solid #dbeafe;
  border-radius: 12px;
  box-shadow: 0 8px 22px rgba(20, 86, 180, 0.08);
}

.sync-detail-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
  color: #1f2937;
}

.sync-detail-title strong {
  display: block;
  margin-bottom: 4px;
  font-size: 15px;
}

.sync-detail-title span {
  font-size: 13px;
  color: #667085;
}

.sync-flow-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sync-flow-card {
  padding: 14px;
  background: #f8fbff;
  border: 1px solid #e6efff;
  border-radius: 10px;
}

.sync-flow-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  min-width: 0;
}

.sync-flow-head strong {
  overflow: hidden;
  color: #1d2939;
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sync-flow-index {
  width: 22px;
  height: 22px;
  flex: 0 0 22px;
  color: #1677ff;
  font-size: 12px;
  font-weight: 700;
  line-height: 22px;
  text-align: center;
  background: #eef6ff;
  border-radius: 50%;
}

.sync-route {
  display: grid;
  grid-template-columns: minmax(190px, 1fr) 92px minmax(190px, 1fr);
  align-items: stretch;
  gap: 12px;
}

.sync-node {
  min-width: 0;
  padding: 10px 12px;
  border-radius: 10px;
}

.sync-node-source {
  background: #f2f8ff;
}

.sync-node-target {
  background: #f4fbf7;
}

.sync-node-label {
  display: block;
  margin-bottom: 6px;
  color: #7a8aa0;
  font-size: 12px;
}

.sync-node strong {
  display: block;
  overflow: hidden;
  color: #1f2937;
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sync-node span:last-child {
  display: block;
  overflow: hidden;
  margin-top: 4px;
  color: #6b7280;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sync-arrow {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #1677ff;
  font-size: 12px;
}

.sync-meta-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px 12px;
  margin-top: 12px;
}

.sync-meta-grid label {
  display: block;
  margin-bottom: 2px;
  color: #8a96a8;
  font-size: 12px;
}

.sync-meta-grid span {
  display: block;
  overflow: hidden;
  color: #344054;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 1280px) {
  .sync-detail-card {
    width: min(820px, calc(100vw - 180px));
  }

  .sync-route {
    grid-template-columns: minmax(180px, 1fr);
  }

  .sync-arrow {
    flex-direction: row;
  }

  .sync-meta-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.danger-item {
  color: #f56c6c;
}
</style>




