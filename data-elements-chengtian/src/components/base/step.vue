<template>
  <div
    class="step-item"
    :class="[
      `step-status-${status}`,
      { 'step-active': isActive },
      { 'step-finish': isFinish },
      { 'step-error': status === 'error' },
      { 'step-vertical': direction === 'vertical' },
      { 'step-clickable': canClick },
    ]"
    :role="canClick ? 'button' : undefined"
    :tabindex="canClick ? 0 : undefined"
    :aria-label="canClick ? `前往第 ${index + 1} 步：${title || ''}` : undefined"
    @click="handleClick"
    @keydown.enter.prevent="handleClick"
    @keydown.space.prevent="handleClick"
  >
    <div class="step-head">
      <div class="step-icon">
        <slot name="icon">
          <template v-if="isFinish && !icon">
            <svg
              focusable="false"
              data-icon="check"
              width="1em"
              height="1em"
              fill="currentColor"
              aria-hidden="true"
              viewBox="64 64 896 896"
            >
              <path
                d="M912 190h-69.9c-9.8 0-19.1 4.5-25.1 12.2L404.7 724.5 207 474a32 32 0 00-25.1-12.2H112c-6.7 0-10.4 7.7-6.3 12.9l273.9 347c12.8 16.2 37.4 16.2 50.3 0l488.4-618.9c4.1-5.1.4-12.8-6.3-12.8z"
              ></path>
            </svg>
          </template>
          <template v-else-if="icon">
            <Icon :icon="icon" />
          </template>
          <template v-else>
            {{ index + 1 }}
          </template>
        </slot>
      </div>
    </div>
    <div class="step-content">
      <div class="step-title">
        <slot name="title" v-bind="props">
          {{ title }}
        </slot>
      </div>
      <div class="step-description">
        <slot name="description" v-bind="props">
          {{ description }}
        </slot>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Ref } from "vue";
import type { StepsStatus, StepsDirection } from "./steps.vue";

interface StepsProvide {
  current: Ref<number>;
  status: Ref<StepsStatus>;
  direction: Ref<StepsDirection>;
  registerStep: (step: { index: number; status?: StepsStatus }) => void;
  unregisterStep: (index: number) => void;
}

const props = defineProps<{
  /** 步骤索引，从 0 开始 */
  index: number;
  /** 步骤状态 */
  status?: StepsStatus;
  /** 是否可点击 */
  clickable?: boolean;
  /** 图标 */
  icon?: string;
  title?: string;
  description?: string;
}>();

const emits = defineEmits<{
  (e: "click", index: number): void;
}>();

// 从父组件注入
const steps = inject<StepsProvide>("steps");
if (!steps) {
  throw new Error("Step must be used within Steps");
}

const { current, status: stepsStatus, direction, registerStep, unregisterStep } = steps;

// 注册当前步骤
onMounted(() => {
  registerStep({ index: props.index, status: props.status });
});

// 注销当前步骤
onUnmounted(() => {
  unregisterStep(props.index);
});

// 计算当前步骤状态
const status = computed<StepsStatus>(() => {
  // 优先使用自身状态
  if (props.status) {
    return props.status;
  }

  // 错误状态
  if (stepsStatus.value === "error" && props.index === current.value) {
    return "error";
  }

  // 完成状态
  if (props.index < current.value) {
    return "finish";
  }

  // 当前状态
  if (props.index === current.value) {
    return stepsStatus.value || "process";
  }

  // 等待状态
  return "wait";
});

// 是否为当前步骤
const isActive = computed(() => props.index === current.value);

// 是否为已完成步骤
const isFinish = computed(() => props.index < current.value || status.value === "finish");

const canClick = computed(() => props.clickable && props.index !== current.value);

// 点击处理
const handleClick = () => {
  if (canClick.value) {
    emits("click", props.index);
  }
};
</script>

<style scoped lang="scss"></style>
