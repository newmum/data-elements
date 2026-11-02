<template>
  <div v-loading="importing" class="form-table">
    <VTable
      ref="tableRef"
      :options="tableOptions"
      :height="tableHeight"
      :insert-data="insertData"
      :editable="!locked"
      :show-operation="showOperation && !locked"
      :show-toolbar="!locked"
      @btn-click="onToolbarClick"
    />
    <input
      v-if="preserveCsvText"
      ref="importInput"
      type="file"
      hidden
      accept=".csv,.txt,.html,.xml,.xlsx"
      @change="importFile"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import { cloneDeep } from "lodash-es";
import VTable from "../data/v-table.vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { csvToTableHtml } from "../_utils/csv-import";

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
    normalizeSelectValues?: boolean;
    valueAliases?: Record<string, Record<string, string>>;
    preserveCsvText?: boolean;
    importColumnMigrations?: { from: string; to: string; values: string[]; fallback: unknown }[];
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
const importInput = ref<HTMLInputElement>();
const importing = ref(false);
let modelVersion = 0;
const tableOptions = computed(() => {
  if (!props.preserveCsvText) return props.options;
  const options = cloneDeep(props.options);
  const toolbar = options.toolbarConfig;
  if (isRow(toolbar) && Array.isArray(toolbar.buttons)) {
    toolbar.buttons = toolbar.buttons.map((button: unknown) =>
      isRow(button) && button.code === "import" ? { ...button, code: "import_form_table" } : button
    );
  }
  return options;
});
const onToolbarClick = ({ code }: { code: string }) => {
  if (code === "import_form_table" && !locked.value && !importing.value) importInput.value?.click();
};
const importFile = async (event: Event) => {
  const input = event.target;
  if (!(input instanceof HTMLInputElement)) return;
  const file = input.files?.[0];
  if (!file || importing.value || locked.value) return;
  const version = modelVersion;
  importing.value = true;
  try {
    if (rows().length) {
      try {
        await ElMessageBox.confirm(
          `将替换当前 ${rows().length} 个参数，未保存的参数修改也会被替换。`,
          "导入参数",
          {
            confirmButtonText: "导入并替换",
            cancelButtonText: "保留当前参数",
            type: "warning",
          }
        );
      } catch {
        return;
      }
    }
    // 只绕开第三方 CSV 引号占位符解析，文件校验、载入和反馈继续使用公开导入 API。
    const imported = /\.csv$/i.test(file.name)
      ? new File([csvToTableHtml(await file.text())], file.name.replace(/\.csv$/i, ".html"), {
          type: "text/html",
        })
      : file;
    if (version !== modelVersion || locked.value) {
      ElMessage.warning("参数内容已变化，请重新选择文件导入");
      return;
    }
    const grid = tableRef.value?.gridRef;
    if (!grid) throw new Error("参数表尚未就绪，请稍后重试");
    const config = isRow(props.options.importConfig) ? props.options.importConfig : {};
    const mode =
      config.mode === "insert" || config.mode === "insertTop" || config.mode === "insertBottom"
        ? config.mode
        : "covering";
    await grid.importByFile(imported, { ...config, mode });
  } catch (error) {
    ElMessage.error(
      error instanceof Error ? error.message : "参数未导入，请使用导出的参数模板检查列名与格式"
    );
  } finally {
    importing.value = false;
    input.value = "";
  }
};
const locked = computed(() => props.disabled || props.readonly);
// VXE 的 auto 表示填满父容器；表单中的 auto 应按全部行自然撑高。
const tableHeight = computed(() => (props.height === "auto" ? undefined : props.height));
const rows = () => (tableRef.value?.getData() ?? []) as Row[];
// 可配置表格导入的下拉标签与内部值保持一致，旧模板缺列时沿用新增行默认值。
const isRow = (value: unknown): value is Row =>
  value !== null && typeof value === "object" && !Array.isArray(value);
const normalizeRows = (value: Row[]): Row[] => {
  if (!props.normalizeSelectValues) return cloneDeep(value);
  const columns: unknown[] = Array.isArray(props.options.columns) ? props.options.columns : [];
  return value.map((row) => {
    const copy = cloneDeep(row);
    for (const [field, aliases] of Object.entries(props.valueAliases || {})) {
      const key = String(copy[field]);
      const value = aliases[key] ?? aliases[key.trim().toLowerCase()];
      if (value !== undefined) copy[field] = value;
    }
    for (const migration of props.importColumnMigrations || []) {
      if (
        (copy[migration.to] == null || copy[migration.to] === "") &&
        migration.values.includes(String(copy[migration.from]))
      ) {
        copy[migration.to] = copy[migration.from];
        copy[migration.from] = migration.fallback;
      }
    }
    for (const [field, fallback] of Object.entries(props.insertData)) {
      if (copy[field] == null || copy[field] === "") copy[field] = fallback;
    }
    for (const column of columns) {
      if (!isRow(column)) continue;
      const field = column.field;
      const renderer = column.editRender;
      const options: unknown = isRow(renderer) ? renderer.options : undefined;
      if (typeof field !== "string" || !Array.isArray(options)) continue;
      const option: unknown = options.find(
        (option: unknown) =>
          isRow(option) &&
          (option.label === copy[field] ||
            (option.value != null && String(option.value) === String(copy[field])))
      );
      if (isRow(option)) copy[field] = option.value;
    }
    return copy;
  });
};
// VXE edits, imports and row operations all update the same reactive collection.
// Compare values before echoing them back, so model updates do not reset cell focus.
watch(
  () => rows(),
  (value) => {
    if (!tableRef.value || JSON.stringify(value) === JSON.stringify(props.modelValue)) return;
    const copy = normalizeRows(value);
    emit("update:modelValue", copy);
    emit("change", copy);
  },
  { deep: true, flush: "post" }
);
watch(
  () => props.modelValue,
  async (value) => {
    modelVersion++;
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
