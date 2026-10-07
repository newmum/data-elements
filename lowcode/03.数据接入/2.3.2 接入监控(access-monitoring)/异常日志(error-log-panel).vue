<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    :draggable="false"
    width="900px"
    class="error-log-dialog"
    destroy-on-close
  >
    <!-- 顶部工具栏 -->
    <div class="error-toolbar">
      <div class="toolbar-left">
        <el-segmented v-model="filter" :options="filterOptions" @change="handleFilterChange" />
      </div>
      <div class="toolbar-right">
        <el-button @click="fetchErrors">
          <Icon icon="el-icon-refresh" class="mr-1" size="13"></Icon>
          刷新
        </el-button>
        <el-button type="danger" :disabled="!errorList.length" plain @click="handleClear">
          <Icon icon="el-icon-delete" class="mr-1" size="13"></Icon>
          清空
        </el-button>
      </div>
    </div>

    <!-- 错误列表 -->
    <div v-loading="loading" class="error-list">
      <empty v-if="!loading && filteredList.length === 0" />
      <template v-else>
        <div v-for="item in filteredList" :key="item.id" class="error-item">
          <!-- 左侧色带 -->
          <div class="error-band" :style="{ background: bandColor(item) }" />
          <!-- 内容区 -->
          <div class="error-content">
            <!-- 标签行 -->
            <div class="error-tags">
              <el-tag
                :type="item.level === 'ERROR' ? 'danger' : 'warning'"
                size="small"
                effect="dark"
              >
                {{ item.level === "ERROR" ? "错误" : "警告" }}
              </el-tag>
              <el-tag size="small" effect="plain">{{ phaseLabel(item.phase) }}</el-tag>
              <el-tag v-if="item.nodeLabel" size="small" effect="plain" type="primary">
                {{ item.nodeLabel }}
              </el-tag>
              <el-tag v-if="item.fieldKey" size="small" effect="plain" type="info">
                {{ item.fieldKey }}
              </el-tag>
              <span class="error-time">
                <el-tooltip
                  :content="dayjs(item.occurredAt).format('YYYY-MM-DD HH:mm:ss')"
                  placement="top"
                >
                  <span>{{ relativeTime(item.occurredAt) }}</span>
                </el-tooltip>
              </span>
            </div>

            <!-- 错误消息 -->
            <div class="error-message">{{ item.message }}</div>

            <!-- 详情 -->
            <div v-if="item.detail" class="error-detail">{{ item.detail }}</div>

            <!-- 建议 -->
            <div v-if="item.suggestion" class="error-suggestion">建议：{{ item.suggestion }}</div>

            <!-- 操作按钮 -->
            <div class="error-actions">
              <el-button
                size="small"
                plain
                type="primary"
                style="align-items: baseline"
                @click="handleCopy(item)"
              >
                <Icon icon="el-icon-CopyDocument" class="mr-1" size="13"></Icon>
                复制错误
              </el-button>
            </div>
          </div>
        </div>
      </template>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="visible = false">关闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import dayjs from "dayjs";

// ======================== 类型定义 ========================

type ErrorPhase = "VALIDATION" | "DEPLOYMENT" | "RUNTIME";
type ErrorLevel = "ERROR" | "WARNING";

interface FlowError {
  id: string;
  level: ErrorLevel;
  phase: ErrorPhase;
  nodeId?: string | null;
  nodeLabel?: string | null;
  fieldKey?: string | null;
  message: string;
  detail?: string | null;
  suggestion?: string | null;
  occurredAt: string;
}

type FilterValue = "ALL" | ErrorPhase;

// ======================== Props & Emits ========================

interface Props {
  modelValue: boolean;
  pipelineId: string;
  taskName?: string;
}

const props = withDefaults(defineProps<Props>(), {
  taskName: "",
});

const emit = defineEmits<{
  (e: "update:modelValue", val: boolean): void;
}>();

// ======================== 弹窗显隐 ========================

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit("update:modelValue", val),
});

const dialogTitle = computed(() => {
  const name = props.taskName || props.pipelineId || "";
  return name ? `${name}` : "异常日志";
});

// ======================== 数据状态 ========================

const loading = ref(false);
const errorList = ref<FlowError[]>([]);
const filter = ref<FilterValue>("ALL");

// ======================== 筛选选项 ========================

const filterOptions = computed(() => [
  { label: `全部 (${errorList.value.length})`, value: "ALL" as FilterValue },
  {
    label: `部署 (${errorList.value.filter((e) => e.phase === "DEPLOYMENT").length})`,
    value: "DEPLOYMENT" as FilterValue,
  },
  {
    label: `运行 (${errorList.value.filter((e) => e.phase === "RUNTIME").length})`,
    value: "RUNTIME" as FilterValue,
  },
  {
    label: `配置 (${errorList.value.filter((e) => e.phase === "VALIDATION").length})`,
    value: "VALIDATION" as FilterValue,
  },
]);

const filteredList = computed(() => {
  if (filter.value === "ALL") return errorList.value;
  return errorList.value.filter((e) => e.phase === filter.value);
});

// ======================== 工具函数 ========================

const relativeTime = (iso: string): string => {
  const t = new Date(iso).getTime();
  if (Number.isNaN(t)) return iso;
  const diff = Math.floor((Date.now() - t) / 1000);
  if (diff < 5) return "刚刚";
  if (diff < 60) return `${diff}秒前`;
  if (diff < 3600) return `${Math.floor(diff / 60)}分钟前`;
  if (diff < 86400) return `${Math.floor(diff / 3600)}小时前`;
  return new Date(iso).toLocaleString();
};

const bandColor = (e: FlowError): string => {
  if (e.level === "WARNING") return "#faad14";
  switch (e.phase) {
    case "VALIDATION":
      return "#fa8c16";
    case "DEPLOYMENT":
      return "#ff4d4f";
    case "RUNTIME":
      return "#ff4d4f";
  }
};

const phaseLabel = (p: ErrorPhase): string => {
  return p === "VALIDATION" ? "配置错误" : p === "DEPLOYMENT" ? "部署错误" : "运行错误";
};

// ======================== API 请求 ========================

const fetchErrors = async () => {
  if (!props.pipelineId) return;
  loading.value = true;
  try {
    const response = await $common.get(`/pipelines/${props.pipelineId}/errors`);
    const data = response.data || response;
    errorList.value = Array.isArray(data) ? data : [];
  } catch (error) {
    console.error("获取错误列表失败:", error);
  } finally {
    loading.value = false;
  }
};

const clearErrors = async () => {
  if (!props.pipelineId) return;
  try {
    await $common.post(`/pipelines/delete/${props.pipelineId}/errors`);
    ElMessage.success("已清空错误记录");
    await fetchErrors();
  } catch (error: any) {
    ElMessage.error(`清空失败: ${error?.message ?? error}`);
  }
};

// ======================== 操作处理 ========================

const handleFilterChange = () => {
  // 筛选切换，无需额外处理
};

const handleClear = async () => {
  try {
    await ElMessageBox.confirm("是否清空所有错误记录？", "提示", {
      type: "warning",
      confirmButtonText: "确定",
      cancelButtonText: "取消",
    });
    await clearErrors();
  } catch {
    // 用户取消
  }
};

const handleCopy = (e: FlowError) => {
  const txt = [
    `[${phaseLabel(e.phase)}/${e.level}] ${e.message}`,
    e.nodeLabel ? `节点: ${e.nodeLabel}` : null,
    e.fieldKey ? `字段: ${e.fieldKey}` : null,
    e.detail ? `详情: ${e.detail}` : null,
    e.suggestion ? `建议: ${e.suggestion}` : null,
    `时间: ${dayjs(e.occurredAt).format("YYYY-MM-DD HH:mm:ss")}`,
  ]
    .filter(Boolean)
    .join("\n");
  $common.copyText(txt);
};

watch(
  () => [props.modelValue, props.pipelineId] as const,
  ([visible, id]) => {
    if (visible && id) {
      fetchErrors();
    }
  }
);
</script>

<style scoped lang="scss">
.error-log-dialog :deep(.el-dialog__body) {
  padding: 12px 20px 20px;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.error-log-dialog {
  /* 工具栏 */
  .error-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;
  }

  .toolbar-right {
    display: flex;
    gap: 8px;
  }

  /* 错误列表 */
  .error-list {
    flex: 1;
    overflow-y: auto;
    min-height: 200px;
    max-height: 480px;
  }

  /* 错误项 */
  .error-item {
    display: flex;
    border-radius: 6px;
    background: #fafafa;
    border: 1px solid #f0f0f0;
    overflow: hidden;
    margin-bottom: 8px;
  }

  .error-item:last-child {
    margin-bottom: 0;
  }

  .error-band {
    width: 4px;
    flex-shrink: 0;
  }

  .error-content {
    flex: 1;
    padding: 8px 12px;
    min-width: 0;
  }

  /* 标签行 */
  .error-tags {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 4px;
    flex-wrap: wrap;
  }

  .error-time {
    margin-left: auto;
    font-size: 12px;
    color: #8c8c8c;
    cursor: default;
    white-space: nowrap;
  }

  /* 错误消息 */
  .error-message {
    font-size: 13px;
    color: #262626;
    word-break: break-word;
    line-height: 1.5;
  }

  /* 详情 */
  .error-detail {
    font-size: 12px;
    color: #595959;
    margin-top: 4px;
    word-break: break-word;
    line-height: 1.5;
  }

  /* 建议 */
  .error-suggestion {
    font-size: 12px;
    color: #1677ff;
    margin-top: 4px;
    line-height: 1.5;
  }

  /* 操作按钮 */
  .error-actions {
    margin-top: 6px;
    display: flex;
    gap: 8px;
  }

  /* 底栏 */
  .dialog-footer {
    display: flex;
    justify-content: flex-end;
  }
}
</style>
