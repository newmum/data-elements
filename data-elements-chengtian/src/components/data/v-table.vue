<!--
@description Vxe Table Grid配置式表格
@description 使用文档 https://vxetable.cn/#/grid/api?apiKey=grid
-->
<template>
  <vxe-grid
    ref="gridRef"
    :class="{ 'hidden-toolbar': !showToolbar }"
    v-bind="gridOptions"
    v-on="gridEvents"
  >
    <!--  操作列  -->
    <template #operation="scoped">
      <slot name="operation" v-bind="scoped">
        <el-button type="danger" link @click="removeRow(scoped.row)">删除</el-button>
      </slot>
    </template>
    <template v-if="showPage" #pager>
      <pagination
        v-model:page="pageVO.pageNo"
        v-model:limit="pageVO.pageSize"
        :total="page?.total"
        @pagination="pageChange"
      ></pagination>
    </template>
    <template #empty>
      <empty type="table" compact />
    </template>
    <!-- 插槽透传 -->
    <template v-for="(value, name) in $slots" #[name]="slotProps">
      <slot :name="name" v-bind="slotProps"></slot>
    </template>
  </vxe-grid>
</template>

<script lang="ts" setup>
import { useVxeGrid, UseVxeGridOptions } from "../_utils/useVxeGrid";
import { reactive } from "vue";

const emit = defineEmits(["page-change", "btn-click", "edit-row"]);

const props = withDefaults(
  defineProps<
    UseVxeGridOptions & {
      showPage?: boolean;
      showToolbar?: boolean;
      page?: {
        pageNo: number;
        pageSize: number;
      };
      editable?: boolean;
    }
  >(),
  {
    options: () => ({}),
    page: () => ({ pageNo: 1, pageSize: 20, total: 0 }),
    showToolbar: true,
    editable: false,
  }
);
const pageVO = reactive({
  pageNo: props?.page.pageNo ?? 1,
  pageSize: props?.page.pageSize ?? 20,
});
const { gridRef, gridOptions, gridEvents, setData, getData, removeRow, validate } = useVxeGrid(
  props,
  emit
);

const pageChange = (data: { page: number; limit: number }) => {
  emit("page-change", { pageNo: data.page, pageSize: data.limit });
};

// 暴露组件方法
defineExpose({
  getData,
  validate,
  setData,
  removeRow,
  gridOptions,
  gridEvents,
  gridRef,
  $grid: gridRef,
  getCheckData: () => gridRef.value?.getCheckboxRecords(),
  loadData: (data: []) => gridRef.value?.loadData(data),
  reloadData: (data: []) => gridRef.value?.reloadData(data),
  setLoading: (val) => (gridOptions.loading = val),
});
</script>

<style scoped lang="scss">
.hidden-toolbar :deep(.vxe-grid--toolbar-wrapper) {
  display: none;
}
</style>
