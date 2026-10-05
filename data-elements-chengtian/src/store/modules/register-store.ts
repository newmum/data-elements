import { defineStore } from "pinia";
import { get } from "lodash-es";
import { FormRule } from "@form-create/element-ui";

export interface Asset {
  did?: string; // 唯一标识符
  tid?: string; // 资产ID
  assetName?: string; // 资产名称
  assetClass?: string; // 资产类型
  [name: string]: any; // 其他资产属性，可以根据需要扩展
}

// 定义状态对象类型（约束缓存结构）
interface RegisterState {
  /*登记类型*/
  registerClass: string | undefined;
  /*登记标题*/
  title: string;
  /*当前步骤*/
  currentStep: number;
  /*登记步骤*/
  steps: any[];
  /*加载状态*/
  loadStatus: Record<string, any>;
  showAction: boolean;
  /*是否隐藏步骤条*/
  hiddenStep: boolean;
  /*更新表单配置，增量更新而非覆盖*/
  updateFormRule: FormRule[];
}

// 初始状态（缓存兜底值）
const initialState: RegisterState = {
  registerClass: undefined,
  title: "",
  currentStep: 0,
  steps: [],
  loadStatus: {},
  showAction: true,
  hiddenStep: false,
  updateFormRule: [],
};

export const useRegisterStore = defineStore("register", () => {
  // 核心：整对象存入会话缓存（key: 'register-state'）
  const state = useSessionStorage<RegisterState>(
    "register-state", // 缓存key，自定义
    { ...initialState }, // 初始值
    {
      deep: true, // 深度监听对象变化，同步到缓存
      mergeDefaults: true, // 缓存值缺失时合并初始值
    }
  );

  const data = useSessionStorage(
    "register-data", // 缓存key，自定义
    {} // 初始值
  );

  // 按钮组件
  const actionRef = ref();

  const isFirstStep = computed(() => state.value.currentStep === 0);

  const isLastStep = computed(
    () => state.value.steps.length > 0 && state.value.currentStep === state.value.steps.length - 1
  );

  // 倒数第二步
  const isSecondLastStep = computed(
    () => state.value.steps.length > 1 && state.value.currentStep === state.value.steps.length - 2
  );

  // 当前渲染组件
  const currentComponent = computed(() =>
    get(state.value.steps, `${state.value.currentStep}.component`)
  );

  // 当前渲染组件
  const currentProps = computed(() => get(state.value.steps, `${state.value.currentStep}.props`));

  // 重置方法（同步清空缓存）
  const $reset = () => {
    state.value = { ...initialState }; // 直接替换整对象，自动同步缓存
    data.value = {}; // 直接替换整对象，自动同步缓存
  };

  const $destory = () => {
    $reset();
    sessionStorage.removeItem("register-state");
    sessionStorage.removeItem("register-data");
  };

  // 执行按钮事件方法
  const handleAction = (type: string) => {
    if (!actionRef.value) console.error("按钮组件为空！");
    actionRef.value?.action(type);
  };

  // 步骤相关方法
  const setSteps = (datas: any[]) => {
    state.value.steps = datas || [];
  };

  const setCurrentStep = (step: number) => {
    state.value.currentStep = Math.max(0, Math.min(step, state.value.steps.length - 1));
  };

  const preStep = () => {
    if (state.value.currentStep > 0) {
      state.value.currentStep--;
    }
  };

  const nextStep = () => {
    if (state.value.steps.length > 0 && state.value.currentStep < state.value.steps.length - 1) {
      state.value.currentStep++;
    }
  };

  // 状态控制方法
  const setLoadStatus = (fn: string, status: boolean) => {
    state.value.loadStatus[fn] = status;
  };

  // 事件调度结束方法
  const finishFn = (type: string | null): void => {
    if (type) {
      switch (type) {
        case "pre":
          preStep();
          break;
        case "next":
          nextStep();
          break;
      }
    }
    // 按钮事件调度结束
  };

  // 导出：解构state.value为响应式ref + 计算属性 + 方法（核心：避免嵌套ref）
  return {
    data,
    state,
    actionRef,
    ...toRefs(state.value),
    // 计算属性
    isFirstStep,
    isLastStep,
    isSecondLastStep,
    currentComponent,
    currentProps,
    // 方法
    $reset,
    $destory,
    setSteps,
    setCurrentStep,
    preStep,
    nextStep,
    setLoadStatus,
    finishFn,
    handleAction,
  };
});
