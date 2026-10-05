<template>
  <Menus
    :class="open ? 'side-open' : 'side-collapse'"
    :active-menu="activeSidePath"
    :menus="sideMenus"
    :collapsed="!open"
    show-icon
    background-color="#fff"
    text-color="#212121"
    :icon-size="open ? '22px' : '30px'"
    v-bind="$attrs"
  />
</template>

<script setup lang="ts">
import Menus from "@/layouts/components/Menu/components/Menus.vue";
import { useLayoutMenu } from "@/layouts/composables/useLayoutMenu";

const props = defineProps({
  open: {
    type: Boolean,
    default: true,
  },
});
// 菜单相关
const { sideMenuRoutes, activeSidePath } = useLayoutMenu();

// 折叠时延迟更新菜单结构，避免 DOM 重排打断宽度过渡动画
const isOpenVisually = ref(props.open);
let collapseTimer: ReturnType<typeof setTimeout> | null = null;

watch(
  () => props.open,
  (newVal) => {
    if (newVal) {
      // 展开：立即恢复完整菜单结构
      if (collapseTimer) {
        clearTimeout(collapseTimer);
        collapseTimer = null;
      }
      isOpenVisually.value = true;
    } else {
      // 折叠：等宽度过渡结束后再去掉子菜单（与 transition 时长对齐）
      collapseTimer = setTimeout(() => {
        isOpenVisually.value = false;
        collapseTimer = null;
      }, 350);
    }
  }
);

onBeforeUnmount(() => {
  if (collapseTimer) clearTimeout(collapseTimer);
});

const sideMenus = computed(() => {
  return isOpenVisually.value
    ? sideMenuRoutes.value
    : sideMenuRoutes.value.map((el) => ({ ...el, children: undefined }));
});
</script>

<style lang="scss" scoped>
.el-menu {
  height: 100%;
  border: none;
  --menu-active-text: var(--el-color-primary);
}

.side-collapse {
  :deep(.el-menu-item) {
    --el-menu-hover-bg-color: var(--el-fill-color-light);
    box-sizing: border-box;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    width: 60px;
    height: 60px;
    margin: 10px 10px 0;

    font-size: var(--el-font-size-base);
    line-height: 24px;
    text-align: center;
    border-radius: 8px;
    transition:
      width 0.35s cubic-bezier(0.4, 0, 0.2, 1),
      height 0.35s cubic-bezier(0.4, 0, 0.2, 1),
      background-color 0.2s ease,
      color 0.2s ease;

    &.is-active {
      color: var(--menu-active-text) !important;
      background-color: #e8f4ff !important;
    }

    .menu-icon {
      width: 30px;
      height: 30px;
      margin-right: 0 !important;
      margin-bottom: 0px;
      font-size: 30px;
      transition:
        font-size 0.35s cubic-bezier(0.4, 0, 0.2, 1),
        width 0.35s,
        height 0.35s;
    }

    .menu-title {
      margin-left: 0;
      overflow: hidden;
      white-space: nowrap;
      opacity: 1;
      transition: opacity 0.2s ease;
    }
  }
}
.side-open {
  padding: 8px 8px 0;
  :deep(.el-menu-item),
  :deep(.el-sub-menu) {
    --el-menu-hover-bg-color: var(--el-fill-color-light);
    --el-menu-item-height: 40px;
    --el-menu-sub-item-height: 40px;
    --el-menu-base-level-padding: 12px;
    margin: 8px 0 0 0;

    font-family: "Source Han Sans SC", sans-serif;
    font-size: 14px;
    line-height: 24px;
    color: #323643;
    text-align: center;
    border-radius: 12px;
    transition:
      margin 0.35s cubic-bezier(0.4, 0, 0.2, 1),
      background-color 0.2s ease,
      color 0.2s ease;

    .el-sub-menu__title {
      font-size: 16px;
      border-radius: 8px;
    }

    .menu-icon {
      margin-right: 8px;
      transition:
        font-size 0.35s cubic-bezier(0.4, 0, 0.2, 1),
        margin 0.35s;
    }

    .menu-title {
      overflow: hidden;
      white-space: nowrap;
      opacity: 1;
      transition: opacity 0.25s ease 0.1s;
    }
  }
  :deep(.el-sub-menu).is-active > .el-sub-menu__title {
    color: var(--menu-active-text) !important;
  }

  :deep(.el-menu-item).is-active {
    position: relative;
    font-weight: 500;
    color: var(--menu-active-text);
    background-color: var(--el-color-primary-light-9);

    &::after {
      position: absolute;
      top: 50%;
      right: 10px;
      width: 6px;
      height: 6px;
      content: "";
      background-color: var(--menu-active-text);
      border-radius: 50%;
      transform: translateY(-50%);
    }
  }

  :deep(.el-sub-menu__icon-arrow) {
    font-size: 16px;
    color: #b9bbc3;
  }
}
</style>
