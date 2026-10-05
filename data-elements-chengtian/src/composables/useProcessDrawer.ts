import { ref, provide, inject, type Ref, type InjectionKey } from "vue";

/** 注入值的类型定义 */
export interface ProcessDrawerContext {
  processDrawerVisible: Ref<boolean>;
  processDrawerData: Ref<any>;
  openProcessDrawer: (data: any) => void;
  closeProcessDrawer: () => void;
}

/** 带类型的 InjectionKey，inject 可自动推断类型 */
const PROCESS_DRAWER_KEY: InjectionKey<ProcessDrawerContext> = Symbol("process-drawer");

/**
 * 业务事项详情抽屉 — 全局 provide（根组件使用）
 * 全局持久化，避免被子组件卸载影响
 */
export function provideProcessDrawer() {
  const processDrawerVisible = ref(false);
  const processDrawerData = ref<any>(null);

  const openProcessDrawer = (data: any) => {
    processDrawerData.value = data;
    processDrawerVisible.value = true;
  };

  const closeProcessDrawer = () => {
    processDrawerVisible.value = false;
    processDrawerData.value = null;
  };

  provide(PROCESS_DRAWER_KEY, {
    processDrawerVisible,
    processDrawerData,
    openProcessDrawer,
    closeProcessDrawer,
  });

  // 返回供根组件模板使用的状态
  return {
    processDrawerVisible,
    processDrawerData,
    closeProcessDrawer,
  };
}

/**
 * 注入业务事项详情抽屉方法（任意组件使用）
 */
export function useProcessDrawer(): ProcessDrawerContext {
  const drawer = inject(PROCESS_DRAWER_KEY);
  if (!drawer) {
    if (import.meta.env.DEV) {
      console.error("请确保调用了 provideProcessDrawer");
    }
    return {
      processDrawerVisible: ref(false),
      processDrawerData: ref(null),
      openProcessDrawer: (_data: any) => {},
      closeProcessDrawer: () => {},
    };
  }
  return drawer;
}
