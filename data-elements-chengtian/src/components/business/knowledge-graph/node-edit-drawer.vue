<template>
  <el-drawer
    v-model="visible"
    title="实体信息"
    size="1200"
    :destroy-on-close="true"
    :close-on-click-modal="false"
    @opened="handleDrawerOpened"
  >
    <template #header>
      <span class="text-base font-bold">实体信息</span>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saveLoading" @click="handleSave">保存</el-button>
      </div>
    </template>

    <div class="flex gap-4 h-full overflow-hidden">
      <!-- 左侧：实体基础信息 -->
      <div class="flex flex-col gap-4 flex-shrink-0" style="width: 450px; overflow-y: auto">
        <JsonForm ref="formRef" :rules="formRules" :options="formOptions">
          <template #field-color>
            <div class="flex gap-2 flex-wrap">
              <div
                v-for="c in PRESET_COLORS"
                :key="c"
                class="cursor-pointer transition-all"
                :style="{
                  width: '28px',
                  height: '28px',
                  borderRadius: '50%',
                  background: c,
                  outline: selectedColor === c ? `3px solid ${c}` : 'none',
                  outlineOffset: '2px',
                }"
                @click="handleColorSelect(c)"
              />
            </div>
          </template>

          <template #field-icon>
            <div class="flex gap-2 flex-wrap">
              <div
                v-for="key in ICON_KEYS"
                :key="key"
                class="cursor-pointer border-2 rounded-lg flex items-center justify-center transition-all"
                :class="selectedIcon === key ? 'border-primary' : 'border-gray-200'"
                style="width: 48px; height: 48px"
                @click="handleIconSelect(key)"
              >
                <el-icon size="24"><component :is="ICON_EP_COMPONENTS[key]" /></el-icon>
              </div>
            </div>
          </template>

          <template #field-preview>
            <div class="flex items-center justify-center" style="width: 100px; height: 100px">
              <div
                class="flex items-center justify-center rounded-full"
                :style="{ width: '60px', height: '60px', background: selectedColor }"
              >
                <el-icon size="24" color="#ffffff">
                  <component :is="ICON_EP_COMPONENTS[selectedIcon]" />
                </el-icon>
              </div>
            </div>
          </template>
        </JsonForm>
      </div>

      <!-- 右侧：字段管理 -->
      <div class="flex flex-col flex-1 min-w-0 overflow-hidden">
        <div class="flex items-center justify-between mb-3">
          <UTitle name="字段管理" />
          <div class="flex gap-2">
            <el-input
              v-model="fieldSearch"
              placeholder="请输入字段关键字"
              clearable
              style="width: 200px"
            />
            <el-button type="primary" plain @click="addField">+ 添加字段</el-button>
          </div>
        </div>

        <VTable
          ref="tableRef"
          :options="tableOptions"
          :editable="true"
          :show-toolbar="false"
          :show-operation="true"
        >
          <template #col_isPrimaryKey="{ row }">
            <el-radio v-model="primaryKeyId" :value="row.id" @change="onPrimaryKeyChange" />
          </template>
          <template #col_isRelationField="{ row }">
            <el-checkbox v-model="row.isRelationField" />
          </template>
          <template #operation="{ row }">
            <el-button type="danger" link @click="removeField(row)">删除</el-button>
          </template>
        </VTable>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from "vue";

import type { GraphNode, GraphField } from "./types";
import { PRESET_COLORS, ICON_KEYS, ICON_EP_COMPONENTS, type IconKey } from "./icons";
import { FormUtils } from "@/utils/form";

const FIELD_TYPES = ["VARCHAR", "INT", "DATETIME", "DECIMAL"] as const;

const props = defineProps<{
  modelValue: boolean;
  node?: GraphNode;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", val: boolean): void;
  (e: "confirm", node: Omit<GraphNode, "id"> & { id?: string }): void;
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit("update:modelValue", val),
});

const formRef = ref<any>(null);
const tableRef = ref<any>(null);
const saveLoading = ref(false);
const fieldSearch = ref("");
const primaryKeyId = ref<string>("");
const selectedColor = ref<string>(PRESET_COLORS[0]);
const selectedIcon = ref<IconKey>(ICON_KEYS[0]);

const formRules = FormUtils.fixJson([
  { type: "UTitle", props: { name: "实体信息" } },
  {
    field: "nameEn",
    title: "实体英文名",
    type: "input",
    $required: true,
    props: { placeholder: "如 CITIZEN" },
  },
  {
    field: "nameCn",
    title: "实体中文名",
    type: "input",
    $required: true,
    props: { placeholder: "如 公民" },
  },
  {
    field: "dataSource",
    title: "选择数据资源",
    type: "select",
    options: [
      { label: "数据资源A", value: "ds_a" },
      { label: "数据资源B", value: "ds_b" },
    ],
  },
  {
    field: "remark",
    title: "备注",
    type: "input",
    props: { type: "textarea", rows: 4, maxlength: 200, showWordLimit: true },
  },
  { type: "fieldComponent", field: "color", title: "节点颜色", value: PRESET_COLORS[0] },
  { type: "fieldComponent", field: "icon", title: "节点图标", value: ICON_KEYS[0] },
  { type: "fieldComponent", field: "preview", title: "展示预览" },
]);

const formOptions = {
  form: {
    inline: false,
    labelWrap: true,
    colon: false,
    labelWidth: "130px",
    labelPosition: "right" as const,
    showMessage: false,
    labelSuffix: "：",
  },
};

const FIELD_TYPE_OPTIONS = FIELD_TYPES.map((t) => ({ label: t, value: t }));

const tableOptions = {
  border: true,
  columns: [
    { title: "字段英文名", field: "fieldNameEn", minWidth: 130, editRender: { name: "VxeInput" } },
    { title: "字段中文名", field: "fieldNameCn", minWidth: 130, editRender: { name: "VxeInput" } },
    {
      title: "字段类型",
      field: "fieldType",
      width: 130,
      editRender: { name: "VxeSelect", options: FIELD_TYPE_OPTIONS },
    },
    {
      title: "字段长度",
      field: "fieldLength",
      width: 90,
      editRender: { name: "VxeInput", props: { type: "integer", min: 1, max: 65535 } },
    },
    {
      title: "实体主标识",
      field: "isPrimaryKey",
      width: 90,
      align: "center" as const,
      slots: { default: "col_isPrimaryKey" },
    },
    {
      title: "关系字段",
      field: "isRelationField",
      width: 80,
      align: "center" as const,
      slots: { default: "col_isRelationField" },
    },
  ],
  editRules: {
    fieldNameEn: [{ required: true, message: "字段英文名不能为空", trigger: "blur" }],
  },
  toolbarConfig: {
    buttons: [],
    tools: [],
  },
};

const fields = ref<GraphField[]>([]);

const filteredFields = computed(() =>
  fieldSearch.value
    ? fields.value.filter(
        (f) =>
          f.fieldNameEn.includes(fieldSearch.value) || f.fieldNameCn.includes(fieldSearch.value)
      )
    : fields.value
);

// 搜索变化时同步表格数据
watch(fieldSearch, () => {
  nextTick(() => tableRef.value?.setData(filteredFields.value));
});

const handleColorSelect = (c: string): void => {
  selectedColor.value = c;
  formRef.value?.setValue({ color: c });
};

const handleIconSelect = (key: IconKey): void => {
  selectedIcon.value = key;
  formRef.value?.setValue({ icon: key });
};

/** 抽屉完全打开后回填数据 */
const handleDrawerOpened = (): void => {
  if (props.node) {
    selectedColor.value = props.node.color;
    selectedIcon.value = props.node.icon as IconKey;
    formRef.value?.setValue({
      nameEn: props.node.nameEn,
      nameCn: props.node.nameCn,
      dataSource: props.node.dataSource ?? "",
      remark: props.node.remark ?? "",
      color: props.node.color,
      icon: props.node.icon,
    });
    fields.value = props.node.fields.map((f) => ({ ...f }));
    primaryKeyId.value = fields.value.find((f) => f.isPrimaryKey)?.id ?? "";
  } else {
    selectedColor.value = PRESET_COLORS[0];
    selectedIcon.value = ICON_KEYS[0];
    formRef.value?.clearValue?.();
    formRef.value?.setValue({ color: PRESET_COLORS[0], icon: ICON_KEYS[0] });
    fields.value = [];
    primaryKeyId.value = "";
  }
  fieldSearch.value = "";
  nextTick(() => tableRef.value?.setData(filteredFields.value));
};

const onPrimaryKeyChange = (val: any): void => {
  fields.value.forEach((f) => {
    f.isPrimaryKey = f.id === val;
  });
};

const addField = (): void => {
  const id = Date.now().toString();
  fields.value.push({
    id,
    fieldNameEn: "",
    fieldNameCn: "",
    fieldType: "VARCHAR",
    fieldLength: 64,
    isPrimaryKey: false,
    isRelationField: false,
  });
  nextTick(() => tableRef.value?.setData(filteredFields.value));
};

const removeField = (row: any): void => {
  if (primaryKeyId.value === row.id) primaryKeyId.value = "";
  fields.value = fields.value.filter((f) => f.id !== row.id);
  nextTick(() => tableRef.value?.setData(filteredFields.value));
};

const handleSave = async (): Promise<void> => {
  try {
    const formData = await formRef.value?.getFormData(true);
    if (!formData) return;
    await tableRef.value?.validate();
    saveLoading.value = true;
    const tableData = tableRef.value?.getData() ?? fields.value;
    emit("confirm", {
      ...(props.node?.id ? { id: props.node.id } : {}),
      ...formData,
      fields: tableData.map((f: any) => ({ ...f, isPrimaryKey: f.id === primaryKeyId.value })),
    });
    visible.value = false;
  } catch (error: unknown) {
    console.error("验证失败:", error);
  } finally {
    saveLoading.value = false;
  }
};
</script>
