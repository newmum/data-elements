<template>
  <div class="card-container h-auto px-5 pt-5 pb-2 flex-1 flex flex-col">
    <data-table
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
import { reactive, onMounted, ref } from "vue";
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
  previewData: [],
});

onMounted(() => {
  init();
});

const init = async () => {
  const tableId = props.tableId;
  if (!tableId) return;

  state.loading = true;
  try {
    await $common.get("/dst/database/metadata/columns", { tid: tableId }).then((cols) => {
      // 设置预览表格列配置
      state.previewCols = cols?.map((el) => ({ label: el.columnName, prop: el.columnName }));
      if (!state.previewCols.length) {
        state.errMsg = "无表字段数据";
      }
    });
  } finally {
    state.loading = false;
  }
};

const fetchData = async ({ pageNo, pageSize }) => {
  try {
    const tableId = props.tableId;
    if (!tableId) {
      state.errMsg = "表id不能为空";
      return;
    }

    const res = await $common.post(
      "/dst/database/metadata/table/preview-data",
      {
        tableId,
        pageNo,
        pageSize,
      },
      { _hiddenErrorMsg: true }
    );
    if (isString(res)) {
      state.errMsg = res;
      return;
    }
    state.errMsg = "";
    const { rows: list, columns, total } = res as any;
    if (!state.previewCols.length) {
      state.previewCols = columns?.map((el) => ({
        label: el,
        prop: el,
      }));
    }
    return {
      list,
      total,
    };
  } catch (error) {
    state.errMsg = error?.message || error;
    return {
      list: [],
      total: 0,
    };
  }
};
</script>

<style scoped lang="scss"></style>
