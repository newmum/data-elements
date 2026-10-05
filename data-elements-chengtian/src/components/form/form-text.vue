<!--
动态表单专用文本组件: 普通文本、字典文本、
-->
<template>
  <div class="form-text flex w-full preview">
    <!-- 为空时 -->
    <el-text
      v-if="(isUndefined(formValue) || isNull(formValue)) && formProps.type !== 'textarea'"
      type="info"
    >
      {{ emptyText }}
    </el-text>

    <!-- 数据源类型控件在只读态仍需执行自身的标签插槽，以渲染配置项的图标。 -->
    <template v-else-if="originType?.toLowerCase() === 'db-type-select' && isGlobalCom(originType)">
      <component
        :is="originType"
        v-bind="{
          modelValue: formValue,
          options,
          suffix,
          ...$attrs,
          ...formProps,
        }"
        preview
        disabled
        readonly
      />
    </template>

    <!-- 字典文本 -->
    <DictLabel
      v-else-if="options"
      v-bind="{
        modelValue: formValue,
        options,
        suffix,
        ...$attrs,
      }"
    />

    <!--  树选择器  -->
    <DictLabel
      v-else-if="originType?.toLowerCase().includes('treeselect')"
      v-bind="{
        modelValue: formValue,
        options: formProps.data,
        suffix,
      }"
    />

    <template v-else-if="formProps.type === 'password'">
      <el-input v-model="formValue" type="password" readonly show-password></el-input>
    </template>

    <template v-else-if="originType === 'date-picker'">
      <el-date-picker v-model="formValue" v-bind="formProps" disabled />
    </template>

    <!-- 文本域 -->
    <el-input
      v-else-if="formProps.type === 'textarea'"
      :model-value="formValue || emptyText"
      v-bind="formProps"
      disabled
      readonly
    ></el-input>

    <template v-else-if="originType === 'ElDatePicker'">
      {{ formValue?.join(" ~ ") }}
    </template>

    <!--  其他全局注册组件  -->
    <template v-else-if="isGlobalCom(originType)">
      <component
        :is="originType"
        v-bind="{
          modelValue: formValue,
          options,
          suffix,
          ...$attrs,
          ...formProps,
        }"
        preview
        disabled
        readonly
      />
    </template>

    <!-- 普通文本 -->
    <template v-else>
      <el-text truncated>{{ formValue }}{{ suffix }}</el-text>
    </template>
  </div>
</template>

<script setup lang="ts">
import { get, pick, isUndefined, isNull } from "lodash-es";
import { computed } from "vue";
import { useVModel } from "@vueuse/core";

const emit = defineEmits<{
  (e: "update:modelValue", value: string): void;
}>();

const props = withDefaults(
  defineProps<{
    originType: string; // 原始组件类型：text、input、select、radio、tree、date等
    modelValue: string | number | []; // 传入值
    formCreateInject: object;
    options: string | OptionType[];
    suffix?: string;
    emptyText?: string;
  }>(),
  {
    emptyText: "-",
  }
);

const formValue = useVModel(props, "modelValue", emit);
const formProps = computed(() => ({
  ...pick(props, ["options", "suffix", "emptyText"]),
  ...get(props.formCreateInject, "rule.props", {} as any),
}));

const instance = getCurrentInstance();
const kebabToPascal = (str: string): string => {
  // 空值处理
  if (!str) return "";

  return str
    .split("-") // 按 - 切割成数组
    .filter(Boolean) // 过滤空字符串（防止连续 --）
    .map((item) => {
      // 每个单词首字母大写，其余不变
      return item.charAt(0).toUpperCase() + item.slice(1);
    })
    .join(""); // 拼接成最终大驼峰
};
const isGlobalCom = (a) => {
  if (!a) return false;
  const name = kebabToPascal(a);
  if (a === "form-text" || a === "FormText") {
    return false;
  }
  return instance.appContext.components[name];
};
</script>

<style scoped lang="scss">
.preview {
  :deep(.el-select__wrapper) {
    padding-right: 0;
    padding-left: 0;
    .el-select__selected-item.el-select__placeholder.is-transparent {
      display: none;
    }
    cursor: default;
    border: none;
    box-shadow: none;

    * {
      cursor: default;
    }
    &.is-disabled {
      --el-select-disabled-color: var(--el-text-color-regular);
      color: var(--el-text-color-regular);
      background-color: var(--el-fill-color-blank);
      border: none !important;
    }
    &:hover {
      border: none !important;
      box-shadow: none !important;
    }
  }

  :deep(.el-select__icon) {
    display: none;
  }

  :deep(.el-input) {
    --el-disabled-bg-color: var(--el-input-bg-color, var(--el-fill-color-blank));
    --el-disabled-text-color: var(--el-text-color-regular);
    .el-input__inner {
      cursor: default;
    }
    .el-input__wrapper {
      cursor: default;
      border: none;
      box-shadow: none;
    }
  }

  :deep(.el-textarea) {
    --el-disabled-bg-color: var(--el-textarea-bg-color, var(--el-fill-color-blank));
    --el-disabled-text-color: var(--el-text-color-regular);
    .el-textarea__inner {
      padding-right: 0;
      padding-left: 0;
      cursor: default;
      border: none;
      box-shadow: none;
    }
  }

  :deep(.el-date-editor) {
    cursor: default;
    border: none;
    box-shadow: none;
    --el-disabled-bg-color: var(--el-input-bg-color, var(--el-fill-color-blank));
    --el-disabled-text-color: var(--el-text-color-regular);
    --el-text-color-placeholder: var(--el-text-color-regular);
    &.is-disabled {
      cursor: default;
      border: none;
      box-shadow: none;
    }
    &.is-active {
      border: none;
      box-shadow: none;
    }
    input {
      cursor: default;
    }
  }
  :deep(.el-cascader) {
    * {
      cursor: default !important;
    }
    .el-input__wrapper {
      padding-right: 0;
      padding-left: 0;
    }
    .el-input__suffix {
      display: none;
    }
  }
}
</style>
