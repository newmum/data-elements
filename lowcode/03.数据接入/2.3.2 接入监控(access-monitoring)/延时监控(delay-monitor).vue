<template>
  <el-dialog v-model="visible" width="min(1120px, calc(100vw - 32px))" top="5vh" destroy-on-close :close-on-click-modal="false">
    <template #header>
      <div class="monitor-heading"><span class="heading-icon"><Icon icon="el-icon-Timer" :size="20" /></span>
        <div class="heading-copy"><h3>延迟监控</h3><p :title="taskName">{{ taskName || '当前任务' }}</p></div></div>
    </template>
    <div class="monitor-body">
      <el-form class="monitor-filters" @submit.prevent="handleSearch">
        <el-form-item label="采集状态"><el-select v-model="draft.monitorStatus" placeholder="全部状态" clearable aria-label="采集状态">
          <el-option label="采集成功" value="SUCCESS" /><el-option label="监控异常" value="ERROR" /><el-option label="队列阻塞" value="BLOCKED" />
        </el-select></el-form-item>
        <el-form-item label="采样时间" class="time-filter"><el-date-picker v-model="draft.range" type="datetimerange" range-separator="至"
          start-placeholder="开始时间" end-placeholder="结束时间" format="YYYY-MM-DD HH:mm:ss" value-format="YYYY-MM-DD HH:mm:ss" aria-label="采样时间范围" /></el-form-item>
        <div class="filter-actions"><el-button type="primary" native-type="submit" :loading="loading"><Icon icon="el-icon-Search" />查询</el-button><el-button @click="handleReset">重置</el-button></div>
      </el-form>
      <div class="results-caption"><span>历史采样记录 <b>{{ total.toLocaleString() }}</b></span><span>默认查看今天 · 展开记录可查看流量明细</span></div>
      <el-alert v-if="loadError" type="error" show-icon :closable="false" title="延迟监控加载失败" :description="loadError" class="load-error" />
      <el-table v-loading="loading" :data="rows" class="monitor-table" height="100%" row-key="tid"
        :empty-text="loadError ? '请检查错误原因后重新查询' : '所选时间范围内暂无监控记录'" @sort-change="handleSort">
        <el-table-column type="expand" width="38"><template #default="{ row }"><div class="sample-detail">
          <div><span>活动线程</span><b>{{ numberText(row.activeThreadCount) }}</b></div>
          <div><span>流入文件</span><b>{{ numberText(row.flowFilesIn) }}</b></div><div><span>流出文件</span><b>{{ numberText(row.flowFilesOut) }}</b></div>
          <div><span>流入大小</span><b>{{ bytesText(row.bytesIn) }}</b></div><div><span>流出大小</span><b>{{ bytesText(row.bytesOut) }}</b></div>
          <p><span>采集消息</span>{{ row.monitorMsg || '暂无附加消息' }}</p>
        </div></template></el-table-column>
        <el-table-column label="序号" width="60" align="center"><template #default="{ $index }">{{ (page - 1) * pageSize + $index + 1 }}</template></el-table-column>
        <el-table-column prop="monitorTime" label="采样时间" width="174" sortable="custom" />
        <el-table-column prop="delayMsEstimate" label="估算延迟" min-width="124" sortable="custom"><template #default="{ row }">{{ durationText(row.delayMsEstimate) }}</template></el-table-column>
        <el-table-column prop="thresholdMs" label="超时阈值" min-width="124" sortable="custom"><template #default="{ row }">{{ durationText(row.thresholdMs) }}</template></el-table-column>
        <el-table-column label="是否超时" width="100" align="center"><template #default="{ row }"><el-tag v-if="row.isTimeout != null" :type="Number(row.isTimeout) === 1 ? 'danger' : 'info'" size="small">{{ Number(row.isTimeout) === 1 ? '已超时' : '未超时' }}</el-tag><span v-else class="muted">—</span></template></el-table-column>
        <el-table-column prop="queuedCount" label="排队数量" min-width="116" sortable="custom"><template #default="{ row }">{{ numberText(row.queuedCount) }}</template></el-table-column>
        <el-table-column prop="queuedBytes" label="排队大小" min-width="116" sortable="custom"><template #default="{ row }">{{ bytesText(row.queuedBytes) }}</template></el-table-column>
        <el-table-column prop="monitorStatus" label="采集状态" min-width="110" align="center"><template #default="{ row }"><el-tag :type="statusType(row.monitorStatus)" size="small">{{ statusLabel(row.monitorStatus) }}</el-tag></template></el-table-column>
      </el-table>
      <div class="monitor-pagination"><el-pagination :current-page="page" :page-size="pageSize" :total="total" :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next" :pager-count="5" size="small" @size-change="handleSize" @current-change="handlePage" /></div>
    </div>
    <template #footer><div class="monitor-footer"><span>{{ refreshedAt ? `最近读取 ${refreshedAt}` : '采集状态与任务运行状态分别展示' }}</span><div><el-button :disabled="loading" @click="fetchData">刷新记录</el-button><el-button @click="visible = false">关闭</el-button></div></div></template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onBeforeUnmount } from 'vue';
import dayjs from 'dayjs';
const props = defineProps<{ modelValue: boolean; taskId: string; taskName?: string }>();
const emit = defineEmits<{ (event: 'update:modelValue', value: boolean): void }>();
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) });
const todayRange = () => [dayjs().startOf('day').format('YYYY-MM-DD HH:mm:ss'), dayjs().endOf('day').format('YYYY-MM-DD HH:mm:ss')];
const draft = reactive({ monitorStatus: '', range: todayRange() as string[] | null });
let applied = { monitorStatus: '', beginTime: '', endTime: '' };
const rows = ref<any[]>([]), total = ref(0), page = ref(1), pageSize = ref(20);
const loading = ref(false), loadError = ref(''), refreshedAt = ref('');
const sort = ref<{ sortField?: string; sortDir?: string }>({});
let sequence = 0, controller: AbortController | undefined;
const cancel = () => { sequence++; controller?.abort(); loading.value = false; };
const errorText = (error: any) => {
  const status = error?.response?.status, data = error?.response?.data;
  if (status === 401) return '登录已过期，请重新登录后重试';
  if (status === 403) return '没有查看当前任务监控记录的权限';
  if (error?.code === 'ECONNABORTED') return '查询超过 30 秒，请缩小时间范围后重试';
  return String(data?.msg || data?.message || data?.error || error?.message || '无法连接监控服务，请稍后重试');
};
const fetchData = async () => {
  if (!props.modelValue) return;
  controller?.abort(); controller = new AbortController();
  const requestId = ++sequence;
  // 翻页读取期间保留总数，避免分页器把第 2 页误重置成第 1 页。
  loading.value = true; loadError.value = ''; rows.value = []; refreshedAt.value = '';
  try {
    if (!props.taskId?.trim()) throw new Error('当前任务缺少任务标识，请重新打开任务后重试');
    const response = await (window as any).$request.post('/ods/task/monitorSnapPage', {
      taskId: props.taskId, pageNum: page.value, pageSize: pageSize.value, ...applied, ...sort.value,
    }, { responsePolicy: 'raw', errorPolicy: 'silent', timeout: 30000, signal: controller.signal });
    const body = response.data;
    if (body?.code != null && ![0, 1, 200].includes(Number(body.code))) throw new Error(body.msg || body.message || `查询失败（${body.code}）`);
    const data = body?.code != null ? body.data : body;
    if (!Array.isArray(data?.list) || !Number.isFinite(Number(data?.total))) throw new Error('监控接口返回格式异常，请联系管理员检查接口版本');
    if (requestId !== sequence) return;
    rows.value = data.list; total.value = Number(data.total); refreshedAt.value = dayjs().format('HH:mm:ss');
  } catch (error: any) { if (requestId === sequence && error?.code !== 'ERR_CANCELED') { page.value = 1; total.value = 0; loadError.value = errorText(error); } }
  finally { if (requestId === sequence) loading.value = false; }
};
const handleSearch = () => { applied = { monitorStatus: draft.monitorStatus, beginTime: draft.range?.[0] || '', endTime: draft.range?.[1] || '' }; page.value = 1; fetchData(); };
const handleReset = () => { draft.monitorStatus = ''; draft.range = todayRange(); handleSearch(); };
const handleSort = ({ prop, order }: any) => { sort.value = order ? { sortField: prop, sortDir: order === 'ascending' ? 'asc' : 'desc' } : {}; page.value = 1; fetchData(); };
const handleSize = (value: number) => { pageSize.value = value; page.value = 1; fetchData(); };
const handlePage = (value: number) => { if (value !== page.value) { page.value = value; fetchData(); } };
const numberText = (value: any) => value == null || value === '' ? '—' : Number(value).toLocaleString();
const durationText = (value: any) => value == null || value === '' ? '—' : Number(value) < 1000 ? `${numberText(value)} ms` : `${Number((Number(value) / 1000).toFixed(2)).toLocaleString()} s`;
const bytesText = (value: any) => { if (value == null || value === '') return '—'; const n = Number(value); if (n < 1024) return `${n} B`; if (n < 1048576) return `${(n / 1024).toFixed(1)} KB`; return `${(n / 1048576).toFixed(1)} MB`; };
const statusLabel = (value: string) => ({ SUCCESS: '采集成功', ERROR: '监控异常', BLOCKED: '队列阻塞' }[value] || value || '未知');
const statusType = (value: string) => value === 'ERROR' ? 'danger' : value === 'BLOCKED' ? 'warning' : value === 'SUCCESS' ? 'success' : 'info';
watch(() => [props.modelValue, props.taskId] as const, ([open]) => {
  cancel(); rows.value = []; total.value = 0; loadError.value = ''; refreshedAt.value = '';
  if (open) { sort.value = {}; pageSize.value = 20; handleReset(); }
}, { immediate: true });
onBeforeUnmount(cancel);
</script>

<style scoped lang="scss">
.monitor-heading { display: flex; align-items: center; gap: 12px; padding-right: 28px; }
.heading-icon { width: 40px; height: 40px; border-radius: 10px; background: var(--el-color-primary-light-9); color: var(--el-color-primary); display: grid; place-items: center; flex-shrink: 0; }
.heading-copy { min-width: 0; h3 { margin: 0 0 4px; font-size: 18px; line-height: 24px; color: var(--el-text-color-primary); } p { margin: 0; font-size: 13px; color: var(--el-text-color-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; } }
.monitor-body { height: min(560px, calc(90vh - 154px)); min-height: 230px; display: flex; flex-direction: column; gap: 14px; }
.monitor-filters { display: flex; align-items: flex-end; flex-wrap: wrap; gap: 12px; padding: 14px; border-radius: 8px; background: var(--el-fill-color-light); .el-form-item { display: flex; flex-direction: column; align-items: flex-start; margin: 0; width: 136px; :deep(.el-form-item__label) { height: 26px; line-height: 22px; padding: 0; font-size: 12px; } :deep(.el-form-item__content) { width: 100%; } } .time-filter { flex: 1; min-width: 350px; :deep(.el-date-editor) { width: 100%; } } }
.filter-actions { display: flex; gap: 8px; .el-button { margin: 0; gap: 5px; } }
.results-caption { display: flex; gap: 12px; justify-content: space-between; font-size: 12px; color: var(--el-text-color-secondary); b { color: var(--el-text-color-primary); margin-left: 6px; } }
.monitor-table { flex: 1; min-height: 100px; width: 100%; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; }
.sample-detail { padding: 16px 24px; width: min(100%, calc(100vw - 112px)); box-sizing: border-box; display: flex; flex-wrap: wrap; gap: 16px 30px; background: var(--el-fill-color-lighter); font-size: 13px; span { color: var(--el-text-color-secondary); margin-right: 10px; } b { font-weight: 500; } p { width: 100%; margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.7; } }
.monitor-pagination { display: flex; justify-content: flex-end; overflow-x: auto; flex-shrink: 0; }
.monitor-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; > span { font-size: 12px; color: var(--el-text-color-secondary); } }
.load-error { flex-shrink: 0; :deep(.el-alert__description) { overflow-wrap: anywhere; } }
.muted { color: var(--el-text-color-placeholder); }
@media (max-width: 760px) { .monitor-filters { padding: 10px; gap: 8px; .time-filter { min-width: 290px; } } .results-caption > span:last-child { display: none; } .monitor-footer > span { display: none; } .monitor-footer { justify-content: flex-end; } }
</style>
