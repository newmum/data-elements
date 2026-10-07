<template>
  <data-table
    ref="tableRef"
    :columns="props.cols"
    :data="originList"
    flex-type="flex-[0_1_200px]"
    border
    row-key="tid"
    class="rounded-lg w-full"
    :show-page="false"
    :show-selection="!props.preview"
    @selection-change="change"
  >
    <template #column-catalogName="{ row }">
      <el-text>
        <Icon icon="catalog" />
        {{ row.catalogName }}
      </el-text>
    </template>
  </data-table>
</template>

<script setup lang="ts">
import { watch, ref, onMounted, nextTick } from "vue";

const emit = defineEmits(["change"]);
const props = withDefaults(
  defineProps<{
    cols?: any[];
    editable?: boolean;
    preview?: boolean;
  }>(),
  {
    editable: true,
    cols: () => [
      {
        label: "数据目录",
        prop: "catalogName",
        ellipsis: true,
        minWidth: 120,
      },
      {
        label: "资源类型",
        prop: "resType",
        options: "assetType",
      },
      {
        label: "共享属性",
        prop: "shareType",
        width: 120,
        options: "shareType",
      },
      {
        label: "来源部门",
        prop: "orgId",
        width: 200,
        options: "org",
      },
    ],
    data: () => [],
  }
);
const modelValue = defineModel("modelValue", {
  type: Array,
  required: true,
});

const originList = ref<any[]>([]);
const tableRef = ref();

watch(
  modelValue,
  (newValue) => {
    if (newValue && !originList.value.length) {
      originList.value = modelValue.value;
      nextTick(() => {
        tableRef.value.$table?.toggleAllSelection();
      });
    }
  },
  { immediate: true }
);

const change = (selectRows: any[]) => {
  modelValue.value = selectRows;
  emit("change", selectRows);
};

const getSelectRows = () => {
  return tableRef.value?.$table?.getSelectionRows() || [];
};

defineExpose({
  getSelectRows,
});
</script>

<style scoped lang="scss"></style>
