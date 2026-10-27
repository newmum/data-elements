<template>
  <el-dialog
    v-model="open"
    title="服务登记"
    fullscreen
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    :before-close="beforeClose"
    class="service-registration-dialog"
    @closed="onClosed"
  >
    <div class="service-workspace">
      <aside class="service-workspace-sidebar" aria-label="服务登记列表">
        <div class="service-sidebar-heading">
          <h3>服务列表</h3>
          <el-button type="primary" link :disabled="busy" @click="addDraft">
            <Icon icon="el-icon-Plus" class="mr-1" />
            添加服务
          </el-button>
        </div>
        <el-input
          v-model.trim="keyword"
          placeholder="搜索已登记服务"
          clearable
          class="service-sidebar-search"
          @keyup.enter="loadServices(1)"
          @clear="loadServices(1)"
        >
          <template #suffix>
            <el-button link title="搜索已登记服务" :loading="listLoading" @click="loadServices(1)">
              <Icon icon="search" />
            </el-button>
          </template>
        </el-input>
        <div class="service-sidebar-items" :aria-busy="listLoading">
          <p class="service-list-label">本次登记 · {{ entries.length }}</p>
          <div v-for="entry in entries" :key="entry.key" class="service-list-row">
            <button
              type="button"
              class="service-list-item"
              :class="{ 'is-selected': activeKey === entry.key }"
              :aria-pressed="activeKey === entry.key"
              :disabled="busy"
              @click="selectEntry(entry)"
            >
              <Icon icon="el-icon-Connection" class="service-list-icon" />
              <span class="service-item-copy">
                <strong :title="entry.name">{{ entry.name }}</strong>
                <small>{{ entry.id ? (entry.dirty ? "未保存修改" : "已保存") : "未保存" }}</small>
              </span>
              <Icon v-if="entry.id && !entry.dirty" icon="el-icon-CircleCheck" class="is-saved" />
            </button>
            <el-button
              v-if="!entry.id"
              link
              class="service-draft-remove"
              :disabled="busy"
              :aria-label="`移除${entry.name}`"
              :title="`移除${entry.name}`"
              @click="removeDraft(entry)"
            >
              <Icon icon="el-icon-Close" />
            </el-button>
          </div>
          <p class="service-list-label">已登记服务 · {{ total }}</p>
          <div v-if="listError" class="service-list-feedback" role="alert">
            <span>{{ listError }}</span>
            <el-button type="primary" link @click="loadServices(page)">重新加载</el-button>
          </div>
          <template v-else>
            <button
              v-for="service in visibleServices"
              :key="service.id"
              type="button"
              class="service-list-item"
              :disabled="busy"
              @click="selectService(service)"
            >
              <Icon icon="el-icon-Connection" class="service-list-icon" />
              <span class="service-item-copy">
                <strong :title="service.name">{{ service.name }}</strong>
                <small>{{ service.method }} · {{ service.code || "未设置编码" }}</small>
              </span>
            </button>
            <p v-if="listLoading" class="service-list-feedback" role="status">正在加载服务列表…</p>
            <p v-else-if="!services.length" class="service-list-feedback">
              {{
                keyword ? "没有匹配的服务，请调整搜索条件" : "暂无已登记服务，可添加服务开始登记"
              }}
            </p>
          </template>
        </div>
        <el-pagination
          v-if="total > pageSize"
          small
          class="service-sidebar-pagination"
          layout="prev, pager, next"
          :pager-count="5"
          :current-page="page"
          :page-size="pageSize"
          :total="total"
          :disabled="listLoading"
          @current-change="loadServices"
        />
      </aside>
      <main
        v-loading="switching"
        class="service-workspace-editor"
        @input.capture="scheduleCapture"
        @change.capture="scheduleCapture"
      >
        <el-scrollbar ref="editorScrollRef">
          <div class="service-editor-inner">
            <ApiRegisterContent
              ref="contentRef"
              :data="editorData"
              context="modal"
              @ready="onEditorReady"
              @saved="onSaved"
              @change="scheduleCapture"
            />
          </div>
        </el-scrollbar>
      </main>
    </div>
    <template #footer>
      <el-button :disabled="busy" @click="requestClose">关闭</el-button>
      <el-button
        type="primary"
        plain
        :disabled="!editorReady || switching"
        :loading="taskLoading"
        @click="saveCurrent(false)"
      >
        <Icon icon="save" class="mr-1" />
        保存登记
      </el-button>
      <el-button
        type="primary"
        :disabled="!editorReady || switching"
        :loading="taskLoading"
        @click="saveCurrent(true)"
      >
        保存并新增
        <Icon icon="el-icon-Plus" class="ml-1" />
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";

type RecordValue = Record<string, unknown>;
interface Entry {
  key: string;
  id: string;
  name: string;
  record: RecordValue;
  dirty: boolean;
  baseline?: string;
}
interface ServiceItem {
  id: string;
  name: string;
  code: string;
  method: string;
}
interface EditorApi {
  handleSave(): Promise<boolean>;
  getPayload(): Promise<RecordValue>;
}
const props = withDefaults(defineProps<{ modelValue: boolean; apiDetail?: RecordValue }>(), {
  apiDetail: () => ({}),
});
const emit = defineEmits<{
  (event: "update:modelValue", value: boolean): void;
  (event: "close", result: "cancel" | "success"): void;
}>();
const open = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit("update:modelValue", value),
});
const contentRef = ref<EditorApi>();
const editorScrollRef = ref<{ setScrollTop(value: number): void }>();
const entries = ref<Entry[]>([]),
  services = ref<ServiceItem[]>([]);
const activeKey = ref(""),
  editorData = ref<RecordValue>({});
const keyword = ref(""),
  page = ref(1),
  total = ref(0);
const pageSize = 20;
const listLoading = ref(false),
  listError = ref("");
const editorReady = ref(false),
  switching = ref(false),
  taskLoading = ref(false);
const savedAny = ref(false);
const busy = computed(() => switching.value || taskLoading.value);
const activeEntry = computed(() => entries.value.find((entry) => entry.key === activeKey.value));
const visibleServices = computed(() => {
  const opened = new Set(entries.value.map((entry) => entry.id).filter(Boolean));
  return services.value.filter((service) => !opened.has(service.id));
});
let sequence = 0,
  listVersion = 0;
let captureTimer: ReturnType<typeof setTimeout> | undefined;
const recordOf = (value: unknown): RecordValue =>
  value !== null && typeof value === "object" && !Array.isArray(value)
    ? (value as RecordValue)
    : {};
const textOf = (value: unknown): string => (value == null ? "" : String(value));
const payloadOf = (response: unknown): RecordValue => {
  const value = recordOf(response);
  return recordOf(value.data ?? value);
};
const clone = (value: RecordValue): RecordValue => JSON.parse(JSON.stringify(value));
const signature = (value: RecordValue): string => JSON.stringify(value);
const errorMessage = (error: unknown): string =>
  error instanceof Error ? error.message : "服务列表加载失败，请重试";
const newEntry = (data: RecordValue = {}): Entry => {
  const id = textOf(data.id || data.tid),
    number = ++sequence;
  return {
    key: id ? `service-${id}` : `draft-${number}`,
    id,
    name: textOf(data.serviceName || data.name) || `新建服务 ${number}`,
    record: clone(data),
    dirty: false,
  };
};
const loadServices = async (nextPage = 1, requestedSize = pageSize) => {
  const version = ++listVersion;
  page.value = nextPage;
  listLoading.value = true;
  listError.value = "";
  try {
    const payload = payloadOf(
      await $common.post("/dws/flowserve/gateway/api/list", {
        departmentOnly: true,
        serviceCreateType: "api",
        pageNum: nextPage,
        pageSize: Math.min(100, Math.max(1, requestedSize)),
        keyword: keyword.value,
        sortField: "updatedTime",
        sortDir: "desc",
      })
    );
    if (version !== listVersion) return;
    services.value = (Array.isArray(payload.list) ? payload.list : [])
      .map((value) => {
        const row = recordOf(value);
        return {
          id: textOf(row.id || row.tid),
          name: textOf(row.serviceName || row.name),
          code: textOf(row.serviceCode || row.code),
          method: textOf(row.method) || "GET",
        };
      })
      .filter((row) => row.id);
    total.value = Number(payload.total || 0);
  } catch (error) {
    if (version === listVersion) listError.value = errorMessage(error);
  } finally {
    if (version === listVersion) listLoading.value = false;
  }
};
const captureCurrent = async () => {
  const entry = activeEntry.value;
  if (!entry || !editorReady.value || !contentRef.value) return;
  const payload = await contentRef.value.getPayload();
  if (activeKey.value !== entry.key) return;
  entry.baseline ??= signature(payload);
  entry.record = clone(payload);
  entry.name = textOf(payload.name).trim() || entry.name;
  entry.dirty = signature(payload) !== entry.baseline;
};
const onEditorReady = async (ready: boolean) => {
  editorReady.value = ready;
  if (ready) await captureCurrent();
};
const scheduleCapture = () => {
  clearTimeout(captureTimer);
  captureTimer = setTimeout(() => void captureCurrent(), 160);
};
const activate = (entry: Entry) => {
  clearTimeout(captureTimer);
  editorReady.value = false;
  activeKey.value = entry.key;
  editorData.value = clone(entry.record);
  editorScrollRef.value?.setScrollTop(0);
};
const selectEntry = async (entry: Entry) => {
  if (busy.value || entry.key === activeKey.value) return;
  switching.value = true;
  try {
    await captureCurrent();
    activate(entry);
  } finally {
    switching.value = false;
  }
};
const addDraft = async () => {
  if (busy.value) return;
  if (entries.value.length >= 100) {
    ElMessage.warning("本次最多同时编辑100条服务，请保存并关闭后继续登记");
    return;
  }
  switching.value = true;
  try {
    await captureCurrent();
    const entry = newEntry({
      groupId: props.apiDetail.groupId || "api_service_group_default",
    });
    entries.value.push(entry);
    activate(entry);
  } finally {
    switching.value = false;
  }
};
const selectService = async (service: ServiceItem) => {
  if (busy.value) return;
  const existing = entries.value.find((entry) => entry.id === service.id);
  if (existing) return selectEntry(existing);
  if (entries.value.length >= 100) {
    ElMessage.warning("本次最多同时编辑100条服务，请关闭后再选择其他服务");
    return;
  }
  switching.value = true;
  try {
    await captureCurrent();
    // 详情仅由用户主动点击读取，列表加载不逐条补查。
    const detail = payloadOf(
      await $common.post("/dws/flowserve/gateway/api/detail", {
        apiId: service.id,
      })
    );
    if (textOf(detail.id || detail.tid) !== service.id)
      throw new Error("服务详情标识不一致，请刷新后重试");
    const entry = newEntry(detail);
    entries.value.push(entry);
    activate(entry);
  } catch (error) {
    if (!recordOf(error).handled) ElMessage.error(errorMessage(error));
  } finally {
    switching.value = false;
  }
};
const onSaved = (payload: RecordValue) => {
  const entry = activeEntry.value;
  if (!entry) return;
  entry.id = textOf(payload.id);
  entry.record = clone(payload);
  entry.name = textOf(payload.name) || entry.name;
  entry.baseline = signature(payload);
  entry.dirty = false;
  savedAny.value = true;
};
const saveCurrent = async (addAfterSave: boolean) => {
  if (busy.value || !editorReady.value || !contentRef.value) return;
  taskLoading.value = true;
  let saved = false;
  try {
    saved = await contentRef.value.handleSave();
  } finally {
    taskLoading.value = false;
  }
  if (!saved) return;
  void loadServices(page.value);
  if (addAfterSave) await addDraft();
};
const removeDraft = async (entry: Entry) => {
  if (busy.value || entry.id) return;
  await captureCurrent();
  if (entry.dirty) {
    try {
      await ElMessageBox.confirm(
        `“${entry.name}”尚未保存，移除将丢弃填写内容。`,
        "移除待登记服务",
        {
          type: "warning",
          confirmButtonText: "移除",
          cancelButtonText: "继续编辑",
        }
      );
    } catch {
      return;
    }
  }
  const wasActive = activeKey.value === entry.key;
  entries.value = entries.value.filter((item) => item.key !== entry.key);
  if (wasActive) {
    if (!entries.value.length) entries.value.push(newEntry({ groupId: props.apiDetail.groupId }));
    activate(entries.value[0]);
  }
};
const canClose = async (): Promise<boolean> => {
  if (busy.value) return false;
  await captureCurrent();
  const dirtyCount = entries.value.filter((entry) => entry.dirty).length;
  if (!dirtyCount) return true;
  try {
    await ElMessageBox.confirm(
      `还有${dirtyCount}条服务的填写内容未保存，关闭将丢弃这些修改。已保存的服务会保留。`,
      "关闭服务登记",
      {
        type: "warning",
        confirmButtonText: "放弃并关闭",
        cancelButtonText: "继续登记",
      }
    );
    return true;
  } catch {
    return false;
  }
};
const requestClose = async () => {
  if (await canClose()) open.value = false;
};
const beforeClose = async (done: () => void) => {
  if (await canClose()) done();
};
const onClosed = () => emit("close", savedAny.value ? "success" : "cancel");
const startWorkspace = () => {
  clearTimeout(captureTimer);
  sequence = 0;
  savedAny.value = false;
  keyword.value = "";
  services.value = [];
  total.value = 0;
  const entry = newEntry(props.apiDetail);
  entries.value = [entry];
  activate(entry);
  void loadServices(1);
};
watch(
  () => props.modelValue,
  (value) => {
    if (value) startWorkspace();
    else {
      ++listVersion;
      clearTimeout(captureTimer);
    }
  },
  { immediate: true }
);
onBeforeUnmount(() => {
  ++listVersion;
  clearTimeout(captureTimer);
});
</script>

<style>
.service-registration-dialog.el-dialog {
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
  width: 100%;
  height: 100vh;
  height: 100dvh;
  max-width: 100vw;
  margin: 0;
}
.service-registration-dialog .el-dialog__header {
  flex-shrink: 0;
  height: 46px;
  box-sizing: border-box;
  padding: 12px 20px;
  margin: 0;
  border-bottom: 1px solid #ebeef5;
}
.service-registration-dialog .el-dialog__title {
  font-size: 16px;
  font-weight: 600;
  color: #25344b;
}
.service-registration-dialog .el-dialog__headerbtn {
  top: 0;
  width: 46px;
  height: 46px;
}
.service-registration-dialog .el-dialog__body {
  display: flex;
  flex: 1 1 0;
  flex-direction: column;
  min-height: 0;
  padding: 0;
  overflow: hidden;
}
.service-registration-dialog .el-dialog__footer {
  flex-shrink: 0;
  display: flex;
  justify-content: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 10px 24px;
  border-top: 1px solid #ebeef5;
}
.service-registration-dialog .el-dialog__footer .el-button + .el-button {
  margin-left: 0;
}
</style>
<style scoped>
.service-workspace {
  display: grid;
  grid-template-columns: clamp(224px, 23%, 284px) minmax(0, 1fr);
  flex: 1;
  width: 100%;
  min-width: 0;
  min-height: 0;
  color: #25344b;
}
.service-workspace-sidebar {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  border-right: 1px solid #ebeef5;
  background: #fff;
}
.service-sidebar-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 14px 16px 10px;
}
.service-sidebar-heading h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}
.service-sidebar-search {
  width: auto;
  margin: 0 12px 10px;
}
.service-sidebar-items {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}
.service-list-label {
  margin: 0;
  padding: 10px 16px 8px;
  font-size: 12px;
  line-height: 18px;
  color: #758298;
}
.service-list-row {
  position: relative;
}
.service-list-row .service-list-item {
  padding-right: 36px;
}
.service-list-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-width: 0;
  padding: 12px 16px;
  text-align: left;
  color: #34445b;
  background: #fff;
  border: 0;
  border-left: 3px solid transparent;
  border-bottom: 1px solid #f0f2f6;
  cursor: pointer;
  transition: background-color 150ms;
}
.service-list-item:hover {
  background: #f7f9fd;
}
.service-list-item.is-selected {
  background: #edf5ff;
  border-left-color: var(--el-color-primary);
}
.service-list-item:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: -3px;
}
.service-list-item:disabled {
  cursor: wait;
  opacity: 0.65;
}
.service-list-icon {
  flex-shrink: 0;
  color: var(--el-color-primary);
  font-size: 18px;
}
.service-item-copy {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  gap: 5px;
}
.service-item-copy strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 500;
  line-height: 20px;
}
.service-item-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  line-height: 16px;
  color: #758298;
}
.is-saved {
  flex-shrink: 0;
  color: #18a36b;
}
.service-draft-remove {
  position: absolute;
  right: 10px;
  top: 23px;
}
.service-list-feedback {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  margin: 0;
  padding: 12px 16px;
  font-size: 12px;
  line-height: 20px;
  color: #758298;
}
.service-sidebar-pagination {
  justify-content: center;
  flex-shrink: 0;
  padding: 10px 2px;
  border-top: 1px solid #ebeef5;
}
.service-workspace-editor {
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}
.service-workspace-editor :deep(.el-scrollbar) {
  height: 100%;
}
.service-editor-inner {
  padding: 8px 20px 20px;
  min-width: 0;
  box-sizing: border-box;
}
@media (max-width: 600px) {
  .service-workspace { grid-template-columns: minmax(0, 1fr) !important; grid-template-rows: minmax(108px, 30%) minmax(0, 1fr); }
  .service-workspace-sidebar { border-right: 0; border-bottom: 1px solid #ebeef5; }
  .service-sidebar-heading { padding: 8px 12px; }
}
@media (max-width: 1000px) {
  .service-workspace {
    grid-template-columns: 224px minmax(0, 1fr);
  }
  .service-editor-inner {
    padding-right: 14px;
    padding-left: 14px;
  }
}
@media (max-width: 720px) {
  .service-workspace {
    grid-template-columns: 192px minmax(0, 1fr);
  }
  .service-sidebar-heading {
    flex-wrap: wrap;
    padding: 12px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .service-list-item {
    transition: none;
  }
}
</style>
