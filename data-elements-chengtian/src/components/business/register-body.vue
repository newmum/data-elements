<template>
  <component :is="currentComponent" ref="comRef" v-bind="currentProps" />
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref } from "vue";
import { useRegisterStore } from "@/store";
import { storeToRefs } from "pinia";
import { createRegistrationActionRunner } from "./registration-actions";

const emit = defineEmits<{
  (e: "finishTriggerFn", type: string | null): void;
}>();

const store = useRegisterStore();
const { currentComponent, currentProps } = storeToRefs(store);
const comRef = ref<Record<string, unknown>>();
const { handleAction, dispose } = createRegistrationActionRunner({
  store,
  getStep: () => comRef.value,
  nextTick,
  delay: (milliseconds) => new Promise((resolve) => window.setTimeout(resolve, milliseconds)),
  emit: (outcome) => emit("finishTriggerFn", outcome),
  warning: (message) => $message.warning(message),
  success: (message) => $message.success(message),
  reportError: (action, message) => console.error(`执行${action}事件异常, 原因:`, message),
  showDatasourceSuccess: true,
});
onBeforeUnmount(dispose);
defineExpose({ handleAction });
</script>
