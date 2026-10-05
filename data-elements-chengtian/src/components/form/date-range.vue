<template>
  <el-date-picker
    v-model="innerValue"
    start-placeholder="请选择开始日期"
    end-placeholder="请选择结束日期"
    value-format="YYYY-MM-DD HH:mm:ss"
    type="daterange"
    v-bind="$attrs"
    v-on="$attrs"
  >
    <!-- 极简插槽透传 -->
    <slot v-for="key in Object.keys($slots)" :name="key" v-bind="[$slots[key]]"></slot>
  </el-date-picker>
</template>

<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    modelValue?: string | undefined;
    separator?: string; // 分隔符，默认逗号
  }>(),
  {
    separator: ",",
    valueFormat: "YYYY-MM-DD HH:mm:ss",
  }
);
const emit = defineEmits<{ "update:modelValue": [v: string | undefined] }>();

// 外部→内部：字符串拆分数组
const strToArr = (str?: string) => {
  if (!str) return undefined;
  try {
    const [start, end] = str.split(props.separator);
    return [start, end];
  } catch {
    console.warn(`data-range.vue 解析【${str}】失败`);
    return [];
  }
};

// 内部→外部：数组合并字符串
const arrToStr = (arr?: []) => {
  const str = arr?.every((v) => v) ? arr.join(props.separator) : undefined;
  if (str === props.separator) return undefined;
  return str;
};

const innerValue = computed({
  get: () => {
    return strToArr(props.modelValue);
  },
  set: (val?: []) => {
    emit("update:modelValue", arrToStr(val));
  },
});

// 清空方法
defineExpose({ clear: () => emit("update:modelValue", undefined) });
</script>

<style scoped>
:deep(.el-date-picker) {
  width: 100%;
}
</style>
