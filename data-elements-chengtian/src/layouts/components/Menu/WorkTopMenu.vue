<!-- 混合布局顶部菜单 -->
<template>
  <Menus
    mode="horizontal"
    :menus="topMenus"
    :active-menu="activeTopPath"
    :background-color="undefined"
    text-color="#323643"
  />
</template>

<script lang="ts" setup>
import Menus from "./components/Menus.vue";
import { useLayoutMenu } from "@/layouts/composables/useLayoutMenu";

const { topMenuRoutes, activeTopPath } = useLayoutMenu();

// 顶部菜单列表
const topMenus = computed(() => {
  return (
    topMenuRoutes.value?.map((el) => ({
      ...el,
      children: undefined,
    })) || []
  );
});
</script>

<style lang="scss" scoped>
.el-menu {
  width: 100%;
  height: 100%;
  color: #323643;
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
      padding: 0 30px;
      font-size: 16px;
      font-weight: 400;
      color: #323643;
      border-bottom: none;

      &:hover {
        font-weight: 600;
        color: var(--el-color-primary);
        background-color: transparent;
      }

      &.is-active {
        font-weight: 600;
        color: var(--el-color-primary) !important;
        background-color: transparent;
        border-bottom: none;

        &::after {
          position: absolute;
          bottom: 0;
          left: calc(50% - 10px);
          width: 20px;
          height: 3px;
          content: "";
          background: var(--el-color-primary);
          border-radius: 1.5px;
          box-shadow: 0 3px 3px #1677ff66;
        }
      }
    }

    // 移除默认的底部边框
    &:after {
      display: none;
    }
  }
}
</style>
