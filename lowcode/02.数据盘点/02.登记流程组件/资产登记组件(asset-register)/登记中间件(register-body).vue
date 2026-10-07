<template>
  <el-skeleton active :loading="loading">
    <component :is="currentComponent" ref="comRef" v-bind="currentProps" />
  </el-skeleton>
</template>

<script setup lang="ts">
import { nextTick, ref } from "vue";
import { useRegisterStore } from "@/store";
import { storeToRefs } from "pinia";
import { set } from "lodash-es";

const emit = defineEmits<{
  (e: "finishTriggerFn", type: string | null): void;
}>();

const store = useRegisterStore();
const { currentComponent, currentProps } = storeToRefs(store);
const comRef = ref();
const loading = ref(false);
const actionPending = ref(false);

const waitForCurrentComponent = async (actionName: string) => {
  for (let index = 0; index < 10; index += 1) {
    await nextTick();
    if (comRef.value) return comRef.value;
    await new Promise((resolve) => window.setTimeout(resolve, 30));
  }
  throw new Error(`当前步骤页面仍在加载，请稍后再${actionName}`);
};

const callCurrentStepMethod = async (fnName: string, actionName: string) => {
  const component = await waitForCurrentComponent(actionName);
  if (!(component?.[fnName] instanceof Function)) {
    throw new Error(`当前步骤暂不支持${actionName}`);
  }
  return component[fnName]();
};

const customApi = async (
  fnName: string,
  callback: () => Promise<string | void>
): Promise<void> => {
  if (actionPending.value) return;
  actionPending.value = true;
  try {
    store.setLoadStatus(fnName, true);
    const finishType = await callback();
    emit("finishTriggerFn", finishType || fnName);
  } catch (err: any) {
    console.error(`执行${fnName}事件异常, 原因:`, err instanceof Error ? err.message : String(err));
    emit("finishTriggerFn", null);
    $message.warning(err?.message || err);
  } finally {
    store.setLoadStatus(fnName, false);
    await nextTick();
    window.setTimeout(() => {
      actionPending.value = false;
    }, 120);
  }
};

const saveApi = (): Promise<void> =>
  customApi("save", async () => {
    const result: {
      data?: Record<string, any>;
      success: boolean;
      refresh?: () => void;
      msg?: string;
    } = await callCurrentStepMethod("save", "保存");
    if (!result) return;
    if (result.success === false) {
      throw new Error(result?.msg || "保存失败");
    }
    if (result.refresh && typeof result.refresh === "function") {
      nextTick(() => result.refresh!());
    }
    $message.success(result.msg || "保存成功");
  });

const nextApi = (): Promise<void> =>
  customApi("next", async () => {
    await callCurrentStepMethod("next", "进入下一步");
  });

const finishApi = (): Promise<void> =>
  customApi("finish", async () => {
    const data = await callCurrentStepMethod("finish", "完成登记");
    if (data && !["db", "dbTable"].includes(store.registerClass)) {
      set(store.data, "finish", data);
    }
    if (comRef.value?.commit instanceof Function) {
      await comRef.value.commit();
    }
    if (["db", "dbTable"].includes(store.registerClass)) {
      return "completed";
    }
    store.setCurrentStep(store.state.steps.length);
    return "finish";
  });

const commonApi = (fnName: string): Promise<void> =>
  customApi(fnName, async () => {
    await callCurrentStepMethod(fnName, fnName);
  });

const handleAction = async (type: string): Promise<void> => {
  switch (type) {
    case "save":
      await saveApi();
      break;
    case "next":
      await nextApi();
      break;
    case "finish":
      await finishApi();
      break;
    default:
      await commonApi(type);
      break;
  }
};

defineExpose({ handleAction });
</script>

<style scoped lang="scss"></style>
