<template>
  <el-dialog v-model="visible" width="min(960px, calc(100vw - 32px))" top="5vh" destroy-on-close :close-on-click-modal="false">
    <template #header><div class="log-heading"><span class="heading-icon"><Icon icon="el-icon-Document" :size="20" /></span>
      <div class="heading-copy"><h3>异常日志</h3><p :title="taskName">{{ taskName || '当前任务' }}</p></div></div></template>
    <div class="log-body">
      <div class="log-toolbar"><el-segmented v-model="filter" :options="filterOptions" aria-label="日志阶段" />
        <div class="toolbar-actions"><el-button :loading="loading" :disabled="clearing" @click="fetchErrors"><Icon icon="el-icon-Refresh" />刷新</el-button>
          <el-button type="danger" plain :disabled="loading || !errorList.length" :loading="clearing" @click="handleClear">清空日志</el-button></div></div>
      <el-input v-model="keyword" clearable placeholder="搜索错误内容、组件或字段" aria-label="搜索日志"><template #prefix><Icon icon="el-icon-Search" /></template></el-input>
      <el-alert v-if="loadError" type="error" show-icon :closable="false" title="异常日志读取失败" :description="loadError" class="load-error" />
      <div v-loading="loading" class="error-list" aria-live="polite">
        <el-empty v-if="!loading && !loadError && !filteredList.length" :image-size="72" :description="errorList.length ? '没有符合筛选条件的日志' : '暂无异常日志'" />
        <div v-for="item in filteredList" :key="item.id" class="error-item" :class="{ warning: item.level === 'WARNING' }">
          <div class="error-tags"><el-tag :type="item.level === 'WARNING' ? 'warning' : 'danger'" size="small">{{ item.level === 'WARNING' ? '警告' : '错误' }}</el-tag>
            <span class="phase-label">{{ phaseLabel(item.phase) }}</span><time>{{ formatTime(item.occurredAt) }}</time></div>
          <div class="error-message">{{ item.message || '未提供错误摘要' }}</div>
          <div v-if="item.nodeLabel || item.fieldKey" class="error-location"><span v-if="item.nodeLabel">组件：{{ item.nodeLabel }}</span><span v-if="item.fieldKey">字段：{{ item.fieldKey }}</span></div>
          <div v-if="item.suggestion" class="error-suggestion">{{ item.suggestion }}</div>
          <div class="error-actions"><details v-if="item.detail"><summary>技术详情</summary><pre>{{ item.detail }}</pre></details>
            <el-button type="primary" link @click="handleCopy(item)"><Icon icon="el-icon-CopyDocument" />复制错误</el-button></div>
        </div>
      </div>
    </div>
    <template #footer><div class="log-footer"><span>最多保留最近 100 条{{ refreshedAt ? ` · 最近读取 ${refreshedAt}` : '' }}</span><el-button @click="visible = false">关闭</el-button></div></template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import dayjs from 'dayjs';
type ErrorPhase = 'VALIDATION' | 'DEPLOYMENT' | 'RUNTIME';
interface FlowError { id: string; level: string; phase: ErrorPhase; nodeLabel?: string; fieldKey?: string; message: string; detail?: string; suggestion?: string; occurredAt: string; }
const props = defineProps<{ modelValue: boolean; pipelineId: string; taskName?: string }>();
const emit = defineEmits<{ (event: 'update:modelValue', value: boolean): void }>();
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) });
const loading = ref(false), clearing = ref(false), loadError = ref(''), refreshedAt = ref('');
const errorList = ref<FlowError[]>([]), filter = ref('ALL'), keyword = ref('');
let sequence = 0, controller: AbortController | undefined;
const cancel = () => { sequence++; controller?.abort(); loading.value = false; };
const filterOptions = computed(() => [{ label: `全部 ${errorList.value.length}`, value: 'ALL' }, ...[
  ['DEPLOYMENT', '部署'], ['RUNTIME', '运行'], ['VALIDATION', '配置'],
].map(([value, label]) => ({ label: `${label} ${errorList.value.filter(item => item.phase === value).length}`, value }))]);
// 后端按单条流程限量保留 100 条；筛选已读取的诊断记录不发起逐条补查。
const filteredList = computed(() => errorList.value.filter(item => (filter.value === 'ALL' || item.phase === filter.value) &&
  (!keyword.value.trim() || [item.message, item.detail, item.nodeLabel, item.fieldKey, item.suggestion].some(value => String(value || '').toLowerCase().includes(keyword.value.trim().toLowerCase())))));
const phaseLabel = (value: string) => ({ DEPLOYMENT: '部署', RUNTIME: '运行', VALIDATION: '配置校验' }[value] || value || '未知阶段');
const formatTime = (value: string) => value && dayjs(value).isValid() ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '时间未知';
const errorText = (error: any) => {
  const status = error?.response?.status, data = error?.response?.data;
  if (status === 404) return '当前流程不存在或已被移除，请刷新任务列表后重试';
  if (status === 401) return '登录已过期，请重新登录后重试';
  if (status === 403) return '没有查看当前流程日志的权限';
  if (error?.code === 'ECONNABORTED') return '读取超过 30 秒，请稍后重试';
  return String(data?.msg || data?.message || data?.error || error?.message || '无法连接日志服务，请稍后重试');
};
const unwrap = (response: any) => {
  const body = response.data;
  if (body?.code != null && ![0, 1, 200].includes(Number(body.code))) throw new Error(body.msg || body.message || `请求失败（${body.code}）`);
  return body?.code != null ? body.data : body;
};
const fetchErrors = async () => {
  if (!props.modelValue) return;
  controller?.abort(); controller = new AbortController();
  const requestId = ++sequence;
  loading.value = true; loadError.value = ''; errorList.value = []; refreshedAt.value = '';
  try {
    if (!props.pipelineId?.trim()) throw new Error('当前任务尚未关联流程，暂时无法读取异常日志');
    const response = await (window as any).$request.get(`/nifi/api/pipelines/${encodeURIComponent(props.pipelineId)}/errors`, {
      responsePolicy: 'raw', errorPolicy: 'silent', timeout: 30000, signal: controller.signal,
    });
    const data = unwrap(response);
    if (!Array.isArray(data)) throw new Error('日志接口返回格式异常，请联系管理员检查接口版本');
    if (requestId !== sequence) return;
    errorList.value = data; refreshedAt.value = dayjs().format('HH:mm:ss');
  } catch (error: any) { if (requestId === sequence && error?.code !== 'ERR_CANCELED') loadError.value = errorText(error); }
  finally { if (requestId === sequence) loading.value = false; }
};
const handleClear = async () => {
  const id = props.pipelineId;
  try { await ElMessageBox.confirm('清空当前流程保留的异常日志？此操作不会停止任务。', '清空日志', { type: 'warning', confirmButtonText: '清空', cancelButtonText: '取消' }); }
  catch { return; }
  if (!props.modelValue || id !== props.pipelineId) return;
  clearing.value = true; loadError.value = '';
  try {
    unwrap(await (window as any).$request.post(`/nifi/api/pipelines/delete/${encodeURIComponent(id)}/errors`, {}, { responsePolicy: 'raw', errorPolicy: 'silent', timeout: 30000 }));
    if (props.modelValue && id === props.pipelineId) { ElMessage.success('已清空异常日志'); await fetchErrors(); }
  } catch (error: any) { if (props.modelValue && id === props.pipelineId) loadError.value = `清空失败：${errorText(error)}`; }
  finally { clearing.value = false; }
};
const handleCopy = (item: FlowError) => (window as any).$common.copyText([
  `[${phaseLabel(item.phase)}/${item.level}] ${item.message}`, item.nodeLabel ? `组件：${item.nodeLabel}` : '',
  item.fieldKey ? `字段：${item.fieldKey}` : '', item.detail ? `详情：${item.detail}` : '',
  item.suggestion ? `建议：${item.suggestion}` : '', `时间：${formatTime(item.occurredAt)}`,
].filter(Boolean).join('\n'));
watch(() => [props.modelValue, props.pipelineId] as const, ([open]) => {
  cancel(); errorList.value = []; loadError.value = ''; refreshedAt.value = ''; filter.value = 'ALL'; keyword.value = '';
  if (open) fetchErrors();
}, { immediate: true });
onBeforeUnmount(cancel);
</script>

<style scoped lang="scss">
.log-heading { display: flex; align-items: center; gap: 12px; padding-right: 28px; }
.heading-icon { width: 40px; height: 40px; border-radius: 10px; background: var(--el-color-primary-light-9); color: var(--el-color-primary); display: grid; place-items: center; flex-shrink: 0; }
.heading-copy { min-width: 0; h3 { margin: 0 0 4px; font-size: 18px; line-height: 24px; color: var(--el-text-color-primary); } p { margin: 0; font-size: 13px; color: var(--el-text-color-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; } }
.log-body { display: flex; flex-direction: column; gap: 14px; height: min(540px, calc(90vh - 154px)); min-height: 230px; }
.log-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.toolbar-actions { display: flex; gap: 8px; .el-button { margin: 0; gap: 5px; } }
.error-list { flex: 1; min-height: 100px; overflow-y: auto; padding: 1px 4px 1px 1px; }
.error-item { border: 1px solid var(--el-border-color-lighter); border-left: 3px solid var(--el-color-danger); border-radius: 8px; padding: 14px 16px; background: var(--el-bg-color); margin-bottom: 12px; &:last-child { margin-bottom: 0; } &.warning { border-left-color: var(--el-color-warning); } }
.error-tags { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; time { font-size: 12px; color: var(--el-text-color-secondary); margin-left: auto; } }
.phase-label { font-size: 12px; color: var(--el-text-color-secondary); }
.error-message { font-size: 14px; line-height: 1.7; font-weight: 500; color: var(--el-text-color-primary); margin-top: 10px; overflow-wrap: anywhere; white-space: pre-wrap; }
.error-location { display: flex; flex-wrap: wrap; gap: 8px 20px; font-size: 12px; color: var(--el-text-color-secondary); margin-top: 6px; overflow-wrap: anywhere; }
.error-suggestion { margin-top: 10px; padding: 8px 10px; background: var(--el-color-primary-light-9); color: var(--el-color-primary); font-size: 13px; border-radius: 4px; line-height: 1.6; overflow-wrap: anywhere; }
.error-actions { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-top: 10px; details { min-width: 0; flex: 1; } summary { cursor: pointer; color: var(--el-text-color-secondary); font-size: 12px; line-height: 24px; } pre { margin: 8px 0 0; padding: 12px; border-radius: 6px; background: var(--el-fill-color-light); font-size: 12px; line-height: 1.65; max-height: 220px; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; } .el-button { margin-left: auto; gap: 5px; } }
.log-footer { display: flex; justify-content: space-between; align-items: center; gap: 12px; > span { font-size: 12px; color: var(--el-text-color-secondary); } }
.load-error { flex-shrink: 0; :deep(.el-alert__description) { overflow-wrap: anywhere; } }
@media (max-width: 640px) { .log-toolbar { gap: 10px; } .log-toolbar :deep(.el-segmented) { width: 100%; } .toolbar-actions { margin-left: auto; } .error-item { padding: 12px; } }
</style>
