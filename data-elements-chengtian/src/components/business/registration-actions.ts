interface RegistrationActionState {
  currentStep: number;
  steps: readonly unknown[];
  loadStatus: Record<string, unknown>;
  registerMode?: string;
}

interface RegistrationActionRunnerOptions {
  store: {
    state: RegistrationActionState;
    registerClass?: string;
    data: Record<string, unknown>;
    setCurrentStep: (step: number) => void;
  };
  getStep: () => Record<string, unknown> | undefined;
  nextTick: () => Promise<unknown>;
  delay: (milliseconds: number) => Promise<unknown>;
  emit: (outcome: string | null) => void;
  warning: (message: string) => void;
  success: (message: string) => void;
  reportError: (action: string, message: string) => void;
  showDatasourceSuccess?: boolean;
}

interface RegistrationActionScope {
  action: string;
  state: RegistrationActionState;
  steps: RegistrationActionState["steps"];
  status: RegistrationActionState["loadStatus"];
  index: number;
}

const registrationActionLabels = new Map([
  ["save", "保存"],
  ["next", "进入下一步"],
  ["finish", "完成登记"],
]);

function isRegistrationActionRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function registrationActionMessage(error: unknown) {
  if (isRegistrationActionRecord(error) && typeof error.message === "string" && error.message) {
    return error.message;
  }
  return typeof error === "string" && error ? error : "操作未完成，请重试";
}

function invokeRegistrationStep(
  step: Record<string, unknown>,
  method: string,
  label: string,
  args: unknown[] = []
): unknown {
  const handler = step[method];
  if (typeof handler !== "function") throw new Error(`当前步骤暂不支持${label}`);
  return handler.apply(step, args);
}

export function createRegistrationActionRunner({
  store,
  getStep,
  nextTick,
  delay,
  emit,
  warning,
  success,
  reportError,
  showDatasourceSuccess,
}: RegistrationActionRunnerOptions) {
  let active: RegistrationActionScope | undefined;
  let disposed = false;

  function sameFlow(scope: RegistrationActionScope) {
    const { state } = store;
    return (
      state === scope.state && state.steps === scope.steps && state.loadStatus === scope.status
    );
  }

  function clearLoading(scope: RegistrationActionScope) {
    if (active === scope && store.state.loadStatus === scope.status)
      scope.status[scope.action] = false;
  }

  async function handleAction(action: string) {
    if (disposed || (active && sameFlow(active))) return;

    const { state, registerClass } = store;
    const scope = {
      action,
      state,
      steps: state.steps,
      status: state.loadStatus,
      index: state.currentStep,
    };
    active = scope;
    scope.status[action] = true;

    let step: ReturnType<typeof getStep>;
    const isCurrent = () =>
      !disposed &&
      active === scope &&
      sameFlow(scope) &&
      state.currentStep === scope.index &&
      (!step || getStep() === step);

    try {
      const label = registrationActionLabels.get(action) ?? action;
      for (let attempt = 0; attempt < 10; attempt++) {
        await nextTick();
        if (!isCurrent()) return;
        step = getStep();
        if (step) break;
        await delay(30);
      }
      if (!isCurrent()) return;
      if (!step) throw new Error(`当前步骤页面仍在加载，请稍后再${label}`);

      let outcome: string | null = action;
      if (action === "finish") {
        const datasource = registerClass === "db" || registerClass === "dbTable";
        const quickEdit =
          registerClass === "db" &&
          state.registerMode === "explore-edit" &&
          scope.steps.length === 1;
        if (quickEdit) {
          await invokeRegistrationStep(step, "commit", "保存");
        } else {
          const data = await invokeRegistrationStep(step, "finish", label);
          // 审查结果只交给发起动作的步骤，关闭或跳步后不再发出提交。
          if (!isCurrent()) return;
          if (data && !datasource) store.data.finish = data;
          if (typeof step.commit === "function")
            await invokeRegistrationStep(step, "commit", label, [data]);
        }
        if (!isCurrent()) return;
        if (datasource) {
          if (showDatasourceSuccess && !quickEdit) success("登记完成，数据源状态已更新");
          outcome = "completed";
        } else {
          store.setCurrentStep(scope.steps.length);
        }
      } else {
        const value = await invokeRegistrationStep(step, action, label);
        if (!isCurrent()) return;
        const result = isRegistrationActionRecord(value) ? value : {};
        if (action === "next" && result.navigationHandled === true) outcome = null;
        if (action === "commit" && result.completed === true) {
          if (typeof result.msg === "string" && result.msg) success(result.msg);
          outcome = "completed";
        }
        if (action === "save" && value) {
          const message = typeof result.msg === "string" && result.msg ? result.msg : undefined;
          if (result.success === false) throw new Error(message ?? "保存失败");
          const refresh = result.refresh;
          if (typeof refresh === "function") {
            void nextTick()
              .then(() => {
                if (isCurrent()) return refresh.call(result);
              })
              .catch((error: unknown) => {
                if (!disposed && sameFlow(scope))
                  reportError("refresh", registrationActionMessage(error));
              });
          }
          success(message ?? "保存成功");
        }
      }
      emit(outcome);
    } catch (error: unknown) {
      if (!isCurrent()) return;
      const message = registrationActionMessage(error);
      reportError(action, message);
      emit(null);
      // 请求拦截器已展示过的接口错误，登记宿主只负责恢复按钮状态。
      if (!isRegistrationActionRecord(error) || !error.handled) warning(message);
    } finally {
      clearLoading(scope);
      // 让步骤更新先完成，再接受同一宿主的下一次动作。
      await nextTick();
      if (active === scope) active = undefined;
    }
  }

  return {
    handleAction,
    dispose() {
      if (active) clearLoading(active);
      active = undefined;
      disposed = true;
    },
  };
}
