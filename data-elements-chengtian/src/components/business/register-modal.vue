<template>
  <el-dialog
    v-model="open"
    destroy-on-close
    fullscreen
    class="register-modal__container"
    header-class="p-0!"
    body-class="px-20"
    @close="onCancel"
  >
    <button
      class="register-modal-back"
      type="button"
      title="返回"
      aria-label="返回并关闭登记弹框"
      @click="onCancel"
    >
      <Icon icon="el-icon-ArrowLeft" />
    </button>

    <!-- asset-register中点击取消会触发对话框的@close事件 -->

    <AssetRegister
      v-if="flow"
      class="register-content"
      :init-store="flow.initStore"
      :actions="flow.actions"
      @completed="onCompleted"
      @cancel="open = false"
    />

    <div v-else-if="stepLoadError" class="mx-auto mt-16 max-w-160" role="alert">
      <p class="mb-4">{{ stepLoadError }}</p>
      <el-button type="primary" plain @click="loadRegistration(true)">重试</el-button>
    </div>
    <loading-state v-else title="正在加载登记页面" compact />
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, getCurrentInstance, onScopeDispose, ref, shallowRef, watch } from "vue";
import { useRegisterStore } from "@/store";
import { hasRuntimeSfc, loadAllRuntimeSfc } from "@/plugins/compiler/runtime-sfc";
import AssetRegister from "./asset-register.vue";
import { createRegistrationFlow } from "./registration-flow";

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void;
  (e: "close"): void;
  (e: "completed"): void;
}>();

const props = defineProps({
  modelValue: Boolean,
  registerClass: { type: String, required: true },
  registerData: { type: Object, default: () => ({}) },
  currentStep: Number,
  registerMode: { type: String, default: "" },
  updateFormRule: { type: Array, default: () => [] },
});

const open = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit("update:modelValue", value),
});
const flow = shallowRef<ReturnType<typeof createRegistrationFlow>>();
const completed = ref(false);
const store = useRegisterStore();
store.$reset();

const onCancel = () => emit("close");
const onCompleted = () => {
  if (completed.value) return;
  completed.value = true;
  emit("completed");
  open.value = false;
};

const app = getCurrentInstance()?.appContext.app;
const stepLoadError = ref("");
let registrationRequest = 0;

const loadRegistration = async (retry = false) => {
  const request = ++registrationRequest;
  stepLoadError.value = "";
  flow.value = undefined;
  completed.value = false;

  try {
    const registration = createRegistrationFlow(props, "native");
    const { steps } = registration.initStore;
    if (!steps.length) throw new Error("当前登记类型未配置流程");
    // 隐藏的完成步骤也需要注册，避免登记成功后打开空白页。
    const hasSteps = () => steps.every((step) => hasRuntimeSfc(app, step.component));
    if (!hasSteps()) await loadAllRuntimeSfc(app, { force: retry });
    if (request !== registrationRequest || !open.value) return;
    if (!hasSteps()) throw new Error("登记步骤组件未完整加载，请重试");

    store.state.hiddenStep = registration.initStore.hiddenStep;
    flow.value = registration;
  } catch (error) {
    if (request === registrationRequest && open.value) {
      stepLoadError.value = error instanceof Error ? error.message : "登记页面加载失败，请重试";
    }
  }
};

watch(
  open,
  (value) => {
    if (value) {
      store.data = props.registerData;
      void loadRegistration();
    } else {
      registrationRequest++;
      // 对话框退场时还会保留插槽，立即卸载登记步骤以结束待提交的动作。
      flow.value = undefined;
    }
  },
  { immediate: true }
);
onScopeDispose(() => registrationRequest++);
</script>

<style lang="scss">
.register-modal__container {
  padding: 0;

  .register-modal-back {
    position: fixed;

    top: 17px;

    left: 14px;

    z-index: 3001;

    display: inline-flex;

    align-items: center;

    justify-content: center;

    width: 32px;

    height: 32px;

    border: 0;

    border-radius: 10px;

    background: #eaf3ff;

    color: #1677ff;

    cursor: pointer;

    font-size: 18px;

    box-shadow: 0 8px 18px rgba(22, 119, 255, 0.16);

    transition:
      background-color 0.16s ease,
      color 0.16s ease,
      transform 0.16s ease,
      box-shadow 0.16s ease;

    &:hover {
      background: #1677ff;

      color: #fff;

      transform: translateX(-1px);

      box-shadow: 0 10px 22px rgba(22, 119, 255, 0.26);
    }
  }

  .register-container__top {
    align-items: center;
  }

  .register-container__top .register-container__title {
    display: flex;

    align-items: center;

    min-height: 32px;

    padding-left: 50px;

    line-height: 32px;
  }

  .el-dialog__header {
    padding: 0;
  }

  .el-dialog__body {
    height: 100%;

    padding: 0;
  }
}
</style>
