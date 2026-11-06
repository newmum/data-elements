<template>
  <Menus
    class="top-menu"
    :class="{ 'top-menu--dark': dark }"
    mode="horizontal"
    :menus="topMenus"
    :active-menu="activeTopPath"
    :background-color="dark ? '#001529' : undefined"
    :text-color="dark ? '#ffffffa6' : '#323643'"
    :active-text-color="dark ? '#fff' : undefined"
  />
</template>

<script lang="ts" setup>
import Menus from "./components/Menus.vue";
import { useLayoutMenu } from "@/layouts/composables/useLayoutMenu";

defineProps<{ dark?: boolean }>();

const { topMenuRoutes, activeTopPath } = useLayoutMenu();

const topMenus = computed(() =>
  topMenuRoutes.value.map((route) => ({ ...route, children: undefined }))
);
</script>

<style lang="scss" scoped>
.top-menu {
  --top-menu-padding: clamp(10px, 1.35vw, 30px);
  --top-menu-font-size: 16px;
  --top-menu-weight: 400;
  --top-menu-highlight-weight: 600;
  --top-menu-text: #323643;
  --top-menu-active-text: var(--el-color-primary);
  --top-menu-active-background: transparent;

  background-color: transparent;
  border: none;

  :deep(.el-tooltip__trigger) {
    padding: 0 16px !important;
  }

  :deep(.el-menu-item) {
    justify-content: center;
  }

  :deep(.menu-item-label) {
    text-align: center;

    // 用数据超市的高亮文字测定占位，页面切换和悬停时都使用这一宽度。
    &::before {
      display: block;
      height: 0;
      font-size: 16px;
      font-weight: 600;
      visibility: hidden;
      content: attr(data-label);
    }
  }

  :deep(.el-menu-item),
  :deep(.el-sub-menu__title) {
    padding: 0 var(--top-menu-padding);
    font-size: var(--top-menu-font-size);
    font-weight: var(--top-menu-weight);
    color: var(--top-menu-text);
    border-bottom: none;

    &:hover {
      font-weight: var(--top-menu-highlight-weight);
      color: var(--top-menu-active-text);
      background-color: transparent;
    }

    &.is-active {
      font-weight: var(--top-menu-highlight-weight);
      color: var(--top-menu-active-text) !important;
      background-color: var(--top-menu-active-background);
      border-bottom: none;
    }
  }
}

.top-menu:not(.top-menu--dark) :deep(.el-menu-item.is-active)::after {
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

.top-menu--dark {
  --top-menu-font-size: var(--el-menu-item-font-size, 14px);
  --top-menu-weight: 700;
  --top-menu-highlight-weight: 700;
  --top-menu-text: #ffffffa6;
  --top-menu-active-text: #fff;
  --top-menu-active-background: var(--el-color-primary);
}
</style>
