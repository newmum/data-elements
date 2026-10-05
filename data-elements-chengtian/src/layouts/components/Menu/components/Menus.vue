<template>
  <!-- 添加点击事件，打开全局导航时，确保点击关闭 -->
  <el-menu
    class="aside-menu"
    :default-active="activeMenu"
    v-bind="$attrs"
    :ellipsis-icon="MoreFilled"
    @click="useSettingStore().navVisible = false"
  >
    <MenuItem
      v-for="menu in menus"
      :key="menu.path"
      :menu="menu"
      :icon-size="iconSize"
      :show-icon="showIcon"
      :collapsed="collapsed"
    />
  </el-menu>
</template>

<script setup lang="ts">
import MenuItem from "./MenuItem.vue";
import { MoreFilled } from "@element-plus/icons-vue";
import { useSettingStore } from "@/store";

defineProps<{
  menus: any;
  activeMenu: string | undefined;
  showIcon?: boolean;
  iconSize?: number | string;
  collapsed?: boolean;
}>();
</script>

<style lang="scss" scoped>
.el-menu {
  width: 100%;
  height: 100%;

  &--horizontal {
    height: $navbar-height !important;

    // 确保菜单项垂直居中
    :deep(.el-menu-item) {
      height: 100%;
      line-height: $navbar-height;
    }

    // 移除默认的底部边框
    &:after {
      display: none;
    }
  }
}
</style>
