<template>
  <div class="reconcile-page">
    <header class="page-toolbar">
      <el-segmented v-model="activeView" :options="viewOptions" @change="refreshActive" />
      <div class="toolbar-actions">
        <el-input
          v-model="keyword"
          clearable
          class="search-input"
          placeholder="搜索策略、任务或表名"
          @clear="refreshActive"
          @keyup.enter="refreshActive"
        >
          <template #suffix>
            <Icon icon="el-icon-Search" @click="refreshActive" />
          </template>
        </el-input>
        <el-button
          v-if="activeView === 'policy'"
          type="primary"
          icon="plus"
          @click="openPolicy()"
        >
          新建对账策略
        </el-button>
        <el-button icon="refresh" circle title="刷新" @click="refreshActive" />
      </div>
    </header>

    <section v-if="activeView === 'run'" class="status-strip">
      <button
        v-for="item in runFilters"
        :key="item.value"
        type="button"
        :class="['status-filter', { active: runStatus === item.value }]"
        @click="selectRunStatus(item.value)"
      >
        <span class="status-filter__label">{{ item.label }}</span>
        <strong>{{ item.count }}</strong>
      </button>
    </section>

    <data-table
      v-if="activeView === 'policy'"
      ref="policyTableRef"
      :columns="[]"
      :data="fetchPolicies"
      row-key="tid"
      height="100%"
      flex-type="flex-[1_1_200px]"
      :show-search="false"
      :show-reset="false"
      :page-sizes="[10, 20, 50, 100]"
      empty-text="暂无对账策略"
    >
      <el-table-column label="策略名称" min-width="210">
        <template #default="{ row }">
          <button class="name-link" type="button" @click="openPolicy(row)">
            {{ row.policy_name }}
          </button>
          <span class="subline">{{ row.task_name || "-" }}</span>
        </template>
      </el-table-column>
      <el-table-column label="来源与目标" min-width="260">
        <template #default="{ row }">
          <div class="route-cell">
            <span :title="row.source_table_name">{{ row.source_table_name || "-" }}</span>
            <Icon icon="el-icon-Right" />
            <span :title="row.target_table_name">{{ row.target_table_name || "-" }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="比对方式" width="110" align="center">
        <template #default="{ row }">{{ compareModeText(row.compare_mode) }}</template>
      </el-table-column>
      <el-table-column label="触发方式" width="120" align="center">
        <template #default="{ row }">{{ triggerModeText(row.trigger_mode) }}</template>
      </el-table-column>
      <el-table-column label="最近结果" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="runStatusType(row.last_run_status)" effect="light">
            {{ runStatusText(row.last_run_status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="一致率" width="110" align="right">
        <template #default="{ row }">
          {{ row.consistency_rate == null ? "-" : `${Number(row.consistency_rate).toFixed(2)}%` }}
        </template>
      </el-table-column>
      <el-table-column label="启用" width="90" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="Number(row.status) === 1"
            @change="(value) => togglePolicy(row, value)"
          />
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="230"
        fixed="right"
        align="center"
        :show-overflow-tooltip="false"
      >
        <template #default="{ row }">
          <div class="operation-buttons">
            <el-button type="primary" link @click="startRun(row)">手动对账</el-button>
            <el-button type="primary" link @click="precheckPolicy(row)">预检</el-button>
            <el-button type="primary" link @click="openPolicy(row)">编辑</el-button>
          </div>
        </template>
      </el-table-column>
    </data-table>

    <data-table
      v-else
      ref="runTableRef"
      :columns="[]"
      :data="fetchRuns"
      row-key="tid"
      height="100%"
      flex-type="flex-[1_1_200px]"
      :show-search="false"
      :show-reset="false"
      :page-sizes="[10, 20, 50, 100]"
      empty-text="暂无对账实例"
    >
      <el-table-column label="策略 / 实例" min-width="225">
        <template #default="{ row }">
          <button class="name-link" type="button" @click="openRun(row)">
            {{ row.policy_name }}
          </button>
          <span class="subline mono">{{ row.tid }}</span>
        </template>
      </el-table-column>
      <el-table-column label="触发方式" width="110" align="center">
        <template #default="{ row }">{{ triggerModeText(row.trigger_type) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="runStatusType(row.status)" effect="light">
            {{ runStatusText(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="来源数据" prop="source_count" width="120" align="right" />
      <el-table-column label="目标数据" prop="target_count" width="120" align="right" />
      <el-table-column label="差异" width="100" align="right">
        <template #default="{ row }">
          <span :class="{ 'danger-text': Number(row.diff_count) > 0 }">
            {{ formatNumber(row.diff_count) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="一致率" width="105" align="right">
        <template #default="{ row }">
          {{ row.consistency_rate == null ? "-" : `${Number(row.consistency_rate).toFixed(2)}%` }}
        </template>
      </el-table-column>
      <el-table-column label="开始时间" width="175">
        <template #default="{ row }">{{ formatDateTime(row.started_at || row.created_time) }}</template>
      </el-table-column>
      <el-table-column label="结束时间" width="175">
        <template #default="{ row }">{{ formatDateTime(row.finished_at) }}</template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="145"
        fixed="right"
        align="center"
        :show-overflow-tooltip="false"
      >
        <template #default="{ row }">
          <div class="operation-buttons">
            <el-button type="primary" link @click="openRun(row)">详情</el-button>
            <el-button
              v-if="['PENDING', 'INITIALIZING', 'RUNNING'].includes(row.status)"
              type="danger"
              link
              @click="cancelRun(row)"
            >
              取消
            </el-button>
          </div>
        </template>
      </el-table-column>
    </data-table>

    <el-drawer
      v-model="policyVisible"
      :title="policyForm.tid ? '编辑对账策略' : '新建对账策略'"
      size="720px"
      destroy-on-close
    >
      <el-form ref="policyFormRef" :model="policyForm" :rules="policyRules" label-width="112px">
        <div class="form-section">
          <div class="section-title">业务范围</div>
          <el-form-item label="策略名称" prop="policyName">
            <el-input v-model="policyForm.policyName" maxlength="100" show-word-limit />
          </el-form-item>
          <el-form-item label="接入任务" prop="accessTaskId">
            <el-select
              v-model="policyForm.accessTaskId"
              filterable
              class="w-full"
              placeholder="选择已配置来源和目标的接入任务"
              @change="applyAccessTask"
            >
              <el-option
                v-for="task in accessTasks"
                :key="task.tid"
                :label="task.task_name"
                :value="task.tid"
              >
                <div class="task-option">
                  <strong>{{ task.task_name }}</strong>
                  <span>{{ task.source_table_name }} → {{ task.target_table_name }}</span>
                </div>
              </el-option>
            </el-select>
          </el-form-item>
          <div v-if="selectedTask" class="selected-route">
            <div>
              <label>来源端</label>
              <strong>{{ selectedTask.source_db_name }}</strong>
              <span>{{ selectedTask.source_table_name }}</span>
            </div>
            <Icon icon="el-icon-Right" />
            <div>
              <label>目标端</label>
              <strong>{{ selectedTask.target_db_name }}</strong>
              <span>{{ selectedTask.target_table_name }}</span>
            </div>
          </div>
        </div>

        <div class="form-section">
          <div class="section-title">比对规则</div>
          <div class="form-grid">
            <el-form-item label="比对深度">
              <el-radio-group v-model="policyForm.compareMode">
                <el-radio-button value="SUMMARY">总量</el-radio-button>
                <el-radio-button value="KEY">主键</el-radio-button>
                <el-radio-button value="CONTENT">内容</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="触发方式">
              <el-select v-model="policyForm.triggerMode" class="w-full">
                <el-option label="手动执行" value="MANUAL" />
                <el-option label="定时执行" value="CRON" />
                <el-option label="接入完成后执行" value="ACCESS_EVENT" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="policyForm.triggerMode === 'CRON'" label="Cron 表达式" prop="cronExpression">
              <el-input v-model="policyForm.cronExpression" placeholder="0 0 2 * * ?" />
            </el-form-item>
            <el-form-item label="来源主键" prop="sourceKeyFields">
              <el-input v-model="policyForm.sourceKeyFields" placeholder="多个字段用逗号分隔" />
            </el-form-item>
            <el-form-item label="目标主键" prop="targetKeyFields">
              <el-input v-model="policyForm.targetKeyFields" placeholder="多个字段用逗号分隔" />
            </el-form-item>
            <el-form-item label="来源增量字段">
              <el-input v-model="policyForm.sourceIncrementField" clearable />
            </el-form-item>
            <el-form-item label="目标增量字段">
              <el-input v-model="policyForm.targetIncrementField" clearable />
            </el-form-item>
          </div>
          <div class="check-row">
            <el-checkbox v-model="policyForm.trimStrings">忽略首尾空格</el-checkbox>
            <el-checkbox v-model="policyForm.ignoreCase">字符串忽略大小写</el-checkbox>
            <el-checkbox v-model="policyForm.nullEqualsEmpty">NULL 等同空字符串</el-checkbox>
          </div>
        </div>

        <div class="form-section">
          <div class="section-title">执行资源</div>
          <div class="form-grid">
            <el-form-item label="并行分桶">
              <el-input-number v-model="policyForm.bucketCount" :min="1" :max="128" />
            </el-form-item>
            <el-form-item label="流式批次">
              <el-input-number v-model="policyForm.fetchSize" :min="100" :max="10000" :step="100" />
            </el-form-item>
            <el-form-item label="差异留存上限">
              <el-input-number v-model="policyForm.diffLimit" :min="100" :max="1000000" :step="1000" />
            </el-form-item>
            <el-form-item label="数值容差">
              <el-input-number v-model="policyForm.numericTolerance" :min="0" :precision="6" />
            </el-form-item>
          </div>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="policyVisible = false">取消</el-button>
        <el-button :loading="savingPolicy" @click="savePolicy(false)">仅保存</el-button>
        <el-button type="primary" :loading="savingPolicy" @click="savePolicy(true)">
          保存并预检
        </el-button>
      </template>
    </el-drawer>

    <el-drawer v-model="runVisible" title="对账实例详情" size="920px" @closed="stopPolling">
      <template v-if="runDetail.tid">
        <div class="run-heading">
          <div>
            <h3>{{ runDetail.policy_name }}</h3>
            <span class="mono">{{ runDetail.tid }}</span>
          </div>
          <el-tag :type="runStatusType(runDetail.status)" size="large">
            {{ runStatusText(runDetail.status) }}
          </el-tag>
        </div>

        <div class="metric-grid">
          <div><label>来源数据</label><strong>{{ formatNumber(runDetail.source_count) }}</strong></div>
          <div><label>目标数据</label><strong>{{ formatNumber(runDetail.target_count) }}</strong></div>
          <div><label>匹配数据</label><strong>{{ formatNumber(runDetail.matched_count) }}</strong></div>
          <div class="metric-danger"><label>差异数据</label><strong>{{ formatNumber(runDetail.diff_count) }}</strong></div>
        </div>

        <el-alert
          v-if="runDetail.error_message"
          class="detail-alert"
          type="error"
          :title="runDetail.error_message"
          :description="runDetail.error_detail || ''"
          show-icon
          :closable="false"
        />

        <section class="detail-section">
          <div class="section-heading">
            <strong>分桶进度</strong>
            <span>{{ runDetail.bucket_completed || 0 }} / {{ runDetail.bucket_total || 0 }}</span>
          </div>
          <el-progress
            :percentage="bucketProgress"
            :status="runDetail.status === 'FAILED' ? 'exception' : undefined"
          />
          <div class="bucket-list">
            <div v-for="bucket in runDetail.buckets || []" :key="bucket.bucket_no" class="bucket-item">
              <span>#{{ bucket.bucket_no + 1 }}</span>
              <el-tag :type="bucketStatusType(bucket.status)" size="small" effect="plain">
                {{ runStatusText(bucket.status) }}
              </el-tag>
              <span>{{ formatNumber(bucket.source_count) }} / {{ formatNumber(bucket.target_count) }}</span>
              <span class="danger-text">{{ formatNumber(bucket.diff_count) }} 差异</span>
            </div>
          </div>
        </section>

        <section class="detail-section diff-section">
          <div class="section-heading">
            <strong>脱敏差异明细</strong>
            <el-select v-model="diffType" clearable placeholder="全部类型" class="diff-filter" @change="loadDiffs">
              <el-option label="目标缺失" value="MISSING_TARGET" />
              <el-option label="目标多出" value="EXTRA_TARGET" />
              <el-option label="字段不一致" value="VALUE_MISMATCH" />
            </el-select>
          </div>
          <el-table :data="diffRows" border height="280" empty-text="暂无差异">
            <el-table-column prop="business_key" label="业务主键" min-width="150" show-overflow-tooltip />
            <el-table-column label="类型" width="120">
              <template #default="{ row }">{{ diffTypeText(row.diff_type) }}</template>
            </el-table-column>
            <el-table-column prop="field_name" label="字段" width="130" show-overflow-tooltip />
            <el-table-column prop="source_value_masked" label="来源值（脱敏）" min-width="150" show-overflow-tooltip />
            <el-table-column prop="target_value_masked" label="目标值（脱敏）" min-width="150" show-overflow-tooltip />
          </el-table>
          <div class="diff-pagination">
            <el-pagination
              v-model:current-page="diffPage"
              :page-size="20"
              layout="total, prev, pager, next"
              :total="diffTotal"
              @current-change="loadDiffs"
            />
          </div>
        </section>
      </template>
    </el-drawer>

    <el-dialog v-model="precheckVisible" title="策略预检结果" width="640px">
      <el-result
        :icon="precheckResult.success ? 'success' : 'error'"
        :title="precheckResult.success ? '源端与目标端预检通过' : '预检未通过'"
        :sub-title="precheckResult.message || '连接、表结构和主键条件均满足对账要求'"
      />
      <el-descriptions v-if="precheckResult.success" :column="2" border>
        <el-descriptions-item label="来源数据库">{{ precheckResult.sourceProduct }}</el-descriptions-item>
        <el-descriptions-item label="目标数据库">{{ precheckResult.targetProduct }}</el-descriptions-item>
        <el-descriptions-item label="来源唯一键">
          {{ precheckResult.sourceUniqueKey ? "已验证" : "未验证" }}
        </el-descriptions-item>
        <el-descriptions-item label="目标唯一键">
          {{ precheckResult.targetUniqueKey ? "已验证" : "未验证" }}
        </el-descriptions-item>
      </el-descriptions>
      <el-collapse v-if="precheckResult.detail" class="error-detail">
        <el-collapse-item title="查看完整错误详情">
          <pre>{{ precheckResult.detail }}</pre>
        </el-collapse-item>
      </el-collapse>
      <el-alert
        v-for="warning in precheckResult.warnings || []"
        :key="warning"
        class="warning-item"
        type="warning"
        :title="warning"
        :closable="false"
      />
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";

const API = {
  summary: "/ods/reconciliation/summary",
  accessTasks: "/ods/reconciliation/access-tasks",
  policyPage: "/ods/reconciliation/policy/page",
  policyDetail: "/ods/reconciliation/policy/detail",
  policySave: "/ods/reconciliation/policy/save",
  policyPrecheck: "/ods/reconciliation/policy/precheck",
  policyEnable: "/ods/reconciliation/policy/enable",
  runManual: "/ods/reconciliation/run/manual",
  runPage: "/ods/reconciliation/run/page",
  runDetail: "/ods/reconciliation/run/detail",
  runCancel: "/ods/reconciliation/run/cancel",
  diffPage: "/ods/reconciliation/run/diff/page",
};

const activeView = ref("policy");
const viewOptions = [
  { label: "对账策略", value: "policy" },
  { label: "执行记录", value: "run" },
];
const keyword = ref("");
const runStatus = ref("");
const policyTableRef = ref();
const runTableRef = ref();
const accessTasks = ref([]);
const summary = reactive({ policyTotal: 0, policyEnabled: 0, openDiff: 0, runStatus: [] });

const runFilters = computed(() => {
  const counts = Object.fromEntries((summary.runStatus || []).map((item) => [item.status, Number(item.count || 0)]));
  const total = Object.values(counts).reduce((sum, count) => sum + count, 0);
  return [
    { label: "全部", value: "", count: total },
    { label: "运行中", value: "RUNNING", count: Number(counts.RUNNING || 0) + Number(counts.PENDING || 0) + Number(counts.INITIALIZING || 0) },
    { label: "一致", value: "CONSISTENT", count: counts.CONSISTENT || 0 },
    { label: "不一致", value: "INCONSISTENT", count: counts.INCONSISTENT || 0 },
    { label: "失败", value: "FAILED", count: counts.FAILED || 0 },
  ];
});

const policyVisible = ref(false);
const policyFormRef = ref();
const savingPolicy = ref(false);
const emptyPolicy = () => ({
  tid: "",
  policyName: "",
  accessTaskId: "",
  compareMode: "KEY",
  triggerMode: "MANUAL",
  cronExpression: "",
  sourceKeyFields: "",
  targetKeyFields: "",
  sourceIncrementField: "",
  targetIncrementField: "",
  bucketCount: 8,
  fetchSize: 2000,
  diffLimit: 100000,
  numericTolerance: 0,
  trimStrings: true,
  ignoreCase: false,
  nullEqualsEmpty: false,
  rules: [],
});
const policyForm = reactive(emptyPolicy());
const policyRules = {
  policyName: [{ required: true, message: "请输入策略名称", trigger: "blur" }],
  accessTaskId: [{ required: true, message: "请选择接入任务", trigger: "change" }],
  sourceKeyFields: [{ required: true, message: "请输入来源稳定主键", trigger: "blur" }],
  targetKeyFields: [{ required: true, message: "请输入目标稳定主键", trigger: "blur" }],
  cronExpression: [{ required: true, message: "请输入 Cron 表达式", trigger: "blur" }],
};
const selectedTask = computed(() => accessTasks.value.find((item) => item.tid === policyForm.accessTaskId));

const precheckVisible = ref(false);
const precheckResult = reactive({ success: false, message: "", detail: "", warnings: [] });
const runVisible = ref(false);
const runDetail = reactive({});
const diffRows = ref([]);
const diffType = ref("");
const diffPage = ref(1);
const diffTotal = ref(0);
let pollTimer = 0;

const normalizePage = (value) => {
  const data = value?.data || value || {};
  return {
    list: Array.isArray(data.list) ? data.list : [],
    total: Number(data.total || 0),
  };
};

const fetchPolicies = async ({ pageNo = 1, pageSize = 20 } = {}) => {
  const result = await $common.post(API.policyPage, {
    page: pageNo,
    size: pageSize,
    keyword: keyword.value,
  });
  return normalizePage(result);
};

const fetchRuns = async ({ pageNo = 1, pageSize = 20 } = {}) => {
  const result = await $common.post(API.runPage, {
    page: pageNo,
    size: pageSize,
    keyword: keyword.value,
    status: runStatus.value,
  });
  return normalizePage(result);
};

const loadSummary = async () => {
  const result = (await $common.post(API.summary, {})) || {};
  Object.assign(summary, result);
};

const loadAccessTasks = async () => {
  const result = await $common.post(API.accessTasks, {});
  accessTasks.value = Array.isArray(result) ? result : [];
};

const refreshActive = () => {
  if (activeView.value === "policy") policyTableRef.value?.refresh?.(true);
  else runTableRef.value?.refresh?.(true);
  loadSummary();
};

const selectRunStatus = (value) => {
  runStatus.value = value;
  runTableRef.value?.refresh?.(true);
};

const applyAccessTask = () => {
  const task = selectedTask.value;
  if (!task) return;
  policyForm.sourceKeyFields = task.source_table_primary_key || "";
  policyForm.targetKeyFields = task.target_table_primary_key || "";
  policyForm.sourceIncrementField = task.source_table_increment_key || "";
};

const openPolicy = async (row) => {
  Object.assign(policyForm, emptyPolicy());
  await loadAccessTasks();
  if (row?.tid) {
    const detail = await $common.post(API.policyDetail, { policyId: row.tid });
    Object.assign(policyForm, {
      tid: detail.tid,
      policyName: detail.policy_name,
      accessTaskId: detail.access_task_id,
      compareMode: detail.compare_mode,
      triggerMode: detail.trigger_mode,
      cronExpression: detail.cron_expression || "",
      sourceKeyFields: detail.source_key_fields || "",
      targetKeyFields: detail.target_key_fields || "",
      sourceIncrementField: detail.source_increment_field || "",
      targetIncrementField: detail.target_increment_field || "",
      bucketCount: Number(detail.bucket_count || 1),
      fetchSize: Number(detail.fetch_size || 2000),
      diffLimit: Number(detail.diff_limit || 100000),
      numericTolerance: Number(detail.numeric_tolerance || 0),
      trimStrings: Number(detail.trim_strings) === 1,
      ignoreCase: Number(detail.ignore_case) === 1,
      nullEqualsEmpty: Number(detail.null_equals_empty) === 1,
      rules: detail.rules || [],
    });
  }
  policyVisible.value = true;
};

const savePolicy = async (withPrecheck) => {
  await policyFormRef.value?.validate?.();
  savingPolicy.value = true;
  try {
    const saved = await $common.post(API.policySave, { ...policyForm });
    Object.assign(policyForm, { tid: saved.tid });
    ElMessage.success("对账策略已保存");
    if (withPrecheck) {
      await showPrecheck(saved.tid);
    }
    policyVisible.value = false;
    policyTableRef.value?.refresh?.(true);
    loadSummary();
  } finally {
    savingPolicy.value = false;
  }
};

const showPrecheck = async (policyId) => {
  const result = await $common.post(API.policyPrecheck, { policyId });
  Object.assign(precheckResult, { success: false, message: "", detail: "", warnings: [] }, result || {});
  precheckVisible.value = true;
  return Boolean(result?.success);
};

const precheckPolicy = async (row) => {
  await showPrecheck(row.tid);
  policyTableRef.value?.refresh?.();
};

const togglePolicy = async (row, enabled) => {
  try {
    if (enabled) {
      const passed = await showPrecheck(row.tid);
      if (!passed) {
        policyTableRef.value?.refresh?.();
        return;
      }
    }
    await $common.post(API.policyEnable, { policyId: row.tid, enabled });
    ElMessage.success(enabled ? "策略已启用" : "策略已停用");
  } finally {
    policyTableRef.value?.refresh?.();
    loadSummary();
  }
};

const startRun = async (row) => {
  await ElMessageBox.confirm(
    "任务会异步分桶执行，不占用当前页面请求。确认开始对账吗？",
    "启动对账",
    { type: "info", confirmButtonText: "开始执行", cancelButtonText: "取消" }
  );
  const result = await $common.post(API.runManual, {
    policyId: row.tid,
    requestId: `${row.tid}-${Date.now()}`,
  });
  ElMessage.success("对账任务已进入执行队列");
  activeView.value = "run";
  await loadSummary();
  runTableRef.value?.refresh?.(true);
  if (result?.tid) openRun(result);
};

const openRun = async (row) => {
  runVisible.value = true;
  diffType.value = "";
  diffPage.value = 1;
  await loadRunDetail(row.tid);
  await loadDiffs();
  startPolling();
};

const loadRunDetail = async (runId = runDetail.tid) => {
  if (!runId) return;
  const result = await $common.post(API.runDetail, { runId });
  Object.assign(runDetail, result || {});
  if (!["PENDING", "INITIALIZING", "RUNNING"].includes(runDetail.status)) stopPolling();
};

const loadDiffs = async () => {
  if (!runDetail.tid) return;
  const result = normalizePage(
    await $common.post(API.diffPage, {
      runId: runDetail.tid,
      type: diffType.value,
      page: diffPage.value,
      size: 20,
    })
  );
  diffRows.value = result.list;
  diffTotal.value = result.total;
};

const cancelRun = async (row) => {
  await ElMessageBox.confirm("确认请求取消该对账实例吗？已完成的分桶会保留。", "取消对账", {
    type: "warning",
  });
  await $common.post(API.runCancel, { runId: row.tid });
  ElMessage.success("已提交取消请求");
  refreshActive();
};

const startPolling = () => {
  stopPolling();
  if (!["PENDING", "INITIALIZING", "RUNNING"].includes(runDetail.status)) return;
  pollTimer = window.setInterval(async () => {
    await loadRunDetail();
    await loadDiffs();
  }, 3000);
};

const stopPolling = () => {
  if (pollTimer) window.clearInterval(pollTimer);
  pollTimer = 0;
};

const bucketProgress = computed(() => {
  const total = Number(runDetail.bucket_total || 0);
  return total ? Math.round((Number(runDetail.bucket_completed || 0) / total) * 100) : 0;
});

const runStatusText = (status) => ({
  PENDING: "等待执行",
  INITIALIZING: "初始化",
  RUNNING: "运行中",
  COMPLETED: "已完成",
  CONSISTENT: "一致",
  INCONSISTENT: "不一致",
  FAILED: "执行失败",
  CANCELLED: "已取消",
}[status] || "未执行");

const runStatusType = (status) => {
  if (["CONSISTENT", "COMPLETED"].includes(status)) return "success";
  if (status === "INCONSISTENT") return "warning";
  if (status === "FAILED") return "danger";
  if (["PENDING", "INITIALIZING", "RUNNING"].includes(status)) return "primary";
  return "info";
};

const bucketStatusType = (status) => runStatusType(status);
const compareModeText = (mode) => ({ SUMMARY: "总量", KEY: "主键", CONTENT: "内容" }[mode] || mode || "-");
const triggerModeText = (mode) => ({ MANUAL: "手动", CRON: "定时", ACCESS_EVENT: "接入完成" }[mode] || mode || "-");
const diffTypeText = (type) => ({ MISSING_TARGET: "目标缺失", EXTRA_TARGET: "目标多出", VALUE_MISMATCH: "字段不一致" }[type] || type || "-");
const formatNumber = (value) => Number(value || 0).toLocaleString("zh-CN");
const formatDateTime = (value) => {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  const pad = (part) => String(part).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
};

onMounted(loadSummary);
onBeforeUnmount(stopPolling);
</script>

<style scoped>
.reconcile-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 520px;
  padding: 20px;
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.page-toolbar,
.toolbar-actions,
.route-cell,
.operation-buttons,
.run-heading,
.section-heading,
.check-row {
  display: flex;
  align-items: center;
}

.page-toolbar {
  justify-content: space-between;
  margin-bottom: 16px;
}

.toolbar-actions {
  gap: 10px;
}

.search-input {
  width: 300px;
}

.status-strip {
  display: grid;
  grid-template-columns: repeat(5, minmax(110px, 1fr));
  margin-bottom: 16px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
}

.status-filter {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 58px;
  padding: 0 18px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  background: #fff;
  border: 0;
  border-right: 1px solid var(--el-border-color-lighter);
}

.status-filter:last-child {
  border-right: 0;
}

.status-filter:hover {
  background: var(--el-fill-color-light);
}

.status-filter.active {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  box-shadow: inset 0 -2px var(--el-color-primary);
}

.status-filter strong {
  font-size: 22px;
  font-variant-numeric: tabular-nums;
}

.name-link {
  display: block;
  max-width: 100%;
  padding: 0;
  overflow: hidden;
  color: var(--el-color-primary);
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.name-link:hover {
  color: var(--el-color-primary-dark-2);
}

.subline {
  display: block;
  max-width: 100%;
  margin-top: 3px;
  overflow: hidden;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.route-cell {
  gap: 8px;
  min-width: 0;
}

.route-cell span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.danger-text {
  color: var(--el-color-danger);
}

.form-section {
  padding: 18px 18px 4px;
  margin-bottom: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.section-title {
  margin-bottom: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 16px;
}

.selected-route {
  display: grid;
  grid-template-columns: 1fr 32px 1fr;
  gap: 12px;
  align-items: center;
  padding: 12px 16px;
  margin: 0 0 16px 112px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.selected-route > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.selected-route label,
.selected-route span,
.task-option span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.selected-route strong,
.selected-route span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-option {
  display: flex;
  min-width: 440px;
  flex-direction: column;
  padding: 4px 0;
}

.check-row {
  gap: 22px;
  padding: 0 0 16px 112px;
}

.run-heading {
  justify-content: space-between;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.run-heading h3 {
  margin: 0 0 5px;
  font-size: 18px;
}

.run-heading span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  margin: 18px 0;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
}

.metric-grid > div {
  display: flex;
  min-height: 88px;
  flex-direction: column;
  justify-content: center;
  padding: 0 18px;
  border-right: 1px solid var(--el-border-color-lighter);
}

.metric-grid > div:last-child {
  border-right: 0;
}

.metric-grid label {
  margin-bottom: 8px;
  color: var(--el-text-color-secondary);
}

.metric-grid strong {
  font-size: 24px;
  font-variant-numeric: tabular-nums;
}

.metric-danger strong {
  color: var(--el-color-danger);
}

.detail-alert {
  margin-bottom: 18px;
}

.detail-section {
  padding: 16px;
  margin-bottom: 18px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.section-heading {
  justify-content: space-between;
  margin-bottom: 14px;
}

.section-heading span {
  color: var(--el-text-color-secondary);
}

.bucket-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  max-height: 220px;
  margin-top: 14px;
  overflow: auto;
}

.bucket-item {
  display: grid;
  grid-template-columns: 42px 86px 1fr auto;
  gap: 8px;
  align-items: center;
  padding: 8px 10px;
  font-size: 12px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.diff-filter {
  width: 150px;
}

.diff-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.error-detail {
  margin-top: 16px;
}

.error-detail pre {
  max-height: 280px;
  padding: 12px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.warning-item {
  margin-top: 10px;
}

.w-full {
  width: 100%;
}

@media (max-width: 1100px) {
  .status-strip {
    grid-template-columns: repeat(3, 1fr);
  }

  .status-filter:nth-child(3) {
    border-right: 0;
  }

  .status-filter:nth-child(n + 4) {
    border-top: 1px solid var(--el-border-color-lighter);
  }

  .metric-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .metric-grid > div:nth-child(2) {
    border-right: 0;
  }

  .metric-grid > div:nth-child(-n + 2) {
    border-bottom: 1px solid var(--el-border-color-lighter);
  }
}
</style>
