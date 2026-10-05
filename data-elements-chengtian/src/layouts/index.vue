<template>
  <div class="layout-wrapper">
    <component :is="currentLayoutComponent" />

    <!-- 设置面板 - 独立于布局组件 -->
    <Settings v-if="isShowSettings" />
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import MixLayout from "./views/MixLayout.vue";
import Settings from "./components/Settings/index.vue";
import { defaultSettings } from "@/settings";

// 根据当前布局模式选择对应的组件
const currentLayoutComponent = computed(() => {
  return MixLayout;
});

// 是否显示设置面板
const isShowSettings = computed(() => defaultSettings.showSettings);
</script>

<style lang="scss" scoped>
.layout-wrapper {
  width: 100%;
  height: 100%;
}
</style>
