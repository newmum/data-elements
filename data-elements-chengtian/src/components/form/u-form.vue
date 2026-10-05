<template>
  <el-form
    ref="formRef"
    :model="formData"
    v-bind="{
      rules: formRules,
      size,
      labelWidth,
      labelPosition,
      disabled,
      inline,
      validateOnRuleChange,
      hideRequiredAsterisk,
      scrollToError,
      ...$attrs,
    }"
  >
    <el-row :gutter="20">
      <template v-for="item in formItems" :key="item.prop">
        <el-col v-if="!item.hidden" :span="item.span || 24">
          <!-- 普通表单项 -->
          <el-form-item
            v-if="!item.slot"
            :prop="item.prop"
            :label="item.label"
            :label-width="item.labelWidth || itemLabelWidth"
            :required="item.required"
            :rules="item.rules"
          >
            <!-- 带提示信息的标签 -->
            <template v-if="item.info || item.logo" #label>
              <div style="display: flex; gap: 4px">
                <!-- logo 星号显示 -->
                <span v-if="item.logo" style="margin-left: 1px; color: #f56c6c">*</span>
                <span>{{ item.label }}</span>
                <!-- 提示信息图标 -->
                <el-tooltip v-if="item.info" :content="item.info" placement="right" effect="light">
                  <el-icon style="margin-top: 2px; color: #909399; cursor: pointer">
                    <QuestionFilled />
                  </el-icon>
                </el-tooltip>
              </div>
            </template>

            <!-- 文本组件 -->
            <template v-if="item.type === 'text'">
              <el-text v-bind="item.props">{{ formData[item.prop] }}</el-text>
            </template>

            <!-- TreeSelect 组件 -->
            <template v-else-if="item.type === 'tree-select'">
              <ElTreeSelect
                v-model="formData[item.prop]"
                :data="unref(item.options) || []"
                :placeholder="item.placeholder || `请选择${item.label}`"
                style="width: 100%"
                clearable
                filterable
                v-bind="item.props"
              />
            </template>

            <!-- 其他已定义组件 -->
            <template v-else-if="Object.keys(FormComponent).includes(item.type as string)">
              <component
                :is="FormComponent[item.type as keyof typeof FormComponent]"
                v-model="formData[item.prop]"
                :placeholder="
                  disabled && isEmpty(formData[item.prop])
                    ? '无'
                    : item.placeholder ||
                      `${item.type === 'select' ? '请选择' : '请输入'}${item.label}`
                "
                style="width: 100%"
                v-bind="{
                  clearable: true,
                  valueFormat: item.type === 'date' ? 'YYYY-MM-DD HH:mm:ss' : null,
                  ...item,
                  type: undefined,
                  ...item.props,
                  options: unref(item.options),
                  formData,
                  ...(item.type === 'number'
                    ? {
                        min: item.min ?? 0,
                        max: item.max ?? 999999,
                      }
                    : {}),
                }"
              />
            </template>

            <!-- 单选框 -->
            <el-radio-group
              v-else-if="item.type === 'radio'"
              v-model="formData[item.prop]"
              :disabled="item.disabled"
            >
              <el-radio
                v-for="option in unref(item.options)"
                :key="option.value"
                :label="option.value"
              >
                {{ option.label }}
              </el-radio>
            </el-radio-group>

            <!-- 复选框 -->
            <el-checkbox-group
              v-else-if="item.type === 'checkbox'"
              v-model="formData[item.prop]"
              :disabled="item.disabled"
            >
              <el-checkbox
                v-for="option in unref(item.options)"
                :key="option.value"
                :label="option.value"
              >
                {{ option.label }}
              </el-checkbox>
            </el-checkbox-group>
            <!-- 自定义插槽 -->
            <slot
              v-else-if="item.type === 'slot'"
              :name="item.slotName"
              :item="item"
              :form="formData"
            />
            <component :is="item.type" v-else v-bind="item.props" v-model="formData[item.prop]" />
          </el-form-item>

          <!-- 自定义插槽表单项 -->
          <slot
            v-else-if="item.slot"
            :span="item.span"
            :offset="item.offset"
            :name="item.slotName"
            :item="item"
            :form="formData"
          />
        </el-col>
      </template>
    </el-row>

    <!-- 表单操作按钮 -->
    <div v-if="showButtons" :class="buttonsClassName">
      <slot name="buttons">
        <el-button
          v-if="showSubmitButton"
          type="primary"
          :loading="submitLoading"
          @click="handleSubmit"
        >
          {{ submitButtonText }}
        </el-button>
        <el-button v-if="showResetButton" @click="handleReset">
          {{ resetButtonText }}
        </el-button>
        <el-button v-if="showCancelButton" @click="handleCancel">
          {{ cancelButtonText }}
        </el-button>
      </slot>
    </div>
  </el-form>
</template>

<script setup lang="ts">
import { ref, computed, unref, provide, watch, nextTick } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import { QuestionFilled } from "@element-plus/icons-vue";
import { FormComponent, type UFormEmit, type UFormProps } from "@/components/_utils/form.types";

const emit = defineEmits<UFormEmit>();
//控制查看状态--删除按钮操作
provide(
  "actionType",
  computed(() => (props.disabled ? "view" : "edit"))
);

const props = withDefaults(defineProps<UFormProps>(), {
  items: () => [],
  rules: () => ({}),
  labelWidth: "100px",
  itemLabelWidth: "",
  labelPosition: "right",
  size: "default",
  disabled: false,
  inline: false,
  validateOnRuleChange: false,
  hideRequiredAsterisk: false,
  scrollToError: false,
  showButtons: true,
  buttonsClassName: "",
  showSubmitButton: true,
  submitButtonText: "提交",
  submitLoading: false,
  showResetButton: true,
  resetButtonText: "重置",
  showCancelButton: false,
  cancelButtonText: "取消",
});

const formRef = ref<FormInstance>();
// const formData = ref({ ...props.modelValue })

const formData = computed({
  get() {
    return props.modelValue;
  },
  set(value) {
    emit("update:modelValue", value);
  },
});

// 初始化默认值
const initializeDefaultValues = () => {
  if (!props.modelValue || !props.items?.length) return;

  const currentData = { ...props.modelValue };
  let hasChanges = false;

  props.items.forEach((item) => {
    if (item.prop && item.defaultValue !== undefined) {
      // 只有当字段值为 undefined、null 或空字符串时才设置默认值
      const currentValue = currentData[item.prop];
      if (currentValue === undefined || currentValue === null || currentValue === "") {
        currentData[item.prop] = item.defaultValue;
        hasChanges = true;
      }
    } else if (item.type === "checkbox") {
      currentData[item.prop] = [];
    }
  });

  // 只有当有变化时才触发更新
  if (hasChanges) {
    emit("update:modelValue", currentData);
  }
};

// 监听modelValue和items变化，初始化默认值
watch(
  [() => props.modelValue, () => props.items],
  () => {
    nextTick(() => {
      initializeDefaultValues();
    });
  },
  { immediate: true, deep: true }
);

// 合并全局 rules 和单个 item 的 rules
const formRules = computed(() => {
  const rules: FormRules = { ...props.rules };
  props.items.forEach((item) => {
    if (item.rules && item.prop) {
      rules[item.prop] = item.rules;
    }
    if (item.required && item.prop) {
      rules[item.prop] = rules[item.prop] || [];
      if (Array.isArray(rules[item.prop])) {
        (rules[item.prop] as any[]).push({
          required: true,
          message: `${item.label}不能为空`,
          trigger: "blur",
        });
      }
    }
  });
  return rules;
});

/**
 * 空值判断：用于在查看(禁用)且无值时显示 placeholder "无"
 */
const isEmpty = (val: any) => {
  if (val === null || val === undefined) return true;
  if (typeof val === "string") return val.trim() === "";
  if (Array.isArray(val)) return val.length === 0;
  return false;
};

// 处理后的表单项配置
const formItems = computed(() => {
  return props.items.map((item) => {
    // 处理 slot 类型
    if (item.type === "slot" && !item.slotName) {
      item.slotName = `item-${item.prop}`;
    }
    return item;
  });
});

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return;

  try {
    console.log("提交表单:", formData.value);

    const isValid = await formRef.value.validate();
    emit("validate", isValid);
    if (isValid) {
      // 处理表单数据，特别是文件字段
      const submitData = { ...formData.value };
      // 如果有文件字段，提取FormData
      if (submitData.file && submitData.file.formData) {
        for (const [key, value] of submitData.file.formData.entries()) {
          console.log(`  ${key}:`, value);
        }
        submitData.file = submitData.file.formData;
      } else if (submitData.file === null || submitData.file === undefined) {
        // 如果没有选择文件，从提交数据中移除file相关字段
        delete submitData.file;
        if (!submitData.fileName || submitData.fileName === "") {
          delete submitData.fileName;
        }
      }

      console.log("UForm 最终提交的数据:", submitData);
      emit("submit", submitData);
    }
  } catch (error) {
    console.error(error);
    emit("validate", false);
  }
};

// 重置表单
const handleReset = () => {
  formRef.value?.resetFields();

  emit("reset");
};

// 取消
const handleCancel = () => {
  emit("cancel");
};

// 暴露方法
defineExpose({
  validate: () => formRef.value?.validate(),
  resetFields: () => handleReset(), // 使用自定义的重置方法
  clearValidate: () => formRef.value?.clearValidate(),
  submit: () => handleSubmit(),
  getFormData: () => formData.value,
});
</script>

<style scoped>
:deep(.el-textarea__inner) {
  overflow: hidden;
}
</style>
