<template>
  <div class="ops-monitor-page card-container px-5 pt-5 pb-2">
    <div class="ops-header">
      <div>
        <h2 class="ops-title">运行监控</h2>
        <p class="ops-subtitle">数据服务接口运行状态、告警规则与调用日志</p>
      </div>
      <el-button :loading="overviewLoading" icon="refresh" @click="loadAll">刷新</el-button>
    </div>

    <div class="ops-cards">
      <div class="ops-card">
        <div class="ops-card__label">告警规则</div>
        <div class="ops-card__value">{{ overview.ruleTotal }}</div>
        <div class="ops-card__hint">启用 {{ overview.ruleEnabled }} 条</div>
      </div>
      <div class="ops-card">
        <div class="ops-card__label">告警事件</div>
        <div class="ops-card__value">{{ overview.eventTotal }}</div>
        <div class="ops-card__hint">
          <span :class="{ 'ops-danger': overview.eventPending > 0 }">待处理 {{ overview.eventPending }} 条</span>
        </div>
      </div>
      <div class="ops-card">
        <div class="ops-card__label">接口调用</div>
        <div class="ops-card__value">{{ overview.callTotal }}</div>
        <div class="ops-card__hint">
          <span :class="{ 'ops-danger': overview.callFailed > 0 }">失败 {{ overview.callFailed }} 次</span>
        </div>
      </div>
      <div class="ops-card">
        <div class="ops-card__label">平均耗时</div>
        <div class="ops-card__value">{{ overview.avgDurationMs }}<span class="ops-unit">ms</span></div>
        <div class="ops-card__hint">最大 {{ overview.maxDurationMs }}ms</div>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="ops-tabs">
      <el-tab-pane label="告警规则" name="rules">
        <div class="ops-toolbar">
          <el-input
            v-model.trim="ruleKeyword"
            class="ops-keyword"
            placeholder="搜索规则名称"
            clearable
            @clear="loadRules(1)"
            @keyup.enter="loadRules(1)"
          />
          <el-button type="primary" @click="loadRules(1)">查询</el-button>
        </div>
        <el-table v-loading="rulesLoading" :data="rules" border stripe class="ops-table">
          <el-table-column prop="name" label="规则名称" min-width="180" show-overflow-tooltip />
          <el-table-column prop="metric" label="指标" width="110" />
          <el-table-column prop="operator" label="比较" width="70" align="center" />
          <el-table-column prop="thresholdValue" label="阈值" width="100" align="right" />
          <el-table-column prop="durationMinutes" label="持续(分钟)" width="100" align="center" />
          <el-table-column prop="notifyTarget" label="通知对象" min-width="130" show-overflow-tooltip />
          <el-table-column label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? "启用" : "停用" }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="180" />
        </el-table>
        <div class="ops-pager">
          <el-pagination
            v-model:current-page="rulePage"
            :page-size="pageSize"
            :total="ruleTotal"
            layout="total, prev, pager, next"
            @current-change="loadRules"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="告警事件" name="events">
        <div class="ops-toolbar">
          <el-select v-model="eventStatus" class="ops-status" placeholder="全部状态" clearable @change="loadEvents(1)">
            <el-option label="待处理" value="triggered" />
            <el-option label="已处理" value="handled" />
          </el-select>
          <el-input
            v-model.trim="eventKeyword"
            class="ops-keyword"
            placeholder="搜索规则名称"
            clearable
            @clear="loadEvents(1)"
            @keyup.enter="loadEvents(1)"
          />
          <el-button type="primary" @click="loadEvents(1)">查询</el-button>
        </div>
        <el-table v-loading="eventsLoading" :data="events" border stripe class="ops-table">
          <el-table-column prop="ruleName" label="规则名称" min-width="180" show-overflow-tooltip />
          <el-table-column prop="metric" label="指标" width="110" />
          <el-table-column prop="actualValue" label="实际值" width="100" align="right" />
          <el-table-column prop="thresholdValue" label="阈值" width="100" align="right" />
          <el-table-column prop="notifyTarget" label="通知对象" min-width="120" show-overflow-tooltip />
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 'triggered' ? 'danger' : 'success'">
                {{ row.status === "triggered" ? "待处理" : "已处理" }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="triggeredAt" label="触发时间" width="180" />
        </el-table>
        <div class="ops-pager">
          <el-pagination
            v-model:current-page="eventPage"
            :page-size="pageSize"
            :total="eventTotal"
            layout="total, prev, pager, next"
            @current-change="loadEvents"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="调用日志" name="calls">
        <div class="ops-toolbar">
          <el-select v-model="callSuccess" class="ops-status" placeholder="全部结果" clearable @change="loadCalls(1)">
            <el-option label="成功" value="1" />
            <el-option label="失败" value="0" />
          </el-select>
          <el-input
            v-model.trim="callKeyword"
            class="ops-keyword"
            placeholder="搜索服务名称或路径"
            clearable
            @clear="loadCalls(1)"
            @keyup.enter="loadCalls(1)"
          />
          <el-button type="primary" @click="loadCalls(1)">查询</el-button>
        </div>
        <el-table v-loading="callsLoading" :data="calls" border stripe class="ops-table">
          <el-table-column prop="serviceName" label="服务名称" min-width="150" show-overflow-tooltip />
          <el-table-column prop="method" label="方法" width="80" align="center" />
          <el-table-column prop="path" label="路径" min-width="220" show-overflow-tooltip />
          <el-table-column prop="status" label="HTTP 状态" width="100" align="center" />
          <el-table-column label="结果" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.success === 1 ? 'success' : 'danger'">{{ row.success === 1 ? "成功" : "失败" }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="durationMs" label="耗时(ms)" width="100" align="right" />
          <el-table-column prop="createdAt" label="调用时间" width="180" />
        </el-table>
        <div class="ops-pager">
          <el-pagination
            v-model:current-page="callPage"
            :page-size="pageSize"
            :total="callTotal"
            layout="total, prev, pager, next"
            @current-change="loadCalls"
          />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";

const pageSize = 10;
const activeTab = ref("rules");
const overviewLoading = ref(false);
const rulesLoading = ref(false);
const eventsLoading = ref(false);
const callsLoading = ref(false);

const overview = reactive({
  ruleTotal: 0,
  ruleEnabled: 0,
  eventTotal: 0,
  eventPending: 0,
  callTotal: 0,
  callFailed: 0,
  avgDurationMs: 0,
  maxDurationMs: 0,
});

const rules = ref([]);
const ruleTotal = ref(0);
const rulePage = ref(1);
const ruleKeyword = ref("");

const events = ref([]);
const eventTotal = ref(0);
const eventPage = ref(1);
const eventKeyword = ref("");
const eventStatus = ref("");

const calls = ref([]);
const callTotal = ref(0);
const callPage = ref(1);
const callKeyword = ref("");
const callSuccess = ref("");

const request = async (url, body) => {
  try {
    const data = await $common.post(url, body);
    return data || {};
  } catch (error) {
    return null;
  }
};

const loadOverview = async () => {
  overviewLoading.value = true;
  try {
    const data = await request("/dws/monitor/overview", {});
    if (!data) {
      return;
    }
    Object.assign(overview, {
      ruleTotal: Number(data.ruleTotal || 0),
      ruleEnabled: Number(data.ruleEnabled || 0),
      eventTotal: Number(data.eventTotal || 0),
      eventPending: Number(data.eventPending || 0),
      callTotal: Number(data.callTotal || 0),
      callFailed: Number(data.callFailed || 0),
      avgDurationMs: Number(data.avgDurationMs || 0),
      maxDurationMs: Number(data.maxDurationMs || 0),
    });
  } finally {
    overviewLoading.value = false;
  }
};

const loadRules = async (pageNum = 1) => {
  rulesLoading.value = true;
  try {
    const data = await request("/dws/monitor/rules", {
      pageNum,
      pageSize,
      keyword: ruleKeyword.value,
    });
    if (!data) {
      return;
    }
    rules.value = data.list || [];
    ruleTotal.value = Number(data.total || 0);
    rulePage.value = pageNum;
  } finally {
    rulesLoading.value = false;
  }
};

const loadEvents = async (pageNum = 1) => {
  eventsLoading.value = true;
  try {
    const data = await request("/dws/monitor/events", {
      pageNum,
      pageSize,
      keyword: eventKeyword.value,
      status: eventStatus.value,
    });
    if (!data) {
      return;
    }
    events.value = data.list || [];
    eventTotal.value = Number(data.total || 0);
    eventPage.value = pageNum;
  } finally {
    eventsLoading.value = false;
  }
};

const loadCalls = async (pageNum = 1) => {
  callsLoading.value = true;
  try {
    const data = await request("/dws/monitor/calls", {
      pageNum,
      pageSize,
      keyword: callKeyword.value,
      success: callSuccess.value === "" ? null : Number(callSuccess.value),
    });
    if (!data) {
      return;
    }
    calls.value = data.list || [];
    callTotal.value = Number(data.total || 0);
    callPage.value = pageNum;
  } finally {
    callsLoading.value = false;
  }
};

const loadAll = async () => {
  await loadOverview();
  await Promise.all([loadRules(rulePage.value), loadEvents(eventPage.value), loadCalls(callPage.value)]);
};

onMounted(async () => {
  await loadAll();
  ElMessage.success("运行监控数据已加载");
});
</script>

<style scoped>
.ops-monitor-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: auto;
}
.ops-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.ops-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #323643;
}
.ops-subtitle {
  margin: 4px 0 0;
  font-size: 13px;
  color: #8a8f99;
}
.ops-cards {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 8px;
}
.ops-card {
  padding: 14px 16px;
  border: 1px solid #e8ebf0;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(24, 31, 46, 0.04);
}
.ops-card__label {
  font-size: 13px;
  color: #8a8f99;
}
.ops-card__value {
  margin-top: 6px;
  font-size: 26px;
  font-weight: 700;
  line-height: 1.2;
  color: #323643;
}
.ops-unit {
  margin-left: 2px;
  font-size: 13px;
  font-weight: 400;
  color: #8a8f99;
}
.ops-card__hint {
  margin-top: 6px;
  font-size: 12px;
  color: #8a8f99;
}
.ops-danger {
  color: #e34d59;
}
.ops-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.ops-keyword {
  width: 260px;
}
.ops-status {
  width: 140px;
}
.ops-pager {
  display: flex;
  justify-content: flex-end;
  padding: 12px 0 4px;
}
@media (max-width: 1200px) {
  .ops-cards {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
