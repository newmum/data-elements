<template>
  <div class="table-preview card-container h-auto px-5 pt-5 pb-2 flex-1 flex flex-col">
    <data-table
      :key="props.tableId"
      ref="tableRef"
      :columns="state.previewCols"
      :loading="state.loading"
      :data="fetchData"
      :show-page="state.totalKnown"
    >
      <template v-if="state.errMsg" #empty>
        <div class="preview-error"><Empty :description="state.errMsg" /><el-button :loading="state.loading" @click="requestPreviewPage(state.pageNo, state.pageSize)">重试</el-button></div>
      </template>
    </data-table>
    <nav v-if="!state.totalKnown" class="preview-page-controls" aria-label="数据预览分页">
      <span>第 {{ state.pageNo }} 页 · 本页 {{ state.rowCount }} 条<span v-if="!state.hasMore && !state.loading && !state.errMsg"> · 已到末页</span></span>
      <div class="preview-page-actions">
        <el-select :model-value="state.pageSize" :disabled="state.loading" aria-label="每页条数" @change="size => requestPreviewPage(1, size)">
          <el-option v-for="size in [20, 50, 100]" :key="size" :label="`${size} 条/页`" :value="size" />
        </el-select>
        <el-button :disabled="state.loading || state.pageNo <= 1" @click="requestPreviewPage(state.pageNo - 1, state.pageSize)">上一页</el-button>
        <el-button :disabled="state.loading || !state.hasMore" @click="requestPreviewPage(state.pageNo + 1, state.pageSize)">下一页</el-button>
      </div>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { h, reactive, ref, resolveComponent, watch } from "vue";
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
  totalKnown: true,
  hasMore: false,
  pageNo: 1,
  pageSize: 20,
  rowCount: 0,
  pageOverride: null as null | {pageNo: number; pageSize: number},
});
let previewRequestId = 0;

watch(() => props.tableId, () => {
  state.previewCols = [];
  state.errMsg = "";
  previewRequestId++;
  state.totalKnown = true;
  state.hasMore = false;
  state.pageNo = 1;
  state.pageSize = 20;
  state.rowCount = 0;
  state.pageOverride = null;
});

// 使用结果集的原始字段作为 prop，中文展示名不参与排序或数据取值。
const previewColumns = (columns: string[] = [], labels: Record<string, string> = {}) => {
  const names = new Map(Object.entries(labels).map(([key, value]) => [key.toLowerCase(), value]));
  return columns.map((field) => {
    const english = field.split(".").pop() || field;
    const chinese = String(names.get(field.toLowerCase()) || names.get(english.toLowerCase()) || "").trim();
    const chineseLabel = chinese && chinese.toLowerCase() !== english.toLowerCase() ? chinese : "";
    const label = chineseLabel ? `${chineseLabel}\n${english}` : english;
    return {
      label,
      // 中英文各占一行，分别省略；完整名称由同一表头的悬停提示展示。
      renderHeader: () => h(resolveComponent("el-tooltip"), {
        content: label,
        placement: "top",
        showAfter: 200,
        popperStyle: { whiteSpace: "pre-line", maxWidth: "480px", overflowWrap: "anywhere" },
      }, {
        default: () => h("span", { class: "preview-column-title", "aria-label": label }, [
          h("span", { class: "preview-column-chinese" }, chineseLabel || "—"),
          h("span", { class: "preview-column-english" }, english),
        ]),
      }),
      prop: field,
      sortable: "custom",
      minWidth: 160,
      showOverflowTooltip: true,
    };
  });
};

const fetchData = async ({ pageNo, pageSize,sortField,sortDir }) => {
  const tableId = props.tableId;
  const requestId = ++previewRequestId;
  const page = state.pageOverride || {pageNo, pageSize: state.totalKnown ? pageSize : state.pageSize};
  state.pageOverride = null;
  state.loading = true;
  state.pageNo = page.pageNo;
  state.pageSize = page.pageSize;
  try {
    if (!tableId) {
      state.errMsg = "表id不能为空";
      return { list: [], total: 0 };
    }

    const res = await $common.post(
      "/dst/database/metadata/table/preview-data",
      {
        tableId,
        pageNo: page.pageNo,
        pageSize: page.pageSize,sortField,sortDir,
        countMode: "none",
      },
      { _hiddenErrorMsg: true }
    );
    if (tableId !== props.tableId || requestId !== previewRequestId) return { list: [], total: 0 };
    if (isString(res)) {
      throw new Error(res);
    }
    state.errMsg = "";
    const { rows: list, columns, total, columnLabels, totalKnown, hasMore } = res as any;
    state.previewCols = previewColumns(columns, columnLabels);
    state.totalKnown = totalKnown !== false;
    state.hasMore = hasMore === true;
    state.rowCount = Array.isArray(list) ? list.length : 0;
    return {
      list,
      // 无总数时隐藏 DataTable 的总数分页，使用下方明确的上一页/下一页导航。
      total: state.totalKnown ? total : 0,
    };
  } catch (error) {
    if (tableId === props.tableId && requestId === previewRequestId) {
      state.errMsg = error?.message || String(error);
      state.rowCount = 0;
      state.hasMore = false;
    }
    return {
      list: [],
      total: 0,
    };
  } finally {
    if (tableId === props.tableId && requestId === previewRequestId) state.loading = false;
  }
};
const requestPreviewPage = (pageNo: number, pageSize: number) => {
  if (state.loading || pageNo < 1 || pageNo > 100000 || ![20, 50, 100].includes(pageSize)) return;
  state.pageOverride = {pageNo, pageSize};
  tableRef.value?.refresh();
};
</script>

<style scoped lang="scss">
.preview-page-controls { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; padding: 12px 0 4px; color: var(--el-text-color-secondary); font-size: 13px; }
.preview-page-actions { display: flex; align-items: center; gap: 8px; }
.preview-page-actions .el-select { width: 110px; }
.preview-error { display: grid; justify-items: center; gap: 12px; padding-bottom: 16px; }
.table-preview :deep(.preview-column-title) {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  max-width: 100%;
  line-height: 20px;
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary);

  > span {
    display: block;
    height: 20px;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  .preview-column-chinese {
    font-size: 14px;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }
}
</style>
