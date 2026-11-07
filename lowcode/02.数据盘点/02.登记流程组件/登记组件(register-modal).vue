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

    <asset-register

      v-if="flow"

      class="register-content"

      :init-store="flow.initStore"

      :actions="flow.actions"

      @completed="onCompleted"

      @cancel="open = false;"

    />

  </el-dialog>

</template>



<script setup lang="ts">
import { computed, ref, shallowRef, watch } from "vue";
import { useRegisterStore } from "@/store";

// BEGIN GENERATED REGISTRATION FLOW
// 源文件 registration-flow.ts。修改后运行 node tools/sync-registration-flow.mjs。
interface RegistrationFlowOptions {
  registerClass: string;
  registerMode?: string;
  registerData?: { currentStep?: number | null };
  currentStep?: number;
  updateFormRule?: unknown[];
}

interface RegistrationStep {
  title: string;
  component: string;
  props?: object;
  hidden?: boolean;
  skipped?: boolean;
}

interface RegistrationActionContext {
  state: { currentStep: number; steps?: RegistrationStep[] };
  isFirstStep: boolean;
  isSecondLastStep: boolean;
}

type RegistrationActionCode = "cancel" | "pre" | "save" | "next" | "finish" | "commit" | "print";
type RegistrationActionVisibility = (context: RegistrationActionContext) => boolean;

interface RegistrationAction {
  code: RegistrationActionCode;
  name: string;
  icon?: string;
  class?: string;
  props: { type: "default" | "primary" | "dashed"; plain?: boolean };
  if?: RegistrationActionVisibility;
}

const registrationActionDefaults: Record<
  RegistrationActionCode,
  Omit<RegistrationAction, "code">
> = {
  cancel: { name: "取消", props: { type: "default" } },
  pre: { name: "上一步", icon: "rollback", class: "btn-pre", props: { type: "default" } },
  save: { name: "保存", icon: "save", props: { type: "primary", plain: true } },
  next: { name: "下一步", icon: "right-circle", props: { type: "primary" } },
  finish: { name: "完成登记", icon: "check-circle", props: { type: "primary" } },
  commit: { name: "提交申请", icon: "check-circle", props: { type: "primary" } },
  print: { name: "打印", props: { type: "dashed" } },
};

function registrationAction(
  code: RegistrationActionCode,
  visible?: RegistrationActionVisibility,
  label?: string
): RegistrationAction {
  const defaults = registrationActionDefaults[code];
  const action: RegistrationAction = {
    ...defaults,
    code,
    name: label ?? defaults.name,
    props: { ...defaults.props },
  };
  if (visible) action.if = visible;
  return action;
}

function registrationSteps({ registerClass }: RegistrationFlowOptions): {
  title: string;
  steps: RegistrationStep[];
} {
  switch (registerClass) {
    case "db":
    case "dbTable":
      return {
        title: "登记数据资源",
        steps: [
          { title: "登记数据源", component: "register-db" },
          { title: "探查数据表", component: "register-dbTable" },
          {
            title: "登记字典表",
            component: "register-dbTable-catalog",
            props: { catalogPhase: "dictionary" },
          },
          {
            title: "登记业务/日志表",
            component: "register-dbTable-catalog",
            props: { catalogPhase: "business" },
          },
          { title: "登记内容审查", component: "register-confirm" },
          {
            title: "完成",
            component: "register-finish",
            props: { finishType: "datasource" },
            hidden: true,
          },
        ],
      };
    case "app":
      return {
        title: "",
        steps: [
          { title: "业务系统", component: "register-app" },
          { title: "完成", component: "register-finish", props: { finishType: "application" } },
        ],
      };
    case "catalog":
      return {
        title: "登记数据目录",
        steps: [
          { title: "目录登记", component: "register-catalog" },
          { title: "完成", component: "register-finish", props: { finishType: "catalog" } },
        ],
      };
    case "fileCatalog":
      return {
        title: "",
        steps: [
          { title: "数据目录登记", component: "register-fileCatalog" },
          { title: "导入数据文件", component: "register-fileCatalog-data" },
          { title: "生成数据目录", component: "register-finish", props: { finishType: "catalog" } },
        ],
      };
    case "apply":
    case "car-apply":
      return {
        title: "订阅申请单",
        steps:
          registerClass === "car-apply"
            ? [
                { title: "选择申请资源", component: "car-list" },
                { title: "填写申请单", component: "car-register-apply" },
              ]
            : [{ title: "登记", component: "car-register-apply" }],
      };
    default:
      return { title: "", steps: [] };
  }
}

const registrationNotFirst: RegistrationActionVisibility = ({ isFirstStep }) => !isFirstStep;
const registrationBeforeCommit: RegistrationActionVisibility = ({ isSecondLastStep }) =>
  !isSecondLastStep;
const registrationCommitStep: RegistrationActionVisibility = ({ isSecondLastStep }) =>
  isSecondLastStep;
const registrationApplyFormStep: RegistrationActionVisibility = ({ state }) =>
  state.steps?.[state.currentStep]?.component === "car-register-apply";
const registrationOnFinishStep = ({ steps, currentStep }: RegistrationActionContext["state"]) =>
  steps?.[currentStep]?.component === "register-finish";

function registrationActions(registerClass: string, datasourceFinishLabel: string) {
  switch (registerClass) {
    case "app":
      return [
        registrationAction(
          "pre",
          (context) => registrationNotFirst(context) && !registrationOnFinishStep(context.state)
        ),
        registrationAction("save", ({ state }) => state.currentStep !== 2),
        registrationAction("next", registrationBeforeCommit),
        registrationAction("finish", registrationCommitStep),
      ];
    case "apply":
      return [
        registrationAction("cancel"),
        registrationAction("save", registrationApplyFormStep),
        registrationAction("commit", registrationApplyFormStep),
      ];
    case "car-apply":
      return [
        registrationAction("cancel"),
        registrationAction("pre", registrationApplyFormStep),
        registrationAction("print", registrationApplyFormStep),
        registrationAction("save", registrationApplyFormStep),
        registrationAction(
          "next",
          ({ state }) => state.steps?.[state.currentStep]?.component === "car-list"
        ),
        registrationAction("commit", registrationApplyFormStep),
      ];
    case "db":
    case "dbTable":
      return [
        registrationAction("pre", registrationNotFirst),
        registrationAction(
          "save",
          ({ state }) => [0, 2, 3].includes(state.currentStep) && !registrationOnFinishStep(state)
        ),
        registrationAction(
          "next",
          ({ state }) => state.currentStep !== 4 && !registrationOnFinishStep(state)
        ),
        registrationAction("finish", ({ state }) => state.currentStep === 4, datasourceFinishLabel),
      ];
    default:
      // 目录登记沿用登记布局自己的默认按钮。
      return undefined;
  }
}

/** 每次打开生成独立步骤，步骤组件可为当前登记标记跳过状态。 */
function createRegistrationFlow(
  options: RegistrationFlowOptions,
  host: "native" | "lowcode"
) {
  const { registerClass, registerMode = "", registerData, currentStep, updateFormRule } = options;
  const editingDatasource = registerClass === "db" && registerMode === "explore-edit";
  const quickEdit = editingDatasource && host === "lowcode";
  const { title, steps } = quickEdit
    ? { title: "编辑数据源", steps: [{ title: "编辑数据源", component: "register-db" }] }
    : registrationSteps(options);
  return {
    initStore: {
      registerClass,
      title: editingDatasource ? "编辑数据源" : title,
      assets: [{ assetClass: registerClass }],
      steps,
      currentStep: registerData?.currentStep ?? currentStep ?? 0,
      loadStatus: {},
      updateFormRule: updateFormRule ?? [],
      registerMode,
      hiddenStep: quickEdit || registerClass === "apply" || registerClass === "car-apply",
    },
    actions: quickEdit
      ? [registrationAction("cancel"), registrationAction("finish", undefined, "保存并完成")]
      : registrationActions(registerClass, host === "native" ? "结束登记" : "完成登记"),
  };
}
// END GENERATED REGISTRATION FLOW

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

watch(open, (value) => {
  if (!value) {
    // 对话框退场时还会保留插槽，立即卸载登记步骤以结束待提交的动作。
    flow.value = undefined;
    return;
  }
  if (!props.registerClass) return;
  store.data = props.registerData;

  completed.value = false;
  const registration = createRegistrationFlow(props, "lowcode");
  store.state.hiddenStep = registration.initStore.hiddenStep;
  flow.value = registration;
}, { immediate: true });
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
