<template>
  <el-drawer v-model="visible" :size="700" :with-header="true">
    <template #header>
      <div class="detail-drawer__header">
        <strong>{{ drawerTitle }}</strong>
        <span v-if="isReadonly">已发布，需下线后才可修改</span>
      </div>
    </template>
    <div class="px-4" :class="{ 'readonly-form': isReadonly }">
      <json-form
        ref="detailFormRef"
        :rules="formRules"
        :options="{ form: { labelWidth: '110px' } }"
      />
    </div>
    <template #footer>
      <el-button icon="close" @click="visible = false">关闭</el-button>
      <el-button v-if="!isReadonly" type="primary" :loading="isLoading" @click="saveDetail">
        <template #icon><Icon icon="save" /></template>
        保存
      </el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, h } from "vue";
import { findTreeNodeByTid } from "@/utils";

// ==================== Props ====================
const props = defineProps<{
  treeData: any[];
}>();

// ==================== Emits ====================
const emit = defineEmits<{
  (e: "saved"): void;
}>();

// ==================== State ====================
const visible = ref(false);
const isLoading = ref(false);
const metaId = ref<string | undefined>(undefined);
const isReadonly = ref(false);
const detailFormRef = ref<any>();

// 抽屉标题
const drawerTitle = computed(() => {
  if (isReadonly.value) return "查看数据元";
  return metaId.value ? "编辑数据元" : "注册数据元";
});

// 质检规则选项
const qualityRules = ref<any[]>([]);
// 值域（标准代码）选项
const standardCodes = ref<any[]>([]);

// ==================== 计算属性：上级分类树选择数据 ====================
const treeSelectData = computed(() => {
  return props.treeData?.[0]?.children || [];
});

// ==================== 表单配置 ====================
const formRules = computed(() => {
  return [
    {
      type: "u-title",
      title: "业务属性",
    },
    {
      field: "metaName",
      title: "数据元名称",
      type: "input",
      required: true,
      col: { span: 24 },
      props: { placeholder: "如：行政区划代码", maxLength: 50, readonly: isReadonly.value },
      $required: true,
    },
    {
      field: "metaCode",
      title: "数据元英文名",
      type: "input",
      col: { span: 24 },
      props: { placeholder: "如：DATA_ELEMENT_CODE", maxLength: 50, readonly: isReadonly.value },
      $required: true,
    },
    {
      field: "standardEncode",
      title: "标准编码",
      type: "input",
      col: { span: 24 },
      props: { placeholder: "手动输入或点击生成", maxLength: 50, readonly: isReadonly.value },
      $required: true,
      renderSlots: {
        append() {
          return h("div", {
            class: "i-svg:idea cursor-pointer",
            style: "font-size: 18px;margin: 0 -8px",
            onClick: () => {
              detailFormRef.value?.setValue({ standardEncode: `ID_${new Date().getTime()}` });
            },
          });
        },
      },
    },
    {
      field: "bizDef",
      title: "业务定义",
      type: "textarea",
      col: { span: 24 },
      props: { placeholder: "描述该数据元的业务含义及应用场景", rows: 3, maxLength: 255, readonly: isReadonly.value },
    },
    {
      type: "u-title",
      title: "技术属性",
    },
    {
      field: "dataTypeId",
      title: "数据类型",
      type: "DictSelect",
      $required: true,
      col: { span: 24 },
      props: { placeholder: "请选择数据类型", options: "colType" },
    },
    {
      field: "fieldType",
      title: "规范字段类型",
      type: "select",
      col: { span: 12 },
      options: [
        { label: "VARCHAR", value: "varchar" },
        { label: "CHAR", value: "char" },
        { label: "INTEGER", value: "integer" },
        { label: "BIGINT", value: "bigint" },
        { label: "DECIMAL", value: "decimal" },
        { label: "DATE", value: "date" },
        { label: "DATETIME", value: "datetime" },
        { label: "BOOLEAN", value: "boolean" },
        { label: "TEXT", value: "text" },
      ],
      props: { placeholder: "请选择规范字段类型" },
      $required: true,
    },
    {
      field: "fieldLength",
      title: "规范长度",
      type: "input-number",
      col: { span: 12 },
      props: { min: 1, max: 65535, precision: 0, placeholder: "请输入最大长度", readonly: isReadonly.value },
      $required: true,
    },
    {
      field: "numericPrecision",
      title: "数值精度",
      type: "input-number",
      col: { span: 12 },
      props: { min: 1, max: 65, precision: 0, placeholder: "DECIMAL 可填写", readonly: isReadonly.value },
    },
    {
      field: "numericScale",
      title: "小数位",
      type: "input-number",
      col: { span: 12 },
      props: { min: 0, max: 30, precision: 0, placeholder: "DECIMAL 可填写", readonly: isReadonly.value },
    },
    {
      field: "formatPattern",
      title: "格式规则",
      type: "input",
      col: { span: 24 },
      props: { maxLength: 500, placeholder: "可填写正则表达式", readonly: isReadonly.value },
    },
    {
      field: "exampleValue",
      title: "示例值",
      type: "input",
      col: { span: 12 },
      props: { maxLength: 500, placeholder: "例如：350102", readonly: isReadonly.value },
    },
    {
      field: "standardCodeSet",
      title: "关联标准代码集",
      type: "input",
      col: { span: 12 },
      props: { maxLength: 100, placeholder: "例如：GB/T 2260", readonly: isReadonly.value },
    },
    {
      field: "valueDomainId",
      title: "值域",
      type: "select",
      col: { span: 24 },
      $required: true,
    },
    {
      field: "isNullable",
      title: "可为空值",
      type: "radio",
      col: { span: 12 },
      options: [
        { label: "是", value: 1 },
        { label: "否", value: 0 },
      ],
      value: 0,
      $required: true,
    },
    {
      type: "u-title",
      title: "安全属性",
    },
    {
      type: "select",
      title: "数据分级",
      field: "dataLevel",
      options: [
        { label: "完全公开（L1）", value: "1" },
        { label: "对内公开（L2）", value: "2" },
        { label: "私密（L3）", value: "3" },
        { label: "机密（L4）", value: "4" },
      ],
      props: { placeholder: "请选择数据分级" },
      $required: true,
    },
    {
      type: "elTreeSelect",
      title: "数据分类",
      field: "dataCategoryId",
      props: {
        data: treeSelectData,
        valueKey: "tid",
        placeholder: "请选择数据分类",
        clearable: true,
        filterable: true,
        "check-strictly": true,
        "default-expand-all": true,
      },
      $required: true,
    },
    {
      type: "u-title",
      title: "质检监控",
    },
    {
      type: "select",
      title: "质检规则",
      field: "qualityRule",
      props: {
        options: [],
        multiple: true,
        placeholder: "请选择质检规则",
      },
    },
    {
      type: "code-editor",
      title: "自定义规则",
      field: "customRule",
      props: {
        showOption: false,
        lang: "javascript",
        readOnly: isReadonly.value,
      },
    },
  ];
});

// ==================== 公开方法 ====================

/** 打开新增数据元抽屉 */
const openAdd = (defaultCategoryId?: string) => {
  metaId.value = undefined;
  isReadonly.value = false;
  visible.value = true;
  nextTick(() => {
    detailFormRef.value?.resetFields();
    if (defaultCategoryId) {
      detailFormRef.value?.setValue({ dataCategoryId: defaultCategoryId });
    }
  });
};

/** 打开编辑/详情数据元抽屉
 * @param row - 行数据
 * @param readonly - 是否只读（已发布状态传 true）
 */
const openDetail = (row: any, readonly = false) => {
  metaId.value = row.tid;
  isReadonly.value = readonly;
  visible.value = true;
  nextTick(() => {
    const formData = { ...row };
    formData.qualityRule = formData.qualityRule?.split(",") || [];
    detailFormRef.value?.setValue(formData);
  });
};

defineExpose({ openAdd, openDetail });

const fetchQualityRules = async () => {
  try {
    const response = await $common.post("/dwm/quality/list");
    if (response && Array.isArray(response)) {
      qualityRules.value = response.map((item: any) => ({
        ...item,
        label: item.ruleName,
        value: item.tid,
      }));
    }
  } catch (error) {
    console.error("Error fetching quality rules:", error);
    qualityRules.value = [];
  }
};

/** 获取值域（标准代码）列表 */
const fetchStandardCodes = async () => {
  try {
    const response = await $common.post("/dwm/standard/code/list");
    if (response && Array.isArray(response)) {
      standardCodes.value = response.map((item: any) => ({
        ...item,
        label: item.dictName,
        value: item.tid,
      }));
    }
  } catch (error) {
    console.error("Error fetching standard codes:", error);
    standardCodes.value = [];
  }
};

// 抽屉打开时更新动态选项
watch(
  () => visible.value,
  async (newVal) => {
    if (newVal) {
      // 初次打开时若尚未加载，则先加载
      if (!qualityRules.value.length) await fetchQualityRules();
      if (!standardCodes.value.length) await fetchStandardCodes();
      await nextTick();
      detailFormRef.value?.updateFieldOptions("qualityRule", qualityRules.value);
      detailFormRef.value?.updateFieldOptions("valueDomainId", standardCodes.value);
    }
  }
);

// ==================== 保存 ====================
const saveDetail = async () => {
  try {
    await detailFormRef.value.validate();
    isLoading.value = true;
    const saveData = await detailFormRef.value.getFormData();

    const categoryNode = findTreeNodeByTid(props.treeData, saveData.dataCategoryId);
    if (categoryNode) {
      saveData.dataCategoryName = categoryNode.label;
      saveData.dataCategoryPath = categoryNode.treePath;
    }
    if (saveData.qualityRule?.length) {
      saveData.qualityRule = saveData.qualityRule.join(",");
    } else {
      saveData.qualityRule = null;
    }

    await $common.post("/dwm/standard/element/metaSaveOrUpdate", {
      ...saveData,
      publishStatus: metaId.value ? null : 1, // 新增时默认发布状态
      tid: metaId.value,
    });

    ElMessage.success("保存成功");
    visible.value = false;
    emit("saved");
  } catch (e) {
    console.error("Error saving data:", e);
  } finally {
    isLoading.value = false;
  }
};
</script>

<style scoped>
.detail-drawer__header {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.detail-drawer__header span {
  color: #909399;
  font-size: 13px;
  font-weight: normal;
}

.readonly-form :deep(.el-select),
.readonly-form :deep(.el-input-number),
.readonly-form :deep(.el-radio-group),
.readonly-form :deep(.i-svg\:idea),
.readonly-form :deep(.code-editor) {
  pointer-events: none;
}
</style>
