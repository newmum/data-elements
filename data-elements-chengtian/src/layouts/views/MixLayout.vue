<template>
  <BaseLayout>
    <!-- 顶部菜单栏 -->
    <div class="layout__header">
      <div class="layout__header-content">
        <!-- Logo区域 -->
        <div class="layout__header-logo">
          <AppLogo :collapse="false" theme="dark" />
        </div>

        <!-- 顶部菜单区域 -->
        <div class="layout__header-menu">
          <MixTopMenu />
        </div>

        <!-- 右侧操作区域 -->
        <div class="layout__header-actions">
          <NavbarActions theme="dark" />
        </div>
      </div>
    </div>

    <!-- 主内容区容器 -->
    <div class="layout__container">
      <!-- 左侧菜单栏 -->
      <div
        v-if="showSidebar"
        class="layout__sidebar--left"
        :class="{ 'layout__sidebar--collapsed': !sidebarOpen }"
      >
        <el-scrollbar>
          <SideMenu :open="sidebarOpen" />
        </el-scrollbar>
        <!-- 侧边栏切换按钮 -->
        <div class="layout__sidebar-toggle" @click="sidebarOpen = !sidebarOpen">
          <Icon :icon="sidebarOpen ? 'el-icon-fold' : 'el-icon-expand'" size="18px" />
        </div>
      </div>

      <!-- 主内容区 -->
      <div :class="{ hasTagsView: isShowTagsView }" class="layout__main">
        <TagsView v-if="isShowTagsView" />
        <AppMain />
      </div>
    </div>
  </BaseLayout>
</template>

<script setup lang="ts">
import { useLayout } from "../composables/useLayout";
import BaseLayout from "./BaseLayout.vue";
import AppLogo from "../components/AppLogo/index.vue";
import MixTopMenu from "../components/Menu/MixTopMenu.vue";
import NavbarActions from "../components/NavBar/components/NavbarActions.vue";
import TagsView from "../components/TagsView/index.vue";
import AppMain from "../components/AppMain/index.vue";
import SideMenu from "@/layouts/components/Menu/SideMenu.vue";
import { useSettingStore } from "@/store";
import { useLayoutMenu } from "@/layouts/composables/useLayoutMenu";

// 布局相关参数
const { isShowTagsView } = useLayout();
const { sideMenuRoutes } = useLayoutMenu();
const store = useSettingStore();
const { sidebarOpen } = storeToRefs(store);
const showSidebar = computed(
  () =>
    sideMenuRoutes.value.some((el) => !el.meta?.hidden && el.meta?.type !== "group") &&
    sideMenuRoutes.value.length > 1
);
</script>

<style lang="scss" scoped>
.layout {
  --menu-background: linear-gradient(135deg, #273c9b, #273c9b 185px, #132e72 0, #132e72);

  &__header {
    position: sticky;
    top: 0;
    z-index: 999;
    width: 100%;
    height: $navbar-height;
    background: var(--menu-background);

    &:before {
      position: absolute;
      top: 0;
      left: -20px;
      z-index: -1;
      width: 250px;
      content: "";
      border-right: 50px solid transparent;
      border-bottom: 55px solid #0f3290;
      border-left: 20px solid transparent;
    }

    &-content {
      display: flex;
      align-items: center;
      height: 100%;
      padding: 0;
    }

    &-menu {
      display: flex;
      flex: 1;
      align-items: center;
      min-width: 0;
      height: 100%;
      overflow: hidden;
    }

    &-actions {
      display: flex;
      flex-shrink: 0;
      align-items: center;
      height: 100%;
      padding: 0 1px 0 24px;
    }
  }

  &__container {
    --sidebar-width: 236px;

    display: flex;
    height: calc(100vh - $navbar-height);

    .layout__sidebar--left {
      position: relative;
      z-index: 1;
      width: var(--sidebar-width);
      height: 100%;
      overflow: hidden;
      background-color: #fff;
      box-shadow: 0 0 6px #0d1b5c33;
      transition: width 0.35s cubic-bezier(0.4, 0, 0.2, 1);

      &.layout__sidebar--collapsed {
        width: var(--sidebar-width-collapsed) !important;
      }

      :deep(.el-scrollbar) {
        height: calc(100vh - $navbar-height - 50px);
      }

      .layout__sidebar-toggle {
        position: absolute;
        bottom: 0;
        display: flex;
        align-items: center;
        justify-content: center;
        width: 100%;
        height: 50px;
        line-height: 50px;
        cursor: pointer;
        box-shadow: 0 3px 6px #686c8029;
      }
    }
  }
}

:deep(.hasTagsView) {
  .app-main {
    height: calc(100vh - $navbar-height - $tags-view-height) !important;
  }
}

.layout__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  overflow-y: auto;

  .app-main {
    display: flex;
    flex: 1;
    flex-direction: column;
    padding: 16px 16px 0;
    overflow-y: auto;
  }
}
</style>
