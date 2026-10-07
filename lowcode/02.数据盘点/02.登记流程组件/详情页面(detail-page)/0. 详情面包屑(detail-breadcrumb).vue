<template>
<breadcrumb
  :breadcrumbs="breadcrumbs"
  :back="routeBack"
/>
</template>

<script setup lang="ts">
import { onMounted, computed } from "vue";
import { useDetailStore, usePermissionStore } from "@/store";
import { storeToRefs } from "pinia";
import { useRoute, useRouter, onBeforeRouteUpdate, onBeforeRouteLeave } from "vue-router";

const route = useRoute();
const router = useRouter();
const detailStore = useDetailStore();
  
// 详情初始化
const type = (route.query.type as string) || "approve";
const title = (route.query.title as string);

// 方便资产详情页添加mock数据
const breadcrumbs = computed(() => {
  const matched = route.matched.filter((item) => item.meta && item.meta.title);
  const a = matched.filter((item) => {
    // 个人中心特殊，要显示在面包屑中
    return (item.meta && item.meta.title && !item.meta.hidden) || item.name === "workplace";
  });
  return [...a, ...detailStore.history];
});

/**
 * 以下代码，实现详情面包屑嵌套
 */
onMounted(() => {
  const findIndex = detailStore.findHistory(route.query);
  if (findIndex === -1) {
    detailStore.history.push({
      path: route.path,
      query: route.query,
      meta: {
        title: route.query.title,
        icon: route.query.type === "approve" ? null : route.query.type,
      },
    });
  }
  if (route.query.tab) {
    detailStore.currentTab = (route.query.tab as string) || "";
  } else {
    detailStore.initTab();
  }
});

// 面包屑返回方法重写
const routeBack = () => {
  // 有两个及以上详情页
  const { path, query } = breadcrumbs.value[breadcrumbs.value.length - 2] as any;
  router.push({ path, query });
};

// 详情页间的跳转，则缓存或移除当前详情
onBeforeRouteUpdate((to, from, next) => {
  const findIndex = detailStore.findHistory(to.query);
  // 若前往新详情则添加记录
  if (findIndex === -1) {
    // 缓存当前详情页签
    const last = detailStore.history[detailStore.history.length - 1];
    last.query = Object.assign({ tab: detailStore.currentTab }, last.query);

    detailStore.history.push({
      path: to.path,
      query: to.query,
      meta: { title: to.query.title, icon: to.query.type === "approve" ? null : to.query.type },
    });
  }
  // 若前往历史详情，则移除后面的详情页
  else {
    detailStore.history = detailStore.history.slice(0, findIndex + 1);
  }
  next();
});

// 离开详情页，则销毁记录
onBeforeRouteLeave((to, from, next) => {
  next();
  setTimeout(() => {
    detailStore.$destroy();
  }, 100);
});
</script>