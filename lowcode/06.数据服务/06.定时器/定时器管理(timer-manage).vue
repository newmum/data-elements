<template>
  <div class="timer-page card-container px-5 pt-5 pb-2">
    <div class="timer-header">
      <div>
        <h2 class="timer-title">定时器管理</h2>
        <p class="timer-subtitle">按租户维护 Cron 定时配置和查看历史日志。当前执行端尚未绑定。</p>
      </div>
      <div class="timer-header__actions">
        <el-button :loading="loading" icon="refresh" @click="loadAll">刷新</el-button>
        <el-button type="primary" icon="plus" @click="openCreate">新建任务</el-button>
      </div>
    </div>

    <div class="timer-toolbar">
      <el-input
        v-model.trim="keyword"
        class="timer-keyword"
        placeholder="搜索任务编码或名称"
        clearable
        @clear="loadTimers(1)"
        @keyup.enter="loadTimers(1)"
      />
      <el-select v-model="statusFilter" class="timer-status" placeholder="全部状态" clearable @change="loadTimers(1)">
        <el-option label="启用" value="ENABLED" />
        <el-option label="停用" value="DISABLED" />
      </el-select>
      <el-button type="primary" @click="loadTimers(1)">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="timers" border stripe class="timer-table">
      <el-table-column prop="timerName" label="任务名称" min-width="150" show-overflow-tooltip />
      <el-table-column prop="timerCode" label="任务编码" min-width="150" show-overflow-tooltip />
      <el-table-column prop="cron" label="Cron" min-width="150" show-overflow-tooltip />
      <el-table-column prop="handler" label="处理器" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">
            {{ row.status === "ENABLED" ? "启用" : "停用" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="上次执行" width="170">
        <template #default="{ row }">{{ formatTime(row.lastFireAt) }}</template>
      </el-table-column>
      <el-table-column label="下次执行" width="170">
        <template #default="{ row }">{{ formatTime(row.nextFireAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link :type="row.status === 'ENABLED' ? 'warning' : 'success'" @click="toggleTimer(row)">
            {{ row.status === "ENABLED" ? "停用" : "启用" }}
          </el-button>
          <el-button link type="primary" @click="fireTimer(row)">立即执行</el-button>
          <el-button link type="primary" @click="openLogs(row)">日志</el-button>
          <el-button link type="danger" @click="removeTimer(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="timer-pager">
      <el-pagination
        v-model:current-page="pageNo"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadTimers"
      />
    </div>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑定时任务' : '新建定时任务'" width="680px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="任务编码" prop="timerCode">
          <el-input v-model.trim="form.timerCode" maxlength="64" placeholder="例如 lake_zone_health_check" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="任务名称" prop="timerName">
          <el-input v-model.trim="form.timerName" maxlength="100" placeholder="业务名称" />
        </el-form-item>
        <el-form-item label="Cron" prop="cron">
          <el-input v-model.trim="form.cron" placeholder="例如 0 */5 * * * ? 或 */5 * * * *" />
          <div class="timer-hint">支持 6 段（秒 分 时 日 月 周）或 5 段（分 时 日 月 周）Spring Cron</div>
        </el-form-item>
        <el-form-item label="处理器" prop="handler">
          <el-input v-model.trim="form.handler" placeholder="bean:处理器名 或 magic:METHOD /路径" />
          <div class="timer-hint">保存处理器配置。执行端绑定后才可运行任务。</div>
        </el-form-item>
        <el-form-item label="执行参数">
          <el-input v-model="form.paramsJson" type="textarea" :rows="3" placeholder='JSON 对象，例如 {"pageSize": 1}' />
        </el-form-item>
        <el-form-item label="锁租期(秒)" prop="lockTtlSeconds">
          <el-input-number v-model="form.lockTtlSeconds" :min="5" :max="3600" :step="5" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="ENABLED">启用</el-radio>
            <el-radio value="DISABLED">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="任务说明">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" placeholder="用途、影响范围等" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveTimer">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="logsVisible" :title="logTitle" size="60%">
      <div class="timer-toolbar">
        <el-button :loading="logsLoading" icon="refresh" @click="loadLogs(logPage)">刷新</el-button>
      </div>
      <el-table v-loading="logsLoading" :data="logs" border stripe class="timer-table">
        <el-table-column label="触发时间" width="180">
          <template #default="{ row }">{{ formatTime(row.fireAt) }}</template>
        </el-table-column>
        <el-table-column label="结果" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'">{{ row.result === "SUCCESS" ? "成功" : "失败" }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="costMs" label="耗时(ms)" width="110" align="right" />
        <el-table-column prop="errorMsg" label="错误信息" min-width="260" show-overflow-tooltip />
      </el-table>
      <div class="timer-pager">
        <el-pagination
          v-model:current-page="logPage"
          :page-size="logPageSize"
          :total="logTotal"
          layout="total, prev, pager, next"
          @current-change="loadLogs"
        />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";

const pageSize = 10;
const loading = ref(false);
const saving = ref(false);
const timers = ref([]);
const total = ref(0);
const pageNo = ref(1);
const keyword = ref("");
const statusFilter = ref("");

const formVisible = ref(false);
const formRef = ref(null);
const form = reactive(emptyForm());
const rules = {
  timerCode: [{ required: true, message: "请输入任务编码", trigger: "blur" }],
  timerName: [{ required: true, message: "请输入任务名称", trigger: "blur" }],
  cron: [{ required: true, message: "请输入 Cron 表达式", trigger: "blur" }],
  handler: [{ required: true, message: "请输入处理器", trigger: "blur" }],
  lockTtlSeconds: [{ required: true, message: "请输入锁租期", trigger: "blur" }],
};

const logsVisible = ref(false);
const logsLoading = ref(false);
const logs = ref([]);
const logTotal = ref(0);
const logPage = ref(1);
const logPageSize = 10;
const logTitle = ref("执行日志");
const logTimerId = ref("");

function emptyForm() {
  return {
    id: "",
    timerCode: "",
    timerName: "",
    cron: "",
    handler: "",
    paramsJson: "",
    lockTtlSeconds: 60,
    status: "ENABLED",
    description: "",
  };
}

function formatTime(value) {
  if (!value) return "—";
  return String(value).replace("T", " ").replace(/\.\d+$/, "");
}

const request = async (url, body) => {
  try {
    return (await $common.post(url, body)) || {};
  } catch (error) {
    return null;
  }
};

const loadTimers = async (page = 1) => {
  loading.value = true;
  try {
    const data = await $common.get("/dws/timer/page", {
      pageNo: page,
      pageSize,
      keyword: keyword.value,
      status: statusFilter.value,
    });
    timers.value = data?.list || [];
    total.value = Number(data?.total || 0);
    pageNo.value = page;
  } catch (error) {
    ElMessage.error("定时任务加载失败");
  } finally {
    loading.value = false;
  }
};

const loadAll = async () => {
  await loadTimers(pageNo.value);
};

const openCreate = () => {
  Object.assign(form, emptyForm());
  formVisible.value = true;
};

const openEdit = (row) => {
  Object.assign(form, emptyForm(), {
    id: row.id,
    timerCode: row.timerCode,
    timerName: row.timerName,
    cron: row.cron,
    handler: row.handler,
    paramsJson: row.paramsJson,
    lockTtlSeconds: Number(row.lockTtlSeconds || 60),
    status: row.status || "ENABLED",
    description: row.description,
  });
  formVisible.value = true;
};

const validateParams = () => {
  const value = form.paramsJson?.trim();
  if (!value) return true;
  try {
    JSON.parse(value);
    return true;
  } catch (error) {
    ElMessage.error("执行参数不是合法 JSON");
    return false;
  }
};

const saveTimer = async () => {
  if (!formRef.value) return;
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid || !validateParams()) return;
  saving.value = true;
  try {
    const data = await request("/dws/timer/manage", {
      action: "save",
      id: form.id,
      timerCode: form.timerCode,
      timerName: form.timerName,
      cron: form.cron,
      handler: form.handler,
      paramsJson: form.paramsJson,
      lockTtlSeconds: form.lockTtlSeconds,
      status: form.status,
      description: form.description,
    });
    if (!data) return;
    formVisible.value = false;
    ElMessage.success("定时任务已保存");
    await loadTimers(form.id ? pageNo.value : 1);
  } finally {
    saving.value = false;
  }
};

const toggleTimer = async (row) => {
  const target = row.status === "ENABLED" ? "DISABLED" : "ENABLED";
  const data = await request("/dws/timer/manage", {
    action: "toggle",
    id: row.id,
    status: target,
  });
  if (!data) return;
  ElMessage.success(target === "ENABLED" ? "定时任务已启用" : "定时任务已停用");
  await loadTimers(pageNo.value);
};

const fireTimer = async (row) => {
  try {
    await ElMessageBox.confirm(`确认立即执行「${row.timerName}」一次？`, "立即执行", { type: "warning" });
  } catch (error) {
    return;
  }
  const data = await request("/dws/timer/manage", { action: "fire", timerCode: row.timerCode });
  if (!data) return;
  if (data.success) {
    ElMessage.success(`执行成功，耗时 ${data.costMs || 0} ms`);
  } else {
    ElMessage.error(data.message || data.errorMsg || "执行失败");
  }
  await loadTimers(pageNo.value);
  if (logsVisible.value) {
    await loadLogs(logPage.value);
  }
};

const removeTimer = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除「${row.timerName}」？删除后不再调度，执行日志保留。`, "删除定时任务", { type: "warning" });
  } catch (error) {
    return;
  }
  const data = await request("/dws/timer/manage", { action: "remove", id: row.id });
  if (!data) return;
  ElMessage.success("定时任务已删除");
  await loadTimers(pageNo.value);
};

const openLogs = async (row) => {
  logTimerId.value = row.id;
  logTitle.value = `执行日志 · ${row.timerName}`;
  logsVisible.value = true;
  await loadLogs(1);
};

const loadLogs = async (page = 1) => {
  logsLoading.value = true;
  try {
    const data = await $common.get("/dws/timer/logs", {
      timerId: logTimerId.value,
      pageNo: page,
      pageSize: logPageSize,
    });
    logs.value = data?.list || [];
    logTotal.value = Number(data?.total || 0);
    logPage.value = page;
  } catch (error) {
    ElMessage.error("执行日志加载失败");
  } finally {
    logsLoading.value = false;
  }
};

onMounted(async () => {
  await loadAll();
});
</script>

<style scoped>
.timer-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: auto;
}
.timer-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.timer-header__actions {
  display: flex;
  gap: 8px;
}
.timer-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #323643;
}
.timer-subtitle {
  margin: 4px 0 0;
  font-size: 13px;
  color: #8a8f99;
}
.timer-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.timer-keyword {
  width: 260px;
}
.timer-status {
  width: 140px;
}
.timer-pager {
  display: flex;
  justify-content: flex-end;
  padding: 12px 0 4px;
}
.timer-hint {
  margin-top: 4px;
  font-size: 12px;
  color: #8a8f99;
}
</style>
