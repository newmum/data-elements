<template>
  <div v-loading="state.loading" class="card-container h-auto flex-1 flex flex-col pt-5 px-5 pb-2">
    <data-table
      ref="tableRef"
      :key="state.cols.length"
      :show-page="false"
      :columns="state.cols"
      :data="state.data"
      :size="route.path.includes('/market') ? 'large' : 'default'"
      @filter-change="handleClick"
    >
      <template #empty>
        <empty>
          <template #description>{{ state.error || "暂无数据" }}</template>
        </empty>
      </template>
    </data-table>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, reactive, computed } from "vue";
import { useRoute } from "vue-router";
import { useDebounceFn } from "@vueuse/core";

const route = useRoute();
const props = defineProps({
  catalogId: { type: String, default: "0d50a16cf9b5458398a22f6fa158ea57" },
});

// refs
const tableRef = ref();
const state = reactive<any>({
  catalogInfo: {},
  cols: [],
  data: [],
  loading: false,
  editing: false,
  filters: {},
  error: "",
  targetTableId: "",
});
const filterChange = (item?: any) => {
  state.filters = item;
  getData();
};
// 过滤触发防抖函数
const handleClick = useDebounceFn(filterChange, 500, {
  // maxWait: 2000, // 最大等待时间（即使没到延迟，超过maxWait也会触发）
});
const init = async () => {
  if (!props.catalogId) {
    return;
  }
  state.loading = true;
  try {
    if (state.catalogInfo.tid === props.catalogId) {
      return;
    }
    const [catalog, cols = []] = await Promise.all([
      $common.post("/dst/catalog/detail", {
        tid: props.catalogId,
      }),
      $common.post("/dst/catalog/catalog-items/list", {
        tid: props.catalogId,
      }),
    ]);
    state.catalogInfo = catalog;

    // 获取目录数据项 .filter((el) => el.targetTableColumnId)
    // state.cols = cols.map((el) => ({
    //   label: el.colName,
    //   prop: "",
    //   targetTableColumnId: el.targetTableColumnId,
    //   filterable: true,
    // }));

    // 获取挂接表字段及数据
    const targetTableId = catalog.targetTableId;
    state.targetTableId = catalog.targetTableId;
    if (!targetTableId) {
      return;
    }
    await $common.get("/dst/database/metadata/columns", { tid: targetTableId }).then((data) => {
      state.cols = data.map((el) => ({
        label: el.columnName,
        prop: el.columnName,
        filterable: true,
      }));
      // // 根据挂接映射字段关系，修改cols中的 prop
      // data.forEach((item) => {
      //   const { columnName, tid } = item;
      //   const col = state.cols.find((el) => el.targetTableColumnId === tid);
      //   if (col) {
      //     col.prop = columnName;
      //   }
      // });
    });
    getData();
  } finally {
    state.loading = false;
  }
};
const getData = (tableId = state.targetTableId) => {
  if (!state.targetTableId) return;
  state.loading = true;
  $common
    .post("/dst/database/metadata/table/preview-data", {
      tableId,
      pageNo: 1,
      pageSize: 20,
      conditions: state.filters,
    })
    .then((data: any) => {
      const { rows } = data;
      state.data = rows || [];
      state.error = "";
      state.loading = false;
    })
    .catch((err) => {
      state.error = err.message || "获取数据失败";
      state.loading = false;
    });
};

watch(
  () => props.catalogId,
  (newVal) => {
    if (newVal) {
      init();
    }
  },
  { immediate: true }
);
</script>

<style scoped lang="scss"></style>
