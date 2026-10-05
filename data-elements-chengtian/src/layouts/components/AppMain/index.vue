<template>
  <section class="app-main" :style="{ height: appMainHeight }">
    <router-view>
      <template #default="{ Component, route }">
        <transition enter-active-class="animate__animated animate__fadeIn" mode="out-in">
          <keep-alive :include="isShowTagsView ? cachedTagViews : cachedListViews">
            <component
              :is="currentComponent(Component, route)"
              :key="route.fullPath"
              v-bind="(route.meta as any)?.params"
            />
          </keep-alive>
        </transition>
      </template>
    </router-view>
  </section>
</template>

<script setup lang="ts">
import { type RouteLocationNormalized } from "vue-router";
import { useSettingStore, useTagsViewStore } from "@/store";
import Error404 from "@/views/error/404.vue";
import { useListRouteCache } from "@/composables/useListRouteCache";
import { useLayout } from "@/layouts/composables/useLayout";

const props = defineProps({
  height: String,
});

const { isShowTagsView } = useLayout();
// tag视图缓存，会结合keepAlive
const { cachedViews: cachedTagViews } = useTagsViewStore();
// 列表->详情, 缓存列表
const { cachedViews: cachedListViews } = useListRouteCache();

// 当前组件
const wrapperMap = new Map<string, Component>();
const currentComponent = (component: Component, route: RouteLocationNormalized) => {
  if (!component) return;

  const { fullPath: componentName } = route; // 使用路由路径作为组件名称
  let wrapper = wrapperMap.get(componentName);

  if (!wrapper) {
    wrapper = {
      name: componentName,
      render: () => {
        try {
          return h(component);
        } catch (error) {
          console.error(`Error rendering component for route: ${componentName}`, error);
          return h(Error404);
        }
      },
    };
    wrapperMap.set(componentName, wrapper);
  }

  // 添加组件数量限制
  if (wrapperMap.size > 100) {
    const firstKey = wrapperMap.keys().next().value;
    if (firstKey) {
      wrapperMap.delete(firstKey);
    }
  }

  return h(wrapper);
};

const appMainHeight = computed(() => {
  if (props.height) return props.height as string;
  if (useSettingStore().showTagsView) {
    return `calc(100vh - var(--navbar-height) - var(--tags-view-height)})`;
  } else {
    return `calc(100vh - var(--navbar-height))`;
  }
});
</script>

<style lang="scss" scoped>
.app-main {
  position: relative;
  background-color: var(--el-bg-color-page);

  /* 布局切换动画优化 */
  &.animate__animated {
    animation-duration: 0.1s;
    animation-fill-mode: forwards;
  }

  &.animate__fadeOut {
    animation-timing-function: ease-in;
  }

  &.animate__fadeIn {
    animation-timing-function: ease-out;
  }
}
</style>
