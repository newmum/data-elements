<template>
  <div class="form-table">
    <VTable
      ref="tableRef"
      :options="options"
      :height="tableHeight"
      :insert-data="insertData"
      :editable="!locked"
      :show-operation="showOperation && !locked"
      :show-toolbar="!locked"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import { cloneDeep } from "lodash-es";
import VTable from "../data/v-table.vue";

type Row = Record<string, unknown>;
const props = withDefaults(
  defineProps<{
    modelValue?: Row[];
    options?: Record<string, unknown>;
    insertData?: Row;
    height?: string;
    showOperation?: boolean;
    disabled?: boolean;
    readonly?: boolean;
  }>(),
  {
    modelValue: () => [],
    options: () => ({}),
    insertData: () => ({}),
    height: "auto",
    showOperation: true,
  }
);
const emit = defineEmits<{
  (event: "update:modelValue", rows: Row[]): void;
  (event: "change", rows: Row[]): void;
}>();
const tableRef = ref<InstanceType<typeof VTable>>();
const locked = computed(() => props.disabled || props.readonly);
// VXE 的 auto 表示填满父容器；表单中的 auto 应按全部行自然撑高。
const tableHeight = computed(() => (props.height === "auto" ? undefined : props.height));
const rows = () => (tableRef.value?.getData() ?? []) as Row[];
// VXE edits, imports and row operations all update the same reactive collection.
// Compare values before echoing them back, so model updates do not reset cell focus.
watch(
  () => rows(),
  (value) => {
    if (!tableRef.value || JSON.stringify(value) === JSON.stringify(props.modelValue)) return;
    const copy = cloneDeep(value);
    emit("update:modelValue", copy);
    emit("change", copy);
  },
  { deep: true, flush: "post" }
);
watch(
  () => props.modelValue,
  async (value) => {
    await nextTick();
    if (JSON.stringify(value) !== JSON.stringify(rows())) tableRef.value?.setData(cloneDeep(value));
  },
  { immediate: true, deep: true }
);
defineExpose({
  getData: rows,
  validate: () => tableRef.value?.validate(),
});
</script>

<style scoped>
.form-table {
  width: 100%;
  min-width: 0;
  max-width: 100%;
}
</style>
