<template>
  <div class="layout" :class="layoutClass">
    <!-- 布局内容插槽 -->
    <slot></slot>

    <!-- 返回顶部按钮 -->
    <el-backtop target=".app-main">
      <div class="i-svg:backtop w-6 h-6" />
    </el-backtop>

    <global-nav v-if="globalNavReady" />
    <el-alert
      v-if="shellLoadFailed"
      class="layout-shell-error"
      title="导航组件加载失败"
      type="warning"
      :closable="false"
      show-icon
    >
      <el-button link type="primary" @click="loadShell(true)">重新加载导航</el-button>
    </el-alert>

    <!-- 全局共享的登记弹框:资产登记、申请单登记等 -->
    <!-- 全局共享的登记弹框:资产登记、申请单登记等 -->
    <RegisterModal
      v-model="open"
      :register-class="registerClass"
      :register-data="registerData"
      :register-mode="registerMode"
      :update-form-rule="updateFormRule"
      @completed="completeRegisterModal"
      @close="closeRegisterModal"
    />

    <!-- 详情全屏弹窗：目录、表、系统、api等（栈式，支持嵌套打开） -->
    <detail-dialog
      v-for="item in dialogStack"
      :key="item.id"
      v-model="item.visible"
      :detail-data="item.detailData"
      @close="closeDetailDialog(item.id)"
    />

    <!-- 业务事项详情抽屉 -->
    <process-detail-drawer
      v-if="processDrawerData"
      :process-name="processDrawerData.name"
      :process-en="processDrawerData.enName"
      :process-description="processDrawerData.description"
      @close="closeProcessDrawer"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, getCurrentInstance, onScopeDispose, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { useLayout } from "../composables/useLayout";
import RegisterModal from "@/components/business/register-modal.vue";
import { provideRegisterModal } from "@/composables/useRegisterModal";
import { provideDetailDialog } from "@/composables/useDetailDialog";
import { provideProcessDrawer } from "@/composables/useProcessDrawer";
import { ensureRuntimeSfc, hasRuntimeSfc } from "@/plugins/compiler/runtime-sfc";
import { useSettingStore } from "@/store";
import { AuthStorage } from "@/utils/auth";
// 布局相关
const { layoutClass } = useLayout();
const route = useRoute();
const app = getCurrentInstance()?.appContext.app;
const settings = useSettingStore();
const shellRoot = computed(() => String(route.meta.runtimeSfcRoot || "app-shell"));
const globalNavReady = computed(() => hasRuntimeSfc(app, "global-nav"));
const shellLoadFailed = ref(false);
let shellLoadGeneration = 0;

async function loadShell(retry = false) {
  const generation = ++shellLoadGeneration;
  const root = shellRoot.value;
  shellLoadFailed.value = false;
  if (!app || (settings.needLogin && !AuthStorage.getAccessToken())) return;

  try {
    // 动态路由与布局请求相同入口。布局自身的静态入口只需导航组件。
    await ensureRuntimeSfc(app, root, { retry });
  } catch {
    // 动态页的加载失败由页内状态提供重试，避免同时弹出两个入口。
    if (generation === shellLoadGeneration && root === "app-shell") {
      shellLoadFailed.value = true;
    }
  }
}

watch([shellRoot, AuthStorage.getSessionRevision], () => void loadShell(), {
  immediate: true,
});
onScopeDispose(() => shellLoadGeneration++);

const {
  open,
  registerData,
  registerClass,
  registerMode,
  updateFormRule,
  closeRegisterModal,
  completeRegisterModal,
} = provideRegisterModal();

const { dialogStack, closeDetailDialog } = provideDetailDialog();

const { processDrawerData, closeProcessDrawer } = provideProcessDrawer();
</script>

<style lang="scss" scoped>
.layout {
  width: 100%;
  height: 100%;
}

.layout-shell-error {
  position: fixed;
  inset-block-start: calc(var(--navbar-height) + 12px);
  inset-inline-end: 16px;
  z-index: 20;
  width: min(320px, calc(100vw - 32px));
}
</style>
