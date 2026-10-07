<template>
  <div>
    <div v-if="!isLastStep" class="action-btns">
      <template v-for="btn in actions" :key="btn.code">
        <span :class="`action-btn-${btn.code}-left`"></span>
        <el-button
          v-if="btn.if ? btn.if(store) : true"
          v-bind="btn.props as any"
          class="action-btn"
          :loading="Boolean(loadStatus[btn.code])"
          :disabled="isActionLocked || Boolean(btn.props?.disabled)"
          @click="action(btn.code as string)"
        >
          <template v-if="btn.icon" #icon>
            <icon :icon="btn.icon" />
          </template>
          {{ btn.name }}
        </el-button>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRegisterStore } from "@/store";
import { storeToRefs } from "pinia";

const emit = defineEmits<{
  (event: "action", type: string): void;
}>();
const store = useRegisterStore();
const { isSecondLastStep, isLastStep, loadStatus } = storeToRefs(store);
const isActionBusy = computed(() => Object.values(loadStatus.value || {}).some(Boolean));
const clickLocked = ref(false);
const isActionLocked = computed(() => clickLocked.value || isActionBusy.value);

withDefaults(
  defineProps<{
    actions?: any[];
  }>(),
  {
    actions: () => [
      { code: "cancel", name: "取消", props: { type: "default" } },
      {
        code: "pre",
        name: "上一步",
        icon: "rollback",
        class: "btn-pre",
        props: { type: "default" },
        if: ({ isFirstStep }) => !isFirstStep,
      },
      {
        code: "save",
        name: "保存",
        icon: "save",
        props: { type: "primary", plain: true },
        if: ({ state }) => state.currentStep === 0,
      },
      {
        code: "next",
        name: "下一步",
        icon: "right-circle",
        props: { type: "primary" },
        if: ({ isSecondLastStep }) => !isSecondLastStep,
      },
      {
        code: "finish",
        name: "完成登记",
        icon: "check-circle",
        props: { type: "primary" },
        if: ({ isSecondLastStep }) => isSecondLastStep,
      },
    ],
  }
);

watch(isActionBusy, (busy) => {
  if (!busy) clickLocked.value = false;
});

const action = (type: string) => {
  if (isActionLocked.value) return;
  clickLocked.value = true;
  emit("action", type);
  window.setTimeout(() => {
    if (!isActionBusy.value) clickLocked.value = false;
  }, 300);
};
defineExpose({ action });
</script>

<style scoped lang="scss">
.btn-pre {
  color: rgb(145, 181, 252);
  border: 1px solid rgb(145, 181, 252);
}

.action-btns .action-btn:nth-of-type(n + 2) {
  margin-left: 12px;
}
</style>
