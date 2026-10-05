import { defineStore } from "pinia";
import { ref, computed } from "vue";
import { useSessionStorage } from "@vueuse/core";
import { RouteMeta } from "vue-router";
import { isEmpty, isEqual, pickBy } from "lodash-es";

// 定义资产信息类型
interface DetailTab {
  title: string;
  component: string;
  props: {};
}

export const useDetailStore = defineStore("detail", () => {
  // 状态
  const config = ref({});
  const currentTab = ref("");
  const history = useSessionStorage<RouteMeta[]>("detail-history", []); // 存放详情 路由记录
  const data = useSessionStorage("detail-data", {});
  const state = useSessionStorage("detail-state", {
    detailClass: "app", // 资产详情类别
    tabs: [] as DetailTab[],
  });

  // 计算属性
  const current = computed(() => {
    const com = state.value.tabs.find((el: any) => el.title === currentTab.value);
    return com || ({} as DetailTab);
  });

  const isEditable = computed(() => data.value?.flowStatus !== 1);

  const setData = (info: object) => {
    data.value = { ...data.value, ...info };
  };

  const init = (type: string, detailConfig: {} = {}) => {
    if (isEmpty(detailConfig)) {
      console.warn("资产详情页签配置有误，请检查...");
    }
    state.value.detailClass = type;
    config.value = detailConfig;
    state.value.tabs = detailConfig.tabs || [];
  };

  const initTab = () => {
    if (config.value.tabs.length > 0) {
      currentTab.value = config.value.tabs[0].title;
    }
  };

  // 重置状态
  const $reset = () => {
    data.value = null;
    currentTab.value = "";
    history.value = [];
  };

  // 销毁
  const $destroy = () => {
    $reset();
    sessionStorage.removeItem("detail-data");
    sessionStorage.removeItem("detail-state");
    sessionStorage.removeItem("detail-history");
  };

  const setCurrentTab = (tab: string) => {
    currentTab.value = tab;
  };

  // 查找详情浏览记录
  const findHistory = (query: object) => {
    // 比对query参数是否一致，排除tab 属性， tab作为缓存标签页
    const a = pickBy(query, (v, k) => k !== "tab");
    return history.value.findIndex((el: any) => {
      return isEqual(
        pickBy(el.query, (v, k) => k !== "tab"),
        a
      );
    });
  };

  return {
    // 状态
    data,
    state,
    config,
    currentTab,
    history,
    isEditable,

    current,

    // 方法
    $reset,
    $destroy,
    init,
    initTab,
    setCurrentTab,
    findHistory,
    setData,
  };
});
