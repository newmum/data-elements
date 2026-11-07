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
export function createRegistrationFlow(
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
