<template>
  <el-dialog
    :model-value="modelValue"
    destroy-on-close
    fullscreen
    class="api-modal__container"
    header-class="p-0!"
    @update:model-value="(val: boolean) => $emit('update:modelValue', val)"
    @close="$emit('close', false)"
  >
    <div class="flex flex-col h-full">
      <!-- Header -->
      <div class="api-modal__header">
        <div class="api-modal__title">{{ title }}</div>
        <slot name="header-extra" />
      </div>

      <!-- Body (编辑态) -->
      <template v-if="!isFinish">
        <div class="api-modal__body">
          <el-scrollbar>
            <div class="px-5 py-2 flex flex-col h-full">
              <slot />
            </div>
          </el-scrollbar>
        </div>
        <!-- Footer -->
        <div class="api-modal__footer">
          <slot name="footer" />
        </div>
      </template>

      <!-- Finish 态 -->
      <template v-else>
        <slot name="finish">
          <task-finish
            :name="finishName || ''"
            :time="finishTime || ''"
            :code="finishCode || ''"
            @to-see="$emit('to-see')"
          />
        </slot>
      </template>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
defineProps<{
  modelValue: boolean;
  title: string;
  isFinish: boolean;
  finishName?: string;
  finishTime?: string;
  finishCode?: string;
}>();

defineEmits<{
  (e: "update:modelValue", value: boolean): void;
  (e: "close", value: boolean): void;
  (e: "to-see"): void;
}>();
</script>

<style lang="scss">
/* 全屏弹窗共享样式 —— 两个 Modal 通用 */
.api-modal__container {
  padding: 0;

  .el-dialog__body {
    height: 100%;
    padding: 0;
  }

  .el-dialog__header {
    padding: 0;
  }

  .api-modal__header {
    display: grid;
    flex-shrink: 0;
    grid-template-columns: 15% 1fr 15%;
    align-items: center;
    height: 45px;
    padding: 0 15px 0 20px;
    background-color: #fff;
    border-bottom: 1px solid #ebeef5;
  }

  .api-modal__title {
    font-family: Arial, sans-serif;
    font-size: 16px;
    font-weight: 700;
    color: #000000e0;
  }

  .api-modal__footer {
    display: flex;
    justify-content: center;
    padding: 10px 24px;
    text-align: right;
    background-color: #fff;
    border-top: 1px solid #ebeef5;
  }

  .api-modal__body {
    height: calc(100% - 45px - 53px);
    grid-template-columns: 100%;
    overflow: hidden;

    .el-scrollbar {
      width: 100%;
      height: 100%;
    }
  }

  .api-status {
    position: relative;

    .vxe-grid--toolbar-wrapper {
      position: absolute;
      right: 0;
      top: -39px;
    }
  }

  .api-params-table {
    min-height: 200px;
    position: relative;

    .vxe-grid--toolbar-wrapper {
      position: absolute;
      right: 0;
      top: -39px;
    }
  }

  .header-checkbox {
    height: 40px;
    color: #587aa8;
  }
}
</style>
