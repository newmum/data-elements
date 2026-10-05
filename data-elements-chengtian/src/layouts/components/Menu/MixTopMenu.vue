<!-- 混合布局顶部菜单 -->
<template>
  <Menus
    mode="horizontal"
    :menus="topMenus"
    :active-menu="activeTopPath"
    background-color="#001529"
    text-color="#ffffffa6"
    active-text-color="#ffff"
  />
</template>

<script lang="ts" setup>
import Menus from "./components/Menus.vue";
import { useLayoutMenu } from "@/layouts/composables/useLayoutMenu";

const { topMenuRoutes, activeTopPath } = useLayoutMenu();

// 顶部菜单列表
const topMenus = computed(() => {
  return topMenuRoutes.value.map((el) => ({
    ...el,
    children: undefined,
  }));
});
</script>

<style lang="scss" scoped>
.el-menu {
  height: 100%;
  color: rgba(255, 255, 255, 0.65);
  background-color: transparent;
  border: none;

  // 缩略符号
  :deep(.el-tooltip__trigger) {
    padding: 0 16px !important;
  }

  &--horizontal {
    height: $navbar-height !important;

    // 确保菜单项垂直居中
    :deep(.el-menu-item),
    :deep(.el-sub-menu__title) {
      padding: 0 34px;
      font-weight: bold;
      color: rgba(255, 255, 255, 0.65);
      border-bottom: none;

      &:hover {
        color: #fff;
        background-color: transparent;
      }

      &.is-active {
        color: #fff !important;
        background-color: var(--el-color-primary);
      }
    }

    // 移除默认的底部边框
    &:after {
      display: none;
    }
  }
}
</style>
