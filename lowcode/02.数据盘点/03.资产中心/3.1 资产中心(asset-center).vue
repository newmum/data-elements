<template>
  <div class="asset-center card-container flex flex-col px-5 pt-5 pb-2">
    <div class="ac-header">
      <div class="ac-header__title">
        <h2>资产中心</h2>
        <p>资产总览 · 目录审批 · 资源挂接</p>
      </div>
      <div class="ac-header__actions">
        <span v-if="statsAsOf" class="ac-asof">统计时间 {{ statsAsOf }}</span>
        <el-button :loading="loading.stats" icon="refresh" @click="loadAll">刷新</el-button>
      </div>
    </div>

    <div v-loading="loading.stats" class="ac-metrics">
      <div
        v-for="item in metrics"
        :key="item.code"
        class="ac-metric"
        :class="{ 'is-muted': item.status !== 'AVAILABLE' }"
        :data-metric="item.code"
      >
        <span class="ac-metric__label">{{ item.label }}</span>
        <span class="ac-metric__value">{{ metricValue(item) }}</span>
        <span class="ac-metric__unit">{{ item.unit || "" }}</span>
      </div>
    </div>

    <div class="ac-toolbar">
      <div class="ac-toolbar__title">
        <h3>资源目录</h3>
        <span class="ac-toolbar__hint">共 {{ total }} 个目录 · 审批只作用于当前版本</span>
      </div>
      <div class="ac-toolbar__filters">
        <el-select
          v-model="query.state"
          class="ac-state"
          placeholder="全部状态"
          clearable
          @change="loadCatalogs(1)"
        >
          <el-option label="待注册" value="DRAFT" />
          <el-option label="审批中" value="PENDING_REVIEW" />
          <el-option label="已退回" value="RETURNED" />
          <el-option label="已注册" value="APPROVED" />
        </el-select>
        <el-input
          v-model.trim="query.keyword"
          class="ac-keyword"
          placeholder="目录名称或关联数据资源"
          clearable
          @clear="loadCatalogs(1)"
          @keyup.enter="loadCatalogs(1)"
        />
        <el-button type="primary" @click="loadCatalogs(1)">查询</el-button>
      </div>
    </div>

    <el-table
      v-loading="loading.list"
      :data="rows"
      border
      stripe
      class="ac-table"
      row-key="tid"
      empty-text="暂无符合条件的数据目录"
    >
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column label="数据目录名称" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <el-text type="primary" cursor-pointer @click="openDetail(row)">{{ row.catalogName }}</el-text>
        </template>
      </el-table-column>
      <el-table-column prop="sourceTableName" label="关联数据资源" min-width="170" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.sourceTableName">{{ row.sourceTableName }}</span>
          <el-text v-else type="info">未挂接</el-text>
        </template>
      </el-table-column>
      <el-table-column prop="dbName" label="数据源" min-width="150" show-overflow-tooltip />
      <el-table-column label="数据组织" width="100" align="center">
        <template #default="{ row }">{{ dictLabel("dataSourceType", row.dataSourceType) }}</template>
      </el-table-column>
      <el-table-column prop="fieldCount" label="来源字段数" width="100" align="center">
        <template #default="{ row }">{{ row.fieldCount == null ? "-" : row.fieldCount }}</template>
      </el-table-column>
      <el-table-column prop="regTime" label="注册时间" width="160" />
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="stateTag(stateOf(row))" size="small">{{ stateLabel(stateOf(row)) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="110" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">查看与处理</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="ac-pager">
      <el-pagination
        v-model:current-page="pageNo"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @current-change="loadCatalogs()"
        @size-change="loadCatalogs(1)"
      />
    </div>

    <el-drawer
      v-model="detail.visible"
      :title="detail.draft && detail.draft.name ? detail.draft.name : '数据目录'"
      size="680px"
      destroy-on-close
    >
      <div v-loading="detail.loading" class="ac-detail">
        <el-descriptions :column="2" border size="small" class="ac-desc">
          <el-descriptions-item label="目录名称" :span="2">{{ detail.draft.name || "-" }}</el-descriptions-item>
          <el-descriptions-item label="关联数据资源">{{ detail.draft.resourceName || "未挂接" }}</el-descriptions-item>
          <el-descriptions-item label="数据项数">{{ fieldCount }}</el-descriptions-item>
          <el-descriptions-item label="管理单位">{{ detail.row.manageUnit || "-" }}</el-descriptions-item>
          <el-descriptions-item label="当前版本">{{ detail.version == null ? "-" : detail.version }}</el-descriptions-item>
          <el-descriptions-item label="登记状态">
            <el-tag :type="stateTag(detail.draft.state)" size="small">{{ stateLabel(detail.draft.state) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="共享属性">{{ dictLabel("shareType", detail.row.shareType) }}</el-descriptions-item>
          <el-descriptions-item label="目录说明" :span="2">{{ detail.draft.summary || "-" }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="ac-section">资源挂接</h4>
        <el-descriptions :column="2" border size="small" class="ac-desc">
          <el-descriptions-item label="挂接数据资源">{{ detail.draft.resourceName || "未挂接" }}</el-descriptions-item>
          <el-descriptions-item label="数据源">{{ detail.row.dbName || detail.draft.sourceId || "-" }}</el-descriptions-item>
          <el-descriptions-item label="数据项" :span="2">
            <span v-if="fieldCount">{{ fieldCount }} 项：{{ fieldLabels }}</span>
            <el-text v-else type="info">尚未挂接数据项</el-text>
          </el-descriptions-item>
        </el-descriptions>

        <h4 class="ac-section">审批流转</h4>
        <el-descriptions :column="2" border size="small" class="ac-desc">
          <el-descriptions-item label="提交人">{{ submittedLabel }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ formatDateTime(detail.draft.submittedAt) }}</el-descriptions-item>
          <el-descriptions-item label="审核意见" :span="2">{{ detail.draft.auditOpinion || detail.row.resourceCenterAuditOpinion || "-" }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="hasActions" class="ac-actions">
          <el-input
            v-model="detail.opinion"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="审批意见：通过可留空，退回必须填写原因"
          />
          <div class="ac-actions__buttons">
            <el-button v-if="canNewVersion" :loading="detail.acting" @click="newVersion">重新编目</el-button>
            <el-button v-if="canSubmit" type="primary" :loading="detail.acting" @click="submitCatalog">
              提交审核
            </el-button>
            <el-button v-if="canWithdraw" :loading="detail.acting" @click="withdraw">撤回</el-button>
            <el-button v-if="canReview" type="success" :loading="detail.acting" @click="review(true)">审核通过</el-button>
            <el-button v-if="canReview" type="danger" :loading="detail.acting" @click="review(false)">退回</el-button>
          </div>
          <p class="ac-actions__hint">{{ actionHint }}</p>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";

const loading = reactive({ stats: false, list: false });
const metrics = ref([]);
const statsAsOf = ref("");
const rows = ref([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(20);
const query = reactive({ state: "", keyword: "" });

const detail = reactive({
  visible: false,
  loading: false,
  acting: false,
  id: "",
  version: null,
  draft: {},
  row: {},
  opinion: "",
});

const STATE_LABELS = { DRAFT: "待注册", PENDING_REVIEW: "审批中", RETURNED: "已退回", APPROVED: "已注册" };
const STATE_TAGS = { DRAFT: "info", PENDING_REVIEW: "warning", RETURNED: "danger", APPROVED: "success" };
const stateLabel = (state) => STATE_LABELS[state] || state || "-";
const stateTag = (state) => STATE_TAGS[state] || "info";

const formatDateTime = (value) => {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  const pad = (number) => String(number).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ` +
    `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
};

/** 字典项可能带子级，按值递归查找中文标签；字典未加载时回退原值。 */
const findDictLabel = (items, value) => {
  for (const item of items || []) {
    if (String(item.value) === String(value)) return item.label;
    const child = findDictLabel(item.children, value);
    if (child) return child;
  }
  return "";
};

const dictLabel = (code, value) => {
  if (value === null || value === undefined || value === "") return "-";
  try {
    const label = findDictLabel($dict.getDictItems(code), value);
    return label || value;
  } catch (error) {
    return value;
  }
};

/** 与目录读取接口保持一致：审批中以 flowStatus=1 优先，主表状态为已注册且有审核痕迹则视为已注册。 */
const stateOf = (row) => {
  const flow = String(row.flowStatus == null ? "" : row.flowStatus);
  const asset = String(row.assetStatus == null ? "" : row.assetStatus);
  if (flow === "1") return "PENDING_REVIEW";
  if (flow === "3") return "RETURNED";
  if (asset === "2" && flow === "2") return "APPROVED";
  return "DRAFT";
};

const metricValue = (item) => (item.value === null || item.value === undefined ? "—" : item.value);

const fieldCount = computed(() => (detail.draft.fieldIds || []).length);
const fieldLabels = computed(() => {
  const labels = detail.draft.fieldLabels || {};
  const list = Object.keys(labels).map((key) => labels[key]);
  return list.length > 5 ? `${list.slice(0, 5).join("、")} 等` : list.join("、");
});

const currentUserId = computed(() => ($user && $user.id) || "");
const submittedLabel = computed(() => {
  const submittedBy = detail.draft.submittedBy;
  if (!submittedBy) return "-";
  return currentUserId.value && submittedBy === currentUserId.value ? "本人" : "其他用户";
});
const canSubmit = computed(() => ["DRAFT", "RETURNED"].includes(detail.draft.state));
const canWithdraw = computed(
  () => detail.draft.state === "PENDING_REVIEW" && !!currentUserId.value && detail.draft.submittedBy === currentUserId.value
);
const canReview = computed(
  () => detail.draft.state === "PENDING_REVIEW" && detail.draft.submittedBy !== currentUserId.value
);
const canNewVersion = computed(() => detail.draft.state === "APPROVED");
const hasActions = computed(() => canSubmit.value || canWithdraw.value || canReview.value || canNewVersion.value);
const actionHint = computed(() => {
  if (canReview.value) return "审核只作用于本版本；退回后提交人可修改并重新提交。";
  if (canWithdraw.value) return "撤回后目录回到待注册状态，版本号不变。";
  if (canSubmit.value) return "提交前会自动校验必填信息与挂接资源。";
  if (canNewVersion.value) return "重新编目会创建新版本，不影响已注册的当前版本。";
  return "";
});

const loadMetrics = async () => {
  loading.stats = true;
  try {
    const data = await $common.get("/dwa/assets/statistics", {});
    metrics.value = (data && data.metrics) || [];
    const asOf = metrics.value.find((item) => item.asOf);
    statsAsOf.value = asOf ? formatDateTime(asOf.asOf) : "";
  } catch (error) {
    console.error("资产统计加载失败:", error);
    metrics.value = [];
    statsAsOf.value = "";
  } finally {
    loading.stats = false;
  }
};

const buildConditions = () => {
  const conditions = [
    { field: "assetType", value: "catalog", type: "match" },
    { field: "dataSourceType", value: "ods", type: "match" },
  ];
  if (query.state === "PENDING_REVIEW") {
    conditions.push({ field: "flowStatus", value: "1", type: "match" });
  } else if (query.state === "RETURNED") {
    conditions.push({ field: "flowStatus", value: "3", type: "match" });
  } else if (query.state === "APPROVED") {
    conditions.push({ field: "assetStatus", value: "2", type: "match" });
  } else if (query.state === "DRAFT") {
    conditions.push({ field: "assetStatus", value: "0", type: "match" });
    conditions.push({ field: "flowStatus", value: "0", type: "match" });
  }
  if (query.keyword) {
    conditions.push({ field: "catalogName", value: query.keyword, type: "like" });
  }
  return conditions;
};

const loadCatalogs = async (page) => {
  if (page) pageNo.value = page;
  loading.list = true;
  try {
    const data = await $common.post("/dst/catalog/page", {
      pageNum: pageNo.value,
      pageSize: pageSize.value,
      sortField: "updatedTime",
      sortDir: "desc",
      conditions: buildConditions(),
    });
    rows.value = (data && data.list) || [];
    total.value = (data && data.total) || 0;
  } catch (error) {
    console.error("数据目录列表加载失败:", error);
    rows.value = [];
    total.value = 0;
  } finally {
    loading.list = false;
  }
};

const loadAll = () => {
  loadMetrics();
  loadCatalogs();
};

const command = (action, version, extra) =>
  $common.post(
    "/dwm/ops/command",
    {
      kind: "catalogs",
      method: "POST",
      id: detail.id,
      action,
      version,
      body: extra || {},
    },
    {},
    60000
  );

const openDetail = async (row) => {
  detail.visible = true;
  detail.loading = true;
  detail.acting = false;
  detail.opinion = "";
  detail.draft = {};
  detail.version = null;
  detail.id = row.tid;
  detail.row = row || {};
  try {
    const data = await $common.post(
      "/dwm/ops/command",
      {
        kind: "catalogs",
        method: "GET",
        id: row.tid,
      },
      {},
      60000
    );
    detail.version = data && data.version != null ? data.version : null;
    detail.draft = (data && data.draft) || {};
  } catch (error) {
    console.error("数据目录详情加载失败:", error);
  } finally {
    detail.loading = false;
  }
};

/** 动作成功后重新取一次详情，保证版本号与状态来自服务端而不是前端推算。 */
const afterAction = async (message) => {
  ElMessage.success(message);
  await openDetail({ tid: detail.id, ...detail.row });
  await loadCatalogs();
};

const submitCatalog = async () => {
  if (detail.acting) return;
  detail.acting = true;
  try {
    const checked = await command("check", detail.version);
    const checks = (checked && checked.checks) || [];
    if (checks.length) {
      const problems = checks.map((item) => item.message || item.label || item.code || String(item)).join("；");
      ElMessage.warning(`校验未通过：${problems}`);
      return;
    }
    await command("submit", detail.version);
    await afterAction("已提交审核");
  } catch (error) {
    console.error("提交审核失败:", error);
  } finally {
    detail.acting = false;
  }
};

const withdraw = async () => {
  if (detail.acting) return;
  detail.acting = true;
  try {
    await command("withdraw", detail.version, { opinion: detail.opinion.trim() });
    await afterAction("已撤回");
  } catch (error) {
    console.error("撤回失败:", error);
  } finally {
    detail.acting = false;
  }
};

const review = async (approve) => {
  if (detail.acting) return;
  if (!approve && !detail.opinion.trim()) {
    ElMessage.warning("退回时请填写审批意见");
    return;
  }
  detail.acting = true;
  try {
    await command("review", detail.version, { approve, opinion: detail.opinion.trim() });
    await afterAction(approve ? "审核通过" : "已退回");
  } catch (error) {
    console.error("审核失败:", error);
  } finally {
    detail.acting = false;
  }
};

const newVersion = async () => {
  if (detail.acting) return;
  detail.acting = true;
  try {
    await command("new-version", detail.version);
    await afterAction("已创建新版本，可编辑后提交审核");
  } catch (error) {
    console.error("重新编目失败:", error);
  } finally {
    detail.acting = false;
  }
};

onMounted(() => {
  try {
    $dict.loadDictItems("dataSourceType");
    $dict.loadDictItems("shareType");
  } catch (error) {
    console.error("字典加载失败:", error);
  }
  loadAll();
});
</script>

<style scoped lang="scss">
.asset-center {
  width: 100%;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.ac-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex: 0 0 auto;
}

.ac-header__title h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #303133;
}

.ac-header__title p {
  margin: 4px 0 0;
  font-size: 12px;
  color: #909399;
}

.ac-header__actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ac-asof {
  font-size: 12px;
  color: #909399;
}

.ac-metrics {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px;
  margin: 14px 0 16px;
  flex: 0 0 auto;
}

.ac-metric {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px;
  background: #f7f9fc;
  border: 1px solid #e4e8f0;
  border-radius: 8px;
  min-width: 0;
}

.ac-metric.is-muted {
  opacity: 0.55;
}

.ac-metric__label {
  font-size: 12px;
  color: #606266;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ac-metric__value {
  font-size: 22px;
  font-weight: 700;
  color: #1f2d3d;
  line-height: 1.2;
}

.ac-metric__unit {
  font-size: 12px;
  color: #909399;
}

.ac-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
  flex: 0 0 auto;
}

.ac-toolbar__title {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.ac-toolbar__title h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: #303133;
}

.ac-toolbar__hint {
  font-size: 12px;
  color: #909399;
}

.ac-toolbar__filters {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-left: auto;
}

.ac-state {
  width: 130px;
}

.ac-keyword {
  width: 240px;
}

.ac-table {
  flex: 1 1 auto;
  min-height: 0;
}

.ac-pager {
  display: flex;
  justify-content: flex-end;
  padding: 10px 0 0;
  flex: 0 0 auto;
}

.ac-detail {
  padding-bottom: 24px;
}

.ac-section {
  margin: 18px 0 8px;
  font-size: 14px;
  font-weight: 700;
  color: #303133;
}

.ac-actions {
  margin-top: 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ac-actions__buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.ac-actions__hint {
  margin: 0;
  font-size: 12px;
  color: #909399;
}

@media (max-width: 760px) {
  .ac-toolbar__filters {
    width: 100%;
    flex-wrap: wrap;
    margin-left: 0;
  }

  .ac-keyword,
  .ac-state {
    width: 100%;
  }
}
</style>
