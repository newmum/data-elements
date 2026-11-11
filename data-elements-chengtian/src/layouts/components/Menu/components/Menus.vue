<template>
  <!-- 添加点击事件，打开全局导航时，确保点击关闭 -->
  <el-menu
    ref="menuRef"
    class="aside-menu"
    :default-active="activeMenu"
    v-bind="$attrs"
    :ellipsis-icon="MoreFilled"
    @select="retainCommittedMenu"
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
import { ref } from "vue";
import type { MenuInstance } from "element-plus";
import MenuItem from "./MenuItem.vue";
import { MoreFilled } from "@element-plus/icons-vue";
import { useSettingStore } from "@/store";

const props = defineProps<{
  menus: any;
  activeMenu: string | undefined;
  showIcon?: boolean;
  iconSize?: number | string;
  collapsed?: boolean;
}>();

const menuRef = ref<MenuInstance>();

function retainCommittedMenu() {
  // el-menu 会立即选中点击项。RouterLink 确认路由后由 default-active 更新高亮。
  if (props.activeMenu) menuRef.value?.updateActiveIndex(props.activeMenu);
}
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
