<template>
  <div class="apply-json-form">
    <JsonForm ref="formRef" bordered :rules="formRules" label-width="250px"></JsonForm>

    <!--  打印  -->
    <apply-print-modal
      ref="printRef"
      v-model="printState.open"
      :rules="printState.rules"
      :data="{ ...formRef?.formData() }"
    />
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from "vue";
import { cloneDeep } from "lodash-es";
import { FormUtils } from "@/utils/form";
import { useRegisterStore } from "@/store";

const store = useRegisterStore();
const props = withDefaults(
  defineProps<{
    /** 初始数据（包含 applyResource 申请资源列表） */
    initData?: Record<string, any>;
    editable?: boolean;
    /** 是否复制申请单 */
    copy?: boolean;
  }>(),
  {
    initData: () => ({}),
    editable: true,
  }
);

// refs
const formRef = ref();
const printRef = ref<HTMLElement>();

const formRules = ref<any[]>([]);

// 打印相关
const printState = reactive({
  open: false,
  rules: [],
});

// 初始化
onMounted(async () => {
  await loadFormRules();
  await initFormData();
});

/** 加载表单规则 */
const loadFormRules = async () => {
  try {
    formRules.value = await ($form as any).get("登记申请单");
    if (!props.editable) {
      formRules.value = FormUtils.transFormText(formRules.value);
    }
  } catch (error) {
    console.error("加载表单规则失败:", error);
  }
};

/** 初始化表单数据 */
const initFormData = async () => {
  let initData: any = {
    applyOrgId: ($user as any).orgId,
    usci: ($user as any).usci,
  };
  let catalogs: any[] = [];

  if (props.initData.tid) {
    // 编辑已有申请单，查询详情
    const detail = await ($common as any).post("/dws/market/applyForm/queryById", {
      tid: props.initData.tid,
    });
    const { applyResource = [], ...rest } = detail || {};
    initData = rest;
    catalogs = applyResource;

    if (props.copy) {
      // 复制一份申请单
      initData = { ...initData, tid: undefined };
    }
  } else {
    // 非编辑模式：从 initData.applyResource 获取申请资源列表
    catalogs = props.initData.applyResource || [];
  }

  catalogs = catalogs.map((el: any) => ({ ...el }));

  // 同步设置表单中的申请资源字段
  nextTick(() => {
    formRef.value?.setValue({ ...initData, applyResource: catalogs });
    formRef.value.api.on("change", (field: string, value: any) => {
      if (field === "applyResource") {
        handleResChange(value);
      }
    });
  });
};

/** 资源选择变化 */
const handleResChange = (rows: string[]) => {
  formRef.value?.setValue({ applyResource: rows });

  // 资源类型中有api时需显示使用信息表单
  const hasApi = rows?.some((el) => el.resType?.includes("api"));
  // 提取使用信息范围的表单字段
  const fields: string[] = [];
  for (const el of formRules.value) {
    if (fields.length > 0 && el.type === "UTitle") {
      break;
    }
    if (el.field === "useTitle" || fields.length > 0) {
      fields.push(el.field);
    }
  }

  if (hasApi) {
    formRef.value?.api?.hidden(false, fields);
  } else {
    formRef.value?.api?.hidden(true, fields);
  }
  // formRef.value?.refresh();
};

/** 获取表单数据（带校验） */
const getFormData = async () => {
  await formRef.value?.validate();
  const formData = formRef.value?.getSaveData() || {};
  return { ...formData };
};

/** 获取表单数据（不带校验） */
const getSaveData = () => {
  return formRef.value?.getSaveData() || {};
};

/** 验证表单 */
const validate = async () => {
  const formData = await getFormData();
  // 校验是否选择了申请资源
  if (Array.isArray(formData.applyResource) && formData.applyResource.length > 0) {
    return true;
  }
  throw "请选择申请资源";
};

/** 设置表单值 */
const setValue = (data: Record<string, any>) => {
  formRef.value?.setValue(data);
};

/** 重置表单 */
const resetFields = () => {
  formRef.value?.resetFields();
};

/** 刷新表单 */
const refresh = () => {
  formRef.value?.refresh();
};

/** 打开打印弹框 */
const openPrint = () => {
  printState.open = true;
  printState.rules = FormUtils.fixJson(cloneDeep(formRef.value?.api.rule || []), "160px");
  if (props.editable) {
    printState.rules = FormUtils.transFormText(printState.rules);
  }
};

// 暴露方法
defineExpose({
  getFormData,
  getSaveData,
  validate,
  setValue,
  resetFields,
  refresh,
  openPrint,
  formRef,
});
</script>

<style scoped lang="scss">
.apply-json-form {
  width: 100%;
}

.apply-print-modal {
  .print-content {
    background: #fff;
    padding: 20px;
    max-height: 60vh;
    overflow-y: auto;
  }

  .form-preview {
    margin-bottom: 20px;
  }

  .resource-preview {
    .preview-title {
      margin: 0 0 12px 0;
      font-size: 14px;
      font-weight: 500;
      color: #303133;
    }
  }

  .dialog-footer {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
  }
}
</style>
