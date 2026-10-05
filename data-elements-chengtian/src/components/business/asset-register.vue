<!--
一.按钮组操作流程
1. 按钮组 发起操作, 开始事件流转
2. 父组件接收按钮组发起的操作，通知子组件执行对应事件
3. 子组件执行对应的方法
4. 子组件执行完毕后，通知父组件执行完毕
-->
<template>
  <div v-loading="store.state.loadStatus['main']" class="register-container">
    <div class="register-container__top">
      <div class="register-container__title">{{ store.title }}</div>
      <Steps
        v-if="!store.state.hiddenStep"
        :current="displayCurrentStep"
        :items="displaySteps"
        :on-change="goToCompletedStep"
      ></Steps>
    </div>

    <el-scrollbar class="register-container__body" view-class="h-full">
      <register-body ref="middleRef" @finish-trigger-fn="finishAction" />
    </el-scrollbar>

    <register-action
      v-show="store.state.showAction"
      ref="actionRef"
      class="register-container__action"
      :actions="actions"
      @action="receiveBtnAction"
    />
  </div>
</template>

<script setup lang="ts">
import { storeToRefs } from "pinia";
import { useRegisterStore } from "@/store";
import { computed, nextTick, onBeforeUnmount, onMounted, PropType, provide, ref } from "vue";

// 定义事件类型
const emit = defineEmits<{
  (e: "cancel"): void;
  (e: "completed"): void;
}>();

// 定义组件属性
const props = defineProps({
  initStore: {
    type: Object as PropType<{
      registerClass: string;
      title: string;
      currentStep: number;
      steps: Array<{ title: string; component: string; props: object }>;
    }>,
    required: true,
  },
  actions: {
    type: Array,
  },
});

// 使用store
const store = useRegisterStore();
// 底部操作组件引用
const { actionRef } = storeToRefs(store);

// 步骤条仅显示未标记 hidden 的步骤，内部步骤仍保留用于渲染完成页等隐藏状态。
const visibleSteps = computed(() => store.state.steps.filter((s: any) => !s.hidden));

// 当前内部步骤被隐藏时，导航态落在它前面的最后一个可见步骤上。
const displayCurrentStep = computed(() => {
  const steps = store.state.steps || [];
  const currentStep = Math.min(store.state.currentStep || 0, Math.max(steps.length - 1, 0));
  const visibleBeforeCurrent = steps.slice(0, currentStep + 1).filter((s: any) => !s.hidden).length;
  return Math.max(0, Math.min(visibleBeforeCurrent - 1, visibleSteps.value.length - 1));
});

// 仅允许直接回到已经完成的步骤，避免跳过当前步骤及后续步骤的必填校验。
const displaySteps = computed(() =>
  visibleSteps.value.map((step: any, visibleIndex: number) => ({
    ...step,
    clickable: visibleIndex < displayCurrentStep.value,
  }))
);

const goToCompletedStep = (visibleIndex: number): void => {
  if (visibleIndex < 0 || visibleIndex >= displayCurrentStep.value) return;

  const targetStep = visibleSteps.value[visibleIndex];
  const internalIndex = store.state.steps.findIndex((step: any) => step === targetStep);
  if (internalIndex >= 0) {
    store.setCurrentStep(internalIndex);
  }
};

// 提供操作组件引用给子组件
provide("getActionRef", actionRef);
const middleRef = ref();

// 接收按钮组操作
const receiveBtnAction = async (type: string, done: () => void): Promise<void> => {
  try {
    if (type === "cancel") {
      emit("cancel");
    } else if (type === "pre") {
      store.preStep();
    } else {
      // 组件内部事件完成前保持底部操作按钮为加载且锁定状态。
      await middleRef.value?.handleAction(type);
      // 步骤推进由子组件成功事件触发。等待一次渲染完成后再解除底部按钮的
      // loading，避免第二至第五步切换时出现“旧按钮瞬间恢复可点”的闪烁。
      await nextTick();
    }
  } finally {
    done?.();
  }
};

// 子组件执行事件后，父组件收尾
const finishAction = (type: string | null): void => {
  if (type) {
    switch (type) {
      case "pre":
        store.preStep();
        break;
      case "next":
        store.nextStep();
        break;
      case "completed":
        emit("completed");
        break;
    }
  }
};

// 组件挂载时初始化数据
onMounted(async (): Promise<void> => {
  // 初始化数据，设置步骤
  const { registerClass, title, steps, currentStep = 0, ...reset } = props.initStore || {};
  store.state = Object.assign(store.state, reset);
  store.registerClass = registerClass;
  store.title = title;
  store.setSteps(steps);
  store.setCurrentStep(currentStep);
});

// 组件卸载时重置store
onBeforeUnmount((): void => {
  console.log("清空store...");
  // 重置store中的关键属性
  store.$destory();
});
</script>

<style scoped lang="scss">
.register-container {
  display: flex;
  flex-direction: column;
  height: 100%;

  &__top {
    display: grid;
    flex-shrink: 0;
    grid-template-columns: 15% 1fr 15%;
    align-items: center;
    height: 76px;
    padding: 0 15px 0 20px;
    background-color: #fff;
    border-bottom: var(--border);
  }

  &__title {
    font-family: Arial, sans-serif;
    font-size: 16px;
    font-weight: 700;
    color: #000000e0;
  }

  &__action {
    display: flex;
    justify-content: center;
    padding: 10px 24px;
    text-align: right;
    background-color: #fff;
    border-top: 1px solid #ebeef5;
  }
}
</style>
