<template>
  <div class="layout" :class="layoutClass">
    <!-- 布局内容插槽 -->
    <slot></slot>

    <!-- 返回顶部按钮 -->
    <el-backtop target=".app-main">
      <div class="i-svg:backtop w-6 h-6" />
    </el-backtop>

    <!-- 动态组件 -->
    <global-nav />

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
import { useLayout } from "../composables/useLayout";
import RegisterModal from "@/components/business/register-modal.vue";
import { provideRegisterModal } from "@/composables/useRegisterModal";
import { provideDetailDialog } from "@/composables/useDetailDialog";
import { provideProcessDrawer } from "@/composables/useProcessDrawer";
// 布局相关
const { layoutClass } = useLayout();

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
</style>
