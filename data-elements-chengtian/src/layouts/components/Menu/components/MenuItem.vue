<template>
  <!-- 新增：分组标题节点，不受 hidden 控制 -->
  <template v-if="menu.meta?.type === 'group'">
    <div v-if="!collapsed" class="menu-group-title">{{ menu.meta.title }}</div>
    <!-- 折叠时暂不显示 -->
    <!-- <div v-else class="menu-group-divider" /> -->
  </template>

  <!-- 原有：普通菜单节点，将 v-if 改为 v-else-if -->
  <template v-else-if="!menu.meta?.hidden">
    <template v-if="componentName === 'el-menu-item'">
      <AppLink :to="menu">
        <el-menu-item :index="menu.path">
          <icon
            v-if="showIcon"
            :icon="menu.meta?.icon || 'menu-cata'"
            :size="iconSize"
            class="menu-icon"
          />
          <span>{{ menu.meta?.title }}</span>
        </el-menu-item>
      </AppLink>
    </template>
    <template v-else>
      <el-sub-menu
        :index="menu.path as string"
        expand-close-icon="CaretTop"
        expand-open-icon="CaretBottom"
      >
        <template #title>
          <Icon
            v-if="showIcon"
            :icon="menu.meta?.icon || 'menu-cata'"
            :size="iconSize"
            class="menu-icon"
          />
          <span>{{ menu.meta?.title }}</span>
        </template>
        <MenuItem
          v-for="child in menu.children"
          :key="child.path"
          :menu="child"
          :collapsed="collapsed"
        />
      </el-sub-menu>
    </template>
  </template>
</template>

<script setup lang="ts">
import AppLink from "../../AppLink/index.vue";
import { RouteVO } from "@/api/system/menu-api";

const props = defineProps<{
  menu: RouteVO;
  iconSize?: string | number;
  showIcon?: boolean;
  collapsed?: boolean;
}>();

const hasChildren = computed(
  () => !!props.menu?.children?.length && props.menu?.children?.some((el) => !el.meta?.hidden)
);
const componentName = computed(() => {
  return hasChildren.value ? "el-sub-menu" : "el-menu-item";
});
</script>

<style lang="scss" scoped>
.menu-group-title {
  padding: 16px 16px 4px;
  font-size: 12px;
  color: #94a3b8;
  letter-spacing: 0.5px;
  pointer-events: none;
  user-select: none;
}

.menu-group-divider {
  height: 1px;
  margin: 8px 12px;
  pointer-events: none;
  background: var(--el-border-color-lighter);
}
</style>
