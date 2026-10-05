<!--
@description 通过一组json实现动态表单的渲染
@description 使用文档 https://www.form-create.com/v3/guide/
-->
<template>
  <div class="json-form jf-container" :class="{ 'jf-border': bordered }">
    <!--  第一个标题右侧操作区域  -->
    <div class="jf-container__toolbar">
      <slot name="toolbar"></slot>
    </div>
    <form-create
      v-model:api="fApi"
      :rule="formRules"
      :option="{ ...option, ...previewOption, ...options }"
      :preview="preview"
    >
      <template v-for="(value, name) in $slots" #[name]="slotProps">
        <slot :name="name" v-bind="slotProps"></slot>
      </template>
    </form-create>
  </div>
</template>

<script lang="ts" setup>
import { cloneDeep, pick } from "lodash-es";
import formCreate, { type Api, Options, FormRule } from "@form-create/element-ui";
const props = withDefaults(
  defineProps<{
    data?: Record<string, any>;
    rules: FormRule;
    preview?: boolean;
    options?: Options;
    bordered?: boolean;
    labelPosition?: "left" | "right" | "top";
    labelWidth?: string;
  }>(),
  {
    labelPosition: "right",
    labelWidth: "180px",
  }
);

const fApi = ref<Api>();
const formRules = ref();

watch(
  () => props.data,
  (val) => {
    if (val) {
      nextTick(() => {
        fApi.value?.setValue(val);
      });
    }
  },
  { immediate: true }
);

watch(
  () => props.rules,
  (val) => {
    formRules.value = cloneDeep(val);
  },
  { immediate: true }
);

const option = ref({
  submitBtn: false,
  resetBtn: false,
  row: { gutter: 0 },
  form: {
    inline: false,
    labelWrap: true,
    colon: false, // 不显示冒号
    labelWidth: props.labelWidth,
    labelPosition: props.labelPosition,
    showMessage: false,
  },
});

const previewOption = computed(() => {
  return props.preview
    ? {
        global: {
          "*": {
            props: {
              preview: true,
              readonly: true,
              // disabled: true,
            },
          },
        },
      }
    : {};
});

const validate = async () => {
  return new Promise((resolve, reject) => {
    fApi.value
      ?.validate((state: any) => {
        if (state === true) {
          resolve(true);
        } else {
          const keys = Object.keys(state);
          reject(state[keys[0]][0]["message"]);
        }
      })
      .catch((err) => {
        const keys = Object.keys(err);
        reject(err[keys[0]][0]["message"]);
      });
  });
};

/*
 * 获取表单数据, 带表单校验
 */
const getFormData = async (valid?: boolean) => {
  if (valid) {
    await validate().catch(() => {
      throw "请完善数据";
    });
    return fApi.value?.formData();
  } else {
    return fApi.value?.formData();
  }
};

/**
 * 获取动态字段 model === "0" || el.model === 0
 */
const getDynamicModel = () => {
  return formRules.value
    .filter((el: any) => el.model === "0" || el.model === 0)
    .map((el: any) => el.field);
};

/**
 * 获取所有可保存字段数据
 * 会过滤ignore段
 */
const getSaveData = (isUpdateBlank = true) => {
  const canSaveFields = formRules.value
    .filter((el: any) => !el.ignore && !!el.field)
    .map((el: any) => el.field);
  const formData = fApi.value?.formData() ?? {};

  if (isUpdateBlank) {
    // 为空的字段 赋值""，因为动态保存接口要使字段置空只能传空串
    const data: any = {};
    canSaveFields.forEach((field: string) => {
      data[field] = formData[field] || "";
    });
    return data;
  }

  return pick(formData, canSaveFields);
};

/**
 * 区分两种字段保存数据：基本字段数据、动态字段数据
 */
const get2SaveData = () => {
  const formData = getSaveData();
  const dynamicModel = getDynamicModel() ?? [];

  const result = Object.entries(formData).reduce(
    (acc, [key, value]) => {
      if (dynamicModel.includes(key)) {
        acc.dynamicData[key] = value ?? ""; // 确保空值转为空字符串
      } else {
        acc.baseData[key] = value; // 确保空值转为空字符串
      }
      return acc;
    },
    {
      baseData: {} as any,
      dynamicData: {} as any,
    }
  );
  return [result.baseData, result.dynamicData];
};

// 设置表单值
const setValue = (formData: Record<string, any>) => {
  const fields = fApi.value?.fields() as string[];
  // 过滤不存在的字段
  const filteredData = pick(formData, fields);
  fApi.value?.setValue(filteredData);
};

// 更新字段选项
const updateFieldOptions = (field: string, options: any[]) => {
  if (!fApi.value) return;

  // 获取字段
  const rule = fApi.value.getRule(field);
  if (rule) {
    // 更新选项
    rule.options = options;
    // 重新生成表单
    fApi.value.refresh();
  }
};

defineExpose({
  api: fApi,
  // 刷新表单，当表单中有引用的变量值发生变更则需要触发，尤其是字段插槽中更新dom常使用
  refresh: () => fApi.value?.refresh(),
  formData: () => fApi.value?.formData(),
  getFormData,
  validate,
  // 设值，只修改传入字段
  setValue,
  resetFields: () => fApi.value?.resetFields(),
  // 覆盖所有值，未传字段也会置空
  coverValue: (formData: Record<string, any>) => fApi.value?.coverValue(formData),
  clearValue: () => fApi.value?.coverValue({}),
  getDynamicModel,
  getSaveData,
  get2SaveData,
  updateFieldOptions,
  // 获取字段值
  getValue: (field: string) => fApi.value?.getValue(field),
  // 获取字段rule
  getRule: (field: string) => fApi.value?.getRule(field),
});
</script>

<style scoped lang="scss">
.jf-container {
  position: relative;
  &__toolbar {
    position: absolute;
    top: 8px;
    left: 80px;
    z-index: 1;
  }
  padding-left: 1px;
}
.jf-border {
  :deep(.fc-form-col) {
    box-sizing: border-box;
    margin: -1px 0 0 -1px;
    border: 1px solid #ebedf0;

    .el-form-item {
      height: 100%;
      margin-bottom: 0;
      .el-form-item__label,
      .el-form-item__content {
        height: auto;
        padding: 10px;
      }
      .el-form-item__label {
        background: #f4f7fc;
        & > .fc-form-title {
          overflow: hidden;
          text-overflow: ellipsis;
          font-weight: 500;
          color: #587aa8 !important;
          white-space: nowrap;
        }
      }
    }
    .twoRows .el-form-item__content {
      display: block;
      min-height: 115px;
    }
    .el-form-item-explain-error {
      display: none;
    }
    /*校验信息提示*/
    .el-form-item__error {
      padding-top: 0;
    }
  }
}
</style>
