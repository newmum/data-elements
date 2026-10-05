<template>
  <el-tabs
    v-model="activeTab"
    v-bind="$attrs"
    :style="{
      '--bottom-border-color': showBorder ? 'var(--el-border-color-light)' : 'transparent',
    }"
    @tab-change="(key) => emit('change', key)"
  >
    <template v-for="tab in tabs" :key="tab.value">
      <el-tab-pane v-if="!tab.hidden" :label="tab.label" :name="tab.value">
        <slot :name="'tab-' + tab.value"></slot>
      </el-tab-pane>
    </template>
  </el-tabs>
</template>

<script setup lang="ts">
import { useVModel } from "@vueuse/core";

const props = defineProps<{
  modelValue: string;
  tabs: Array<{ label: string; value: string; hidden?: boolean }>;
  showBorder?: boolean;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", value: string): void;
  (e: "change", value: string | number): void;
}>();

const activeTab = useVModel(props, "modelValue", emit);
</script>

<style lang="scss" scoped>
:deep(.el-tabs__header) {
  margin: 0;
}
:deep(.el-tabs__nav-wrap:after) {
  //--el-border-color-light: var(--bottom-border-color, --el-border-color-light);
  height: 1px;
  background-color: var(--bottom-border-color);
}
</style>
