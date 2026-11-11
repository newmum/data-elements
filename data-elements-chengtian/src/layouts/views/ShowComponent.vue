<template>
  <component :is="pageComponent" v-if="pageComponent" />
  <section v-else-if="loadError" class="runtime-page-state" role="alert">
    <p class="runtime-page-state__title">页面加载失败</p>
    <p class="runtime-page-state__message">{{ loadError }}</p>
    <el-button type="primary" plain @click="loadPage(true)">重试</el-button>
  </section>
  <WorkspaceLoading v-else />
</template>

<script setup lang="ts">
import { getCurrentInstance, onBeforeUnmount, ref, shallowRef, watch, type Component } from "vue";
import { ensureRuntimeSfc } from "@/plugins/compiler/runtime-sfc";
import { useSettingStore } from "@/store";
import { AuthStorage } from "@/utils/auth";
import WorkspaceLoading from "@/layouts/components/WorkspaceLoading.vue";

const props = defineProps({
  name: {
    type: String,
    default: "",
  },
});

const app = getCurrentInstance()?.appContext.app;
const settings = useSettingStore();
const pageComponent = shallowRef<Component | null>(null);
const loadError = ref("");
let requestSequence = 0;

async function loadPage(retry = false) {
  const sequence = ++requestSequence;
  pageComponent.value = null;
  loadError.value = "";
  if (settings.needLogin && !AuthStorage.getAccessToken()) return;

  try {
    const component = await ensureRuntimeSfc(app, props.name, { retry });
    if (sequence === requestSequence) pageComponent.value = component;
  } catch (error) {
    if (sequence === requestSequence) {
      loadError.value =
        error instanceof Error && error.message ? error.message : "暂时无法加载页面，请稍后重试";
    }
  }
}

watch([() => props.name, AuthStorage.getSessionRevision], () => void loadPage(), {
  immediate: true,
});
onBeforeUnmount(() => requestSequence++);
</script>

<style scoped>
.runtime-page-state {
  max-width: 640px;
  margin: 24px auto;
  padding: 0 20px;
  color: var(--el-text-color-regular);
}

.runtime-page-state__title {
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.runtime-page-state__message {
  margin: 8px 0 16px;
  overflow-wrap: anywhere;
}
</style>
