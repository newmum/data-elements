<template>
  <div class="table-preview card-container h-auto px-5 pt-5 pb-2 flex-1 flex flex-col">
    <data-table
      :key="props.tableId"
      ref="tableRef"
      :columns="state.previewCols"
      :loading="state.loading"
      :data="fetchData"
    >
      <template v-if="state.errMsg" #empty>
        <Empty :description="state.errMsg" />
      </template>
    </data-table>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from "vue";
import { isString } from "lodash-es";

const props = defineProps<{
  tableId: string;
}>();

const tableRef = ref();
const state = reactive({
  // 数据预览
  loading: false,
  errMsg: "",
  previewCols: [] as any[],
});

watch(() => props.tableId, () => {
  state.previewCols = [];
  state.errMsg = "";
});

// 使用结果集的原始字段作为 prop，中文展示名不参与排序或数据取值。
const previewColumns = (columns: string[] = [], labels: Record<string, string> = {}) => {
  const names = new Map(Object.entries(labels).map(([key, value]) => [key.toLowerCase(), value]));
  return columns.map((field) => {
    const english = field.split(".").pop() || field;
    const chinese = String(names.get(field.toLowerCase()) || names.get(english.toLowerCase()) || "").trim();
    return {
      label: chinese && chinese.toLowerCase() !== english.toLowerCase() ? `${chinese}\n${english}` : english,
      prop: field,
      sortable: "custom",
      minWidth: 160,
      showOverflowTooltip: true,
    };
  });
};

const fetchData = async ({ pageNo, pageSize,sortField,sortDir }) => {
  const tableId = props.tableId;
  try {
    if (!tableId) {
      state.errMsg = "表id不能为空";
      return { list: [], total: 0 };
    }

    const res = await $common.post(
      "/dst/database/metadata/table/preview-data",
      {
        tableId,
        pageNo,
        pageSize,sortField,sortDir,
      },
      { _hiddenErrorMsg: true }
    );
    if (tableId !== props.tableId) return { list: [], total: 0 };
    if (isString(res)) {
      state.errMsg = res;
      return { list: [], total: 0 };
    }
    state.errMsg = "";
    const { rows: list, columns, total, columnLabels } = res as any;
    state.previewCols = previewColumns(columns, columnLabels);
    return {
      list,
      total,
    };
  } catch (error) {
    if (tableId === props.tableId) state.errMsg = error?.message || String(error);
    return {
      list: [],
      total: 0,
    };
  }
};
</script>

<style scoped lang="scss">
.table-preview :deep(.el-table__header-wrapper .el-table__header .el-table__cell .cell > span.title) {
  display: inline-block;
  white-space: pre-line;
  line-height: 1.5;
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary);

  &::first-line {
    font-size: 14px;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }
}
</style>
