<template>
  <div
    class="steps-container"
    :class="[
      { 'steps-horizontal': direction === 'horizontal' },
      { 'steps-vertical': direction === 'vertical' },
    ]"
  >
    <slot>
      <Step
        v-for="(item, index) in items"
        :key="index"
        :index="index"
        v-bind="item"
        @click="handleStepClick"
      >
        <!-- 插槽透传 -->
        <template v-for="(value, name) in $slots" #[name]="slotProps">
          <slot :name="name" v-bind="slotProps"></slot>
        </template>
      </Step>
    </slot>
  </div>
</template>

<script setup lang="ts">
import type { Ref } from "vue";
import Step from "./step.vue";

export type StepsDirection = "horizontal" | "vertical";
export type StepsStatus = "wait" | "process" | "finish" | "error";

interface StepsProvide {
  current: Ref<number>;
  status: Ref<StepsStatus>;
  direction: Ref<StepsDirection>;
  registerStep: (step: { index: number; status?: StepsStatus }) => void;
  unregisterStep: (index: number) => void;
}

const props = defineProps<{
  /** 当前步骤索引，从 0 开始 */
  current?: number;
  /** 步骤条方向 */
  direction?: StepsDirection;
  /** 步骤条状态 */
  status?: StepsStatus;
  /** 步骤变更回调 */
  onChange?: (current: number) => void;
  /** 步骤列表 */
  items: { title: string; description?: string; status?: StepsStatus; icon?: string; clickable?: boolean }[];
}>();

const handleStepClick = (index: number): void => {
  props.onChange?.(index);
};

// 定义默认值
const current = ref(props.current ?? 0);
const status = ref<StepsStatus>(props.status ?? "process");
const direction = ref<StepsDirection>(props.direction ?? "horizontal");
const steps = ref<Array<{ index: number; status?: StepsStatus }>>([]);

// 监听 props 变化
watch(
  () => props.current,
  (val) => {
    if (val !== undefined) {
      current.value = val;
    }
  }
);

watch(
  () => props.status,
  (val) => {
    if (val) {
      status.value = val;
    }
  }
);

watch(
  () => props.direction,
  (val) => {
    if (val) {
      direction.value = val;
    }
  }
);

// 注册步骤
const registerStep = (step: { index: number; status?: StepsStatus }) => {
  steps.value.push(step);
  steps.value.sort((a, b) => a.index - b.index);
};

// 注销步骤
const unregisterStep = (index: number) => {
  steps.value = steps.value.filter((step) => step.index !== index);
};

// 提供给子组件
provide<StepsProvide>("steps", {
  current,
  status,
  direction,
  registerStep,
  unregisterStep,
});
</script>

<style scoped lang="scss">
.steps-container {
  --head-bg-color: #e8f4ff;
  --head-border-color: #e8f4ff;
  --head-color: var(--el-color-primary);
  --line-color: rgba(5, 5, 5, 0.06);
  --text-color: rgba(0, 0, 0, 0.88);
  position: relative;

  display: flex;
  gap: 16px;
  width: 100%;

  // 步骤项基础样式
  :deep(.step-item) {
    position: relative;
    z-index: 2;
    display: flex;
    overflow: hidden;

    // 头部图标
    .step-head {
      z-index: 1;
      display: flex;
      flex-shrink: 0;
      align-items: center;
      justify-content: center;
      width: 32px;
      height: 32px;
      margin-right: 8px;
      color: var(--head-color);
      background-color: var(--head-bg-color);
      border: 1px solid var(--head-border-color);
      border-radius: 50%;
      transition: all 0.3s ease;
    }

    // 图标容器
    .step-icon {
      position: relative;
      display: flex;
      align-items: center;
      justify-content: center;
      line-height: 1;
    }
    .step-content {
      display: inline-block;
      vertical-align: top;
    }

    // 标题样式
    .step-title {
      position: relative;
      display: inline-block;
      font-size: 16px;
      line-height: 32px;
      color: var(--text-color);
    }

    // 描述文字
    .step-description {
      font-size: 14px;
      color: var(--text-color);
    }
  }

  // 状态样式 - 已完成
  :deep(.step-status-finish) {
    --head-bg-color: #e8f4ff;
    --head-border-color: #e8f4ff;
    --head-color: var(--el-color-primary);

    .step-title::after {
      --line-color: var(--el-color-primary);
    }

    &.step-clickable:hover {
      cursor: pointer;
      --head-border-color: var(--el-color-primary);
      --head-color: var(--el-color-primary);
      --text-color: var(--el-color-primary);
    }
  }

  // 状态样式 - 进行中
  :deep(.step-status-process) {
    .step-head {
      --head-bg-color: var(--el-color-primary);
      --head-border-color: var(--el-color-primary);
      --head-color: #fff;
    }
  }

  // 状态样式 - 错误
  :deep(.step-status-error) {
    --head-bg-color: var(--el-color-error);
    --head-border-color: var(--el-color-error);
    --head-color: #fff;

    .step-title::after {
      background-color: var(--el-color-error);
    }
  }

  // 状态样式 - 等待
  :deep(.step-status-wait) {
    --head-bg-color: rgba(0, 0, 0, 0.06);
    --head-border-color: transparent;
    --head-color: rgba(0, 0, 0, 0.65);
    --text-color: #00000073;

    &.step-clickable:hover {
      cursor: pointer;
      --head-border-color: var(--el-color-primary);
      --head-color: var(--el-color-primary);
      --text-color: var(--el-color-primary);
    }
  }

  :deep(.step-clickable:focus-visible) {
    outline: 2px solid var(--el-color-primary);
    outline-offset: 3px;
    border-radius: 4px;
  }
}

// 水平方向布局
.steps-horizontal {
  flex-direction: row;
  gap: 12px;

  :deep(.step-item) {
    flex: 1 1 0;
    min-width: 0;

    &:last-child {
      flex: 0 0 auto;
      overflow: visible;
    }

    // 非最后一项样式
    &:not(:last-child) {
      .step-content {
        flex: 0 0 auto;
        width: auto;
      }

      .step-title {
        display: block;
        width: 100%;
        font-size: 14px;
        white-space: nowrap;
      }

      // 尾部线段
      .step-title::after {
        position: absolute;
        inset-inline-start: 100%;
        top: 16px;
        width: 9999px;
        height: 1px;
        margin-left: 8px;
        content: "";
        background: var(--line-color);
      }
    }
  }
}

// 垂直方向布局
.steps-vertical {
  flex-direction: column;
  :deep(.step-item) {
    .step-content {
      display: flex;
      flex-direction: column;
      margin-left: 0;
    }
    // 非最后一项样式
    &:not(:last-child) {
      flex: 1;

      &::before {
        position: absolute;
        inset-inline-start: 15px;
        top: 50px;
        width: 1px;
        height: 100%;
        content: "";
        background: var(--line-color);
      }

      .step-content {
        margin-right: 16px;
      }
    }
  }
}
</style>
