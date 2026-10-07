<template>
  <el-dialog v-model="visible" :title="dialogTitle" :draggable="false" width="1400px" class="delay-monitor-dialog">
    <div class="search-bar">
      <JsonForm
        ref="jsonFormRef"
        :rules="searchRules"
        :data="searchForm"
        :options="jsonFormOptions"
      />
      <div class="search-actions">
        <el-button type="primary" @click="handleSearch">
          <Icon icon="el-icon-Search" class="mr-1" />
          查询
        </el-button>
        <el-button @click="handleReset">
          <Icon icon="el-icon-Refresh" class="mr-1" />
          重置
        </el-button>
      </div>
    </div>

    <DataTable ref="tableRef" :columns="tableColumns" :data="fetchData" show-index>
      <!-- 是否超时 -->
      <template #column-isTimeout="{ row }">
        <span
          :style="{
            color: row.isTimeout === 1 ? '#f56c6c' : '#67c23a',
            fontWeight: row.isTimeout === 1 ? '600' : '400',
          }"
        >
          {{ row.isTimeout === 1 ? "是" : "否" }}
        </span>
      </template>
    </DataTable>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="visible = false">关闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import dayjs from "dayjs";
import type { UColumnProps } from "@/components/data/data-table.vue";
import { FormUtils } from "@/utils/form";

// ======================== Props & Emits ========================
const props = defineProps<{
  modelValue: boolean;
  taskId: string;
  taskName?: string;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", val: boolean): void;
}>();

// ======================== 弹窗显隐 ========================
const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit("update:modelValue", val),
});

const dialogTitle = computed(() => {
  return props.taskName ? `${props.taskName}` : "延时监控";
});

// ======================== 搜索表单（JsonForm） ========================
const today = dayjs().format("YYYY-MM-DD");

const searchForm = ref({
  monitorStatus: "",
  beginTime: `${today} 00:00:00`,
  endTime: `${today} 23:59:59`,
});

const jsonFormRef = ref();

const jsonFormOptions = {
  form: {
    inline: true,
    labelWidth: "90px",
    labelPosition: "right" as const,
    colon: false,
    showMessage: false,
  },
  submitBtn: false,
  resetBtn: false,
};

const searchRules = FormUtils.fixJson([
  {
    type: "dict-select",
    field: "monitorStatus",
    title: "监控状态",
    props: {
      options: "taskMonitorStatus",
    },
    style: {
      width: "120px",
    },
  },
  {
    type: "datePicker",
    field: "beginTime",
    title: "开始时间",
    props: {
      type: "datetime",
      format: "YYYY-MM-DD HH:mm:ss",
      valueFormat: "YYYY-MM-DD HH:mm:ss",
    },
  },
  {
    type: "datePicker",
    field: "endTime",
    title: "结束时间",
    props: {
      type: "datetime",
      format: "YYYY-MM-DD HH:mm:ss",
      valueFormat: "YYYY-MM-DD HH:mm:ss",
    },
  },
]);

// ======================== DataTable ========================
const tableRef = ref();

const tableColumns = ref<UColumnProps[]>([
  {
    prop: "delayMsEstimate",
    label: "估算延时(ms)",
  },
  {
    prop: "thresholdMs",
    label: "超时阈值(ms)",
  },
  {
    prop: "isTimeout",
    label: "是否超时",
    width: 90,
    align: "center",
  },
  {
    prop: "queuedCount",
    label: "排队数量",
  },
  {
    prop: "queuedBytes",
    label: "排队字节数",
  },
  {
    prop: "activeThreadCount",
    label: "活动线程数",
  },
  {
    prop: "flowFilesIn",
    label: "流入文件数",
  },
  {
    prop: "flowFilesOut",
    label: "流出文件数",
  },
  {
    prop: "bytesIn",
    label: "流入字节数",
  },
  {
    prop: "bytesOut",
    label: "流出字节数",
  },
  {
    prop: "monitorStatus",
    label: "监控状态",
    width: 100,
    align: "center",
    options: "taskMonitorStatus",
  },
  {
    prop: "monitorMsg",
    label: "监控消息",
    minWidth: 220,
    showOverflowTooltip: true,
  },
  {
    prop: "monitorTime",
    label: "监控时间",
    width: 180,
  },
]);

// ======================== API 调用 ========================

/** DataTable 的 data 函数 */
const fetchData = async ({
  pageNo: pageNum,
  pageSize,
}: {
  pageNo: number;
  pageSize: number;
}): Promise<{ list: any[]; total: number }> => {
  // 从 JsonForm 获取实时表单值（搜索结果），回退到 searchForm ref
  const formData = jsonFormRef.value?.formData() || searchForm.value;

  const params: Record<string, any> = {
    pageNum,
    pageSize,
    taskId: props.taskId,
    monitorStatus: formData.monitorStatus,
    beginTime: formData.beginTime || "",
    endTime: formData.endTime || "",
  };

  const response = await (window as any).$common.post(
    "/ods/task/monitorSnapPage",
    params
  );

  const data = response.data || response;
  return {
    list: data.list || [],
    total: data.total || 0,
  };
};

// ======================== 搜索操作 ========================

/** 查询：重置到第一页 */
const handleSearch = () => {
  tableRef.value?.refresh(true);
};

/** 重置搜索条件并重新查询 */
const handleReset = () => {
  const todayStr = dayjs().format("YYYY-MM-DD");
  const defaults = {
    monitorStatus: "",
    beginTime: `${todayStr} 00:00:00`,
    endTime: `${todayStr} 23:59:59`,
  };
  searchForm.value = { ...defaults };
  jsonFormRef.value?.coverValue(defaults);
  tableRef.value?.refresh(true);
};
</script>

<style scoped lang="scss">
.delay-monitor-dialog {
  :deep(.el-dialog__body) {
    padding: 16px 20px 20px;
  }

  :deep(.el-dialog__footer) {
    padding: 12px 20px;
    border-top: 1px solid #f0f0f0;
  }
}

.search-bar {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 16px;
  margin-bottom: 16px;
  background: #fafafa;
  border-radius: 8px;
  border: 1px solid #f0f0f0;

  > :deep(.json-form) {
    flex: 1;
  }

  .search-actions {
    flex-shrink: 0;
    display: flex;
    gap: 8px;
    padding-top: 2px;
  }
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
}
</style>
