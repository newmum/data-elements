import { ref, provide, inject, type Ref, type InjectionKey } from "vue";

/** 栈中每一项代表一个独立的详情弹窗实例 */
export interface DialogStackItem {
  id: number;
  detailData: any;
  visible: boolean;
  onClose?: () => void;
}

/** 注入值的类型定义 */
export interface DetailDialogContext {
  /** 弹窗栈（BaseLayout 用 v-for 渲染） */
  dialogStack: Ref<DialogStackItem[]>;
  /** 打开弹窗，返回唯一 id；可传 onClose 回调在关闭时触发 */
  openDetailDialog: (data: any, onClose?: () => void) => number;
  /** 关闭指定 id 的弹窗并从栈中移除 */
  closeDetailDialog: (id: number) => void;
}

/** 带类型的 InjectionKey，inject 可自动推断类型 */
const DETAIL_DIALOG_KEY: InjectionKey<DetailDialogContext> = Symbol("detail-dialog");

/** 自增 id，保证每个弹窗实例唯一 */
let dialogIdCounter = 0;

/**
 * 详情全屏弹窗 — 全局 provide（根组件使用）
 * 栈式管理，支持嵌套打开
 */
export function provideDetailDialog() {
  const dialogStack = ref<DialogStackItem[]>([]);

  const openDetailDialog = (data: any, onClose?: () => void) => {
    const id = ++dialogIdCounter;
    dialogStack.value.push({
      id,
      detailData: data,
      visible: true,
      onClose,
    });
    return id;
  };

  const closeDetailDialog = (id: number) => {
    const idx = dialogStack.value.findIndex((d) => d.id === id);
    if (idx !== -1) {
      const item = dialogStack.value[idx];
      dialogStack.value.splice(idx, 1);
      // 在从栈中移除后触发回调，此时上层弹窗已关闭
      item.onClose?.();
    }
  };

  provide(DETAIL_DIALOG_KEY, {
    dialogStack,
    openDetailDialog,
    closeDetailDialog,
  });

  // 返回供根组件模板使用的状态
  return {
    dialogStack,
    closeDetailDialog,
  };
}

/**
 * 注入目录详情弹窗方法（任意组件使用）
 */
export function useDetailDialog(): DetailDialogContext {
  const dialog = inject(DETAIL_DIALOG_KEY);
  if (!dialog) {
    if (import.meta.env.DEV) {
      console.error("请确保调用了 provideDetailDialog");
    }
    // 兜底空方法，避免页面报错
    return {
      dialogStack: ref([]),
      openDetailDialog: () => 0,
      closeDetailDialog: () => {},
    };
  }
  return dialog;
}
