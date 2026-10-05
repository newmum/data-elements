<template>
  <div class="flex">
    <el-input-number
      v-model="min"
      class="mr-10px"
      :min="0"
      placeholder="最小"
      v-bind="{ ...$attrs, ...props.minProps }"
      @change="changeMin"
    ></el-input-number>
    -
    <el-input-number
      v-model="max"
      :min="0"
      placeholder="最大"
      class="ml-10px"
      v-bind="{ ...$attrs, ...props.maxProps }"
      @change="changeMax"
    ></el-input-number>
  </div>
</template>

<script setup lang="ts">
const emit = defineEmits<{
  (e: "update:modelValue", value: any): void;
}>();

const props = defineProps({
  modelValue: Array,
  minProps: {
    type: Object,
    default: () => ({}),
  },
  maxProps: {
    type: Object,
    default: () => ({}),
  },
});

const min = ref();
const max = ref();

const changeMin = (val: number | undefined) => {
  if (
    max.value !== null &&
    val !== null &&
    max.value !== undefined &&
    val !== undefined &&
    val > max.value
  )
    emit("update:modelValue", [max.value, val]);
  else emit("update:modelValue", [val, max.value]);
};

const changeMax = (val: number | undefined) => {
  emit("update:modelValue", [min.value, val]);
  if (
    min.value !== null &&
    val !== null &&
    min.value !== undefined &&
    val !== undefined &&
    val < min.value
  )
    emit("update:modelValue", [val, min.value]);
  else emit("update:modelValue", [min.value, val]);
};

watch(
  () => props.modelValue,
  (val) => {
    const [a, b] = val ?? [];
    min.value = a;
    max.value = b;
  },
  { immediate: true }
);
</script>

<style lang="scss" scoped></style>
