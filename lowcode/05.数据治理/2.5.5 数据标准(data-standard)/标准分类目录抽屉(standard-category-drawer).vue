<template>
  <el-drawer
    v-model="visible"
    :size="480"
    :with-header="true"
    :title="isEdit ? '编辑分类' : '新增分类'"
    @open="handleOpen"
    @closed="handleClosed"
  >
    <div class="px-4">
      <json-form ref="formRef" :rules="formRules" :options="{ form: { labelWidth: '90px' } }" />
    </div>
    <template #footer>
      <el-button ref="closeButtonRef" icon="close" @click="handleClose">关闭</el-button>
      <el-button type="primary" :loading="loading" @click="handleSave">
        <template #icon><Icon icon="save" /></template>
        保存
      </el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, computed, nextTick } from "vue";

// ==================== Props & Emits ====================
const props = defineProps<{
  /** 上级分类树数据，用于"上级分类"选择器 */
  treeData: any[];
}>();

const emit = defineEmits<{
  /** 保存成功后触发，通知父组件刷新树数据 */
  (e: "saved"): void;
}>();

// ==================== 状态 ====================
const visible = ref(false);
const loading = ref(false);
const formRef = ref<any>();
const closeButtonRef = ref<any>();

/** 当前编辑的节点数据（新增时为 null） */
const currentNode = ref<any>(null);

/** 新增时默认回填的父节点 tid */
const defaultParentId = ref<string>("");

const isEdit = computed(() => !!currentNode.value?.tid);

// The persisted root identifier is `root`; it is a technical value and must
// not leak into the category editor as its display text.
const parentTreeData = computed(() => [
  {
    tid: "root",
    label: "顶层",
    children: props.treeData || [],
  },
]);

// ==================== 表单规则（json-form 格式） ====================
const formRules = computed(() => [
  {
    field: "dictName",
    title: "分类名称",
    type: "input",
    $required: true,
    col: { span: 24 },
    props: { placeholder: "请输入分类名称", maxLength: 50, showWordLimit: true, clearable: true },
  },
  {
    field: "dictCode",
    title: "分类编码",
    type: "input",
    $required: true,
    col: { span: 24 },
    props: {
      placeholder: "请输入分类编码，如：CATEGORY_CODE",
      maxLength: 50,
      showWordLimit: true,
      clearable: true,
    },
  },
  {
    field: "parentId",
    title: "上级分类",
    type: "elTreeSelect",
    col: { span: 24 },
    props: {
      data: parentTreeData.value,
      valueKey: "tid",
      placeholder: "请选择上级分类（不选则为顶级）",
      clearable: true,
      filterable: true,
      "check-strictly": true,
      "default-expand-all": true,
      style: "width: 100%",
    },
  },
  {
    field: "sortNo",
    title: "排序号",
    type: "inputNumber",
    col: { span: 24 },
    value: 1,
    props: {
      min: 1,
      max: 9999,
      controlsPosition: "right",
      style: "width: 100%",
    },
  },
]);

// ==================== 对外暴露：打开抽屉 ====================

/**
 * 打开"新增"抽屉
 * @param parentId 默认选中的父节点 tid（可选）
 */
const openAdd = (parentId?: string) => {
  currentNode.value = null;
  defaultParentId.value = parentId || "";
  visible.value = true;
};

/**
 * 打开"编辑"抽屉
 * @param node 当前节点数据（含 tid、label/dictName、dictCode、parentId、sortNo）
 */
const openEdit = (node: any) => {
  currentNode.value = node;
  defaultParentId.value = "";
  visible.value = true;
};

const handleClose = () => {
  visible.value = false;
};

const close = () => {
  // Invoke the same footer-close action that users can trigger successfully.
  const button = closeButtonRef.value?.$el;
  if (button?.click) {
    button.click();
    return;
  }
  handleClose();
};

defineExpose({ openAdd, openEdit, close });

// ==================== 抽屉生命周期 ====================

const handleOpen = async () => {
  await nextTick();
  formRef.value?.resetFields();
  if (isEdit.value) {
    // 编辑：回填节点数据
    const data = currentNode.value;
    formRef.value?.setValue({
      dictName: data.label || data.dictName || "",
      dictCode: data.dictCode || data.value || "",
      parentId: data.parentId || data.pid || "",
      sortNo: data.sortNo ?? 1,
    });
  } else if (defaultParentId.value) {
    // 新增时回填默认父节点
    formRef.value?.setValue({ parentId: defaultParentId.value, sortNo: 1 });
  }
};

const handleClosed = () => {
  formRef.value?.resetFields();
  currentNode.value = null;
  defaultParentId.value = "";
};

// ==================== 保存 ====================
const handleSave = async () => {
  try {
    await formRef.value?.validate();
    loading.value = true;

    const formData = await formRef.value?.getFormData();

    const payload: Record<string, any> = {
      dictName: formData.dictName,
      dictCode: formData.dictCode,
      parentId: formData.parentId || "0",
      sortNo: formData.sortNo ?? 1,
      bizType: "standard_class",
    };

    if (isEdit.value) {
      payload.tid = currentNode.value.tid;
    }

    await $common.post("/sym/dictSaveOrUpdate", payload);

    ElMessage.success(isEdit.value ? "编辑成功" : "新增成功");
    // Close only after the request succeeds so the form remains available if
    // validation or persistence fails.  The technical value `root` is kept.
    close();
    await nextTick();
    emit("saved");
  } catch (e) {
    console.error("保存分类失败:", e);
  } finally {
    loading.value = false;
  }
};
</script>
