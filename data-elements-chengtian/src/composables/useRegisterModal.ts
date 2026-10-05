import { ref, provide, inject, type Ref, type InjectionKey } from "vue";
import { useRegisterStore } from "@/store";

/** 注入值的类型定义 */
export interface RegisterModalContext {
  open: Ref<boolean>;
  completedVersion: Ref<number>;
  registerData: Ref<Record<string, unknown>>;
  registerClass: Ref<string>;
  registerMode: Ref<string>;
  updateFormRule: Ref<unknown[]>;
  openRegisterModal: (options?: any) => void;
}

/** 带类型的 InjectionKey，inject 可自动推断类型 */
const REGISTER_MODAL_KEY: InjectionKey<RegisterModalContext> = Symbol("register-modal");

/**
 * 低代码页面由运行时动态挂载，部分页面不在 BaseLayout 的组件注入链上。
 * 保留最近一次由 BaseLayout 提供的上下文，确保这类页面也能打开同一个登记弹窗，
 * 而不是落入静默的空实现。
 */
let activeRegisterModalContext: RegisterModalContext | null = null;

/**
 * 提供全局登记弹框的方法（根组件使用）
 */
export function provideRegisterModal() {
  // 1. 弹框显隐状态（对应 v-model）
  const open = ref(false);
  const completedVersion = ref(0);
  // 2. 弹框的核心数据（对应 :data）
  const registerData = ref<Record<string, unknown>>({});
  // 3. 弹框的自定义类名（对应 register-class）
  const registerClass = ref("");
  // 显式记录入口流程语义；未传时始终使用既有标准登记流程。
  const registerMode = ref("");
  // 4. 更新表单rule，通常更新表单中的props
  const updateFormRule = ref([]);

  // 5. 打开弹框的方法（支持传入自定义参数）
  const openRegisterModal = (options: any = {}) => {
    const registerStore = useRegisterStore();
    registerStore.$reset();
    sessionStorage.removeItem("register-state");
    sessionStorage.removeItem("register-data");

    registerData.value = { ...(options.registerData || {}) };
    updateFormRule.value = options.updateFormRule || [];
    registerClass.value = options.registerClass || "app";
    registerMode.value = options.registerMode || "";
    open.value = true;
  };

  // 6. 关闭弹框的方法
  const closeRegisterModal = () => {
    open.value = false;
    registerData.value = {};
    updateFormRule.value = [];
    registerMode.value = "";
  };

  const completeRegisterModal = () => {
    completedVersion.value += 1;
    closeRegisterModal();
  };

  // 7. 提供给全局注入（InjectionKey 自动约束类型）
  const context: RegisterModalContext = {
    open,
    completedVersion,
    registerData,
    registerClass,
    registerMode,
    updateFormRule,
    openRegisterModal,
  };
  activeRegisterModalContext = context;
  provide(REGISTER_MODAL_KEY, context);

  // 返回供根组件模板使用的状态
  return {
    open,
    completedVersion,
    registerData,
    registerClass,
    registerMode,
    updateFormRule,
    closeRegisterModal,
    completeRegisterModal,
  };
}

/**
 * 注入全局登记弹框的方法（任意触发组件使用）
 */
export function useRegisterModal(): RegisterModalContext {
  const modal = inject(REGISTER_MODAL_KEY);
  if (modal) {
    return modal;
  }

  // 低代码运行时组件可能脱离注入链，但仍应复用根布局中的同一登记弹窗。
  if (activeRegisterModalContext) {
    return activeRegisterModalContext;
  }

  // 根布局尚未初始化时保留安全兜底，避免渲染阶段异常；同时明确报错便于定位。
  console.error("登记弹窗尚未初始化，请确保页面由 BaseLayout 承载");
  return {
    open: ref(false),
    completedVersion: ref(0),
    registerData: ref({}),
    registerClass: ref(""),
    registerMode: ref(""),
    updateFormRule: ref([]),
    openRegisterModal: () => {},
  };
}
