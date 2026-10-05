import { useSettingStore } from "@/store";

/**
 * 布局相关的通用逻辑
 */
export function useLayout() {
  const settingStore = useSettingStore();

  // 是否显示标签视图
  const isShowTagsView = computed(() => settingStore.showTagsView);

  // 是否显示设置面板
  const isShowSettings = computed(() => settingStore.showSettings);

  // 是否显示Logo
  const isShowLogo = computed(() => settingStore.showLogo);

  // 布局CSS类
  const layoutClass = computed(() => ({
    mobile: settingStore.device === "mobile",
  }));

  return {
    isShowTagsView,
    isShowSettings,
    isShowLogo,
    layoutClass,
  };
}
