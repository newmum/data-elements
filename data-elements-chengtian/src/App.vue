<template>
  <el-config-provider v-bind="config">
    <!-- 开启水印 -->
    <el-watermark
      :font="{ color: 'rgba(0, 0, 0, .15)' }"
      :content="showWatermark ? watermarkContent : ''"
      :z-index="9999"
      class="wh-full"
    >
      <router-view v-slot="{ Component }">
        <component :is="Component" v-if="Component" />
        <div v-else-if="startupFailed" class="workspace-loading">
          <div class="workspace-loading__panel workspace-loading__panel--error" role="alert">
            <span class="workspace-loading__label">页面加载失败</span>
            <el-button type="primary" plain @click="reloadPage">重新加载</el-button>
          </div>
        </div>
        <WorkspaceLoading v-else />
      </router-view>
    </el-watermark>
  </el-config-provider>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref } from "vue";
import { START_LOCATION, useRouter } from "vue-router";
import { useSettingStore } from "@/store";
import zhCn from "element-plus/es/locale/lang/zh-cn";
import WorkspaceLoading from "@/layouts/components/WorkspaceLoading.vue";

const settingsStore = useSettingStore();
const router = useRouter();
const startupFailed = ref(false);

// 入口路由的异步资源失败时，不能一直停留在启动动画。
const removeStartupError = router.onError((_error, _to, from) => {
  if (from === START_LOCATION) startupFailed.value = true;
});
onBeforeUnmount(removeStartupError);

function reloadPage() {
  window.location.reload();
}

const showWatermark = computed(() => settingsStore.showWatermark);
const watermarkContent = computed(() => settingsStore.watermarkContent);

const config = reactive({
  locale: zhCn,
  dialog: {
    alignCenter: false,
    draggable: true,
    overflow: true,
  },
  link: {
    type: "primary",
    underline: "never",
  },
  message: {
    plain: true,
    grouping: true,
  },
});
</script>
