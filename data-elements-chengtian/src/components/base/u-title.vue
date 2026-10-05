<template>
  <div class="u-title">
    <div v-if="name || title || $slots['name']" class="title">
      <slot name="name">
        {{ name || title }}
      </slot>
    </div>

    <div class="toolbar">
      <div class="left">
        <slot></slot>
      </div>
      <div class="right">
        <slot name="right"></slot>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { FormRules } from "element-plus";

const props = withDefaults(
  defineProps<{
    name?: string;
    formCreateInject?: { rule: FormRules };
  }>(),
  {}
);

const title = computed(() => props.formCreateInject?.rule?.title);
</script>

<style scoped lang="scss">
.u-title {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 12px 0;
}

.title {
  flex-shrink: 0;
  font-weight: 500;
  color: #20273a;

  &::before {
    position: relative;
    top: 2px;
    display: inline-block;
    align-content: center;
    height: 1em;
    margin-right: 8px;
    content: "";
    border-left: var(--u-title-border-width, 4px) solid var(--el-color-primary);
    border-radius: 8px;
  }
}

.secondary {
  color: #666;
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}
</style>
