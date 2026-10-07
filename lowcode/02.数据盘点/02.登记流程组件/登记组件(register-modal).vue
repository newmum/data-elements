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

      v-if="loaded"

      class="register-content"

      :init-store="initData"

      :config="config"

      :actions="actions"

      @completed="onCompleted"

      @cancel="open = false;"

    />

  </el-dialog>

</template>



<script setup lang="ts">

import { ref, computed, watch } from "vue";

import { useRegisterStore } from "@/store";



const emit = defineEmits<{

  (e: "update:modelValue", value: boolean): void;

  (e: "close", value: boolean): void;

  (e: "completed"): void;

}>();



const props = defineProps({

  modelValue: Boolean,

  registerClass: {

    type: String,

    required: true,

  },

  registerData: {

    // 登记数据，直接应用于store-data中

    type: Object,

    default: () => ({}),

  },

  qualityData: {

    // 质检数据

    type: Object,

    default: () => ({}),

  },

  id: String,

  currentStep: Number, // 初始步骤

  // 从页面入口显式传入的流程模式。只有数据探查编辑会使用 explore-edit，
  // 其他数据源管理入口不传此值，始终保留现有五步登记。
  registerMode: {
    type: String,
    default: "",
  },

  updateFormRule: {
    type: Array,
    default: () => [],
  }, // 更新表单

});



const loaded = ref(false);

const actions = ref();

const store = useRegisterStore();



useRegisterStore().$reset();

loaded.value = true;



// 登记产配置

const config = {

  dbTable: {
    title: "登记数据资源",
    steps: [
      { title: "登记数据源", component: "register-db" },
      { title: "探查数据表", component: "register-dbTable" },
      { title: "登记字典表", component: "register-dbTable-catalog", props: { catalogPhase: "dictionary" } },
      { title: "登记业务/日志表", component: "register-dbTable-catalog", props: { catalogPhase: "business" } },
      { title: "登记内容审查", component: "register-confirm" },
      { title: "完成", component: "register-finish", props: { finishType: "datasource" }, hidden: true },
    ],

  },

  app: {

    title: "",

    steps: [

      { title: "业务系统", component: "register-app" },

      { title: "完成", component: "register-finish", props: { finishType: "application" } },

    ],

  },

  catalog: {

    title: "登记数据目录",

    steps: [

      { title: "目录登记", component: "register-catalog" },

      { title: "完成", component: "register-finish", props: { finishType: "catalog" } },

    ],

  },

  db: {
    title: "登记数据资源",
    steps: [
      { title: "登记数据源", component: "register-db" },
      { title: "探查数据表", component: "register-dbTable" },
      { title: "登记字典表", component: "register-dbTable-catalog", props: { catalogPhase: "dictionary" } },
      { title: "登记业务/日志表", component: "register-dbTable-catalog", props: { catalogPhase: "business" } },
      { title: "登记内容审查", component: "register-confirm" },
      { title: "完成", component: "register-finish", props: { finishType: "datasource" }, hidden: true },
    ],

  },

  fileCatalog: {

    title: "",

    steps: [

      { title: "数据目录登记", component: "register-fileCatalog" },

      { title: "导入数据文件", component: "register-fileCatalog-data" },

      { title: "生成数据目录", component: "register-finish", props: { finishType: "catalog" } },

    ],

  },

  quality: {

    title: "数据质检",

    steps: [

      { title: "规则定义配置", component: "quality-rule-config", props: { id: props.id } },

      { title: "质检任务设置", component: "quality-task-config", props: { id: props.id } },

      { title: "提交配置", component: "task-finish", props: { ...props.qualityData } },

    ],

  },

  "car-apply": {

    // 带购物车的申请流程

    title: "订阅申请单",

    steps: [

      { title: "选择申请资源", component: "car-list" },

      { title: "填写申请单", component: "car-register-apply" },

      {

        title: "完成申请",

        component: "register-finish",

        props: {

          cols: [

            { prop: "assetName", label: "资产名称" },

            { prop: "orgId", label: "提供部门", options: "org" },

            { prop: "status", label: "信息完整状态", align: "center" },

            { prop: "applyTime", label: "申请时间" },

          ],

        },

      },

    ],

  },

  apply: {

    title: "订阅申请单",

    steps: [

      { title: "登记", component: "car-register-apply" },

      {

        title: "完成申请",

        component: "register-finish",

        props: {

          cols: [

            { prop: "assetName", label: "资产名称" },

            { prop: "orgId", label: "提供部门", options: "org" },

            { prop: "status", label: "信息完整状态", align: "center" },

            { prop: "applyTime", label: "申请时间" },

          ],

        },

      },

    ],

  },

};



const open = computed({

  get: () => props.modelValue,

  set: (val: boolean) => emit("update:modelValue", val),

});



const initData = ref({});

const completed = ref(false);

const isExploreQuickEdit = computed(
  () => props.registerClass === "db" && props.registerMode === "explore-edit"
);

const isRegisterFinishStep = (state): boolean => {
  return state.steps?.[state.currentStep]?.component === "register-finish";
};



const onCancel = (): void => {

  emit("close");

};

const onCompleted = (): void => {

  if (completed.value) return;

  completed.value = true;

  emit("completed");

  open.value = false;

};







/*

不同登记类型，使用不同的按钮配置操作

*/

const refreshState = (type) => {
  // 数据探查中的编辑仅维护已登记数据源本身，不重复进入表探查、分类和审查。
  // registerClass 仍为 db，确保 register-db 继续复用同一份保存与校验逻辑。
  if (isExploreQuickEdit.value) {
    store.state.hiddenStep = true;
    actions.value = [
      {
        code: "cancel",
        name: "取消",
        props: { type: "default" },
      },
      {
        // asset-register 的 finish 动作会在当前步骤保存成功后统一发出 completed 并关闭弹窗。
        code: "finish",
        name: "保存并完成",
        icon: "check-circle",
        props: { type: "primary" },
      },
    ];
    return;
  }
  store.state.hiddenStep = false;
  if (type === "app") {
    actions.value = [
      {
        code: "pre",
        name: "上一步",
        icon: "rollback",
        class: "btn-pre",
        props: {
          type: "default",
        },
        if({ isFirstStep, state }) {
          return !isFirstStep && !isRegisterFinishStep(state);
        },
      },
      {
        props: {
          type: "primary",
          plain: true,
        },
        code: "save",
        name: "保存",
        icon: "save",
        if({ state }) {
          // 第三步表登记，不需要保存按钮
          return state.currentStep !== 2;
        },
      },
      {
        props: {
          type: "primary",
        },
        code: "next",
        name: "下一步",
        icon: "right-circle",
        if({ isSecondLastStep }) {
          return !isSecondLastStep;
        },
      },
      {
        props: {
          type: "primary",
        },
        code: "finish",
        name: "完成登记",
        icon: "check-circle",
        if({ state, isSecondLastStep }) {
          return isSecondLastStep;
        },
      },
    ];
  }

  // 数据接入
  if (type === "quality") {
    actions.value = [
      {
        code: "cancel",
        name: "取消",
        props: {
          type: "default",
        },
      },
      {
        code: "pre",
        name: "上一步",
        icon: "rollback",
        class: "btn-pre",
        props: {
          type: "default",
        },
        if({ isFirstStep }) {
          return !isFirstStep;
        },
      },
      {
        props: {
          type: "primary",
          plain: true,
        },
        code: "save",
        name: "保存",
        icon: "save",
      },
      {
        props: {
          type: "primary",
        },
        code: "next",
        name: "下一步",
        icon: "right-circle",
        if({ isSecondLastStep }) {
          return !isSecondLastStep;
        },
      },
      {
        props: {
          type: "primary",
        },
        code: "commit",
        name: "提交配置",
        icon: "check-circle",
        if({ isSecondLastStep }) {
          return isSecondLastStep;
        },
      },
    ];
  }

  // 订阅申请按钮配置
  if (type === "apply") {
    store.state.hiddenStep = true;
    actions.value = [
      {
        code: "cancel",
        name: "取消",
        props: {
          type: "default",
        },
      },
      {
        props: {
          type: "primary",
          plain: true,
        },
        code: "save",
        name: "保存",
        icon: "save",
      },
      {
        props: {
          type: "primary",
        },
        code: "commit",
        name: "提交申请",
        icon: "check-circle",
        if({ isSecondLastStep }) {
          return isSecondLastStep;
        },
      },
    ];
  }

  // 订阅申请按钮配置
  if (type === "car-apply") {
    store.state.hiddenStep = true;
    actions.value = [
      {
        code: "cancel",
        name: "取消",
        props: {
          type: "default",
        },
      },
      {
        code: "pre",
        name: "上一步",
        icon: "rollback",
        class: "btn-pre",
        props: {
          type: "default",
        },
        if({ isFirstStep }) {
          return !isFirstStep;
        },
      },
      {
        code: "print",
        name: "打印",
        props: {
          type: "dashed",
        },
        if({ isSecondLastStep }) {
          return isSecondLastStep;
        },
      },
      {
        props: {
          type: "primary",
          plain: true,
        },
        code: "save",
        name: "保存",
        icon: "save",
        if({ isSecondLastStep }) {
          return isSecondLastStep;
        },
      },
      {
        props: {
          type: "primary",
        },
        code: "next",
        name: "下一步",
        icon: "right-circle",
        if({ isSecondLastStep }) {
          return !isSecondLastStep;
        },
      },
      {
        props: {
          type: "primary",
        },
        code: "commit",
        name: "提交申请",
        icon: "check-circle",
        if({ isSecondLastStep }) {
          return isSecondLastStep;
        },
      },
    ];
  }

  if (["dbTable", "db"].includes(type)) {
    actions.value = [
      {
        code: "pre",
        name: "上一步",
        icon: "rollback",
        class: "btn-pre",
        props: {
          type: "default",
        },
        if({ isFirstStep }) {
          return !isFirstStep;
        },
      },
      {
        props: {
          type: "primary",
          plain: true,
        },
        code: "save",
        name: "保存",
        icon: "save",
        if({ state }) {
          return [0, 2, 3].includes(state.currentStep) && !isRegisterFinishStep(state);
        },
      },
      {
        props: {
          type: "primary",
        },
        code: "next",
        name: "下一步",
        icon: "right-circle",
        if({ state }) {
          return state.currentStep !== 4 && !isRegisterFinishStep(state);
        },
      },
      {
        props: {
          type: "primary",
        },
        // asset-register 仅会在 finish 动作成功后推进到隐藏的 register-finish 步骤。
        code: "finish",
        name: "完成登记",
        icon: "check-circle",
        if({ state }) {
          return state.currentStep == 4;
        },
      },
    ];
  }
};



watch(

  open,

  (val: boolean) => {

    store.data = props.registerData;

    if (val) completed.value = false;

    if (val && props.registerClass) {

      initData.value = {

        registerClass: props.registerClass,

        title: isExploreQuickEdit.value ? "编辑数据源" : config[props.registerClass]?.title,

        assets: props.assets?.length > 0 ? props.assets : [{ assetClass: props.registerClass } as Asset],

        // 轻量编辑只有一个数据源表单步骤；标准入口维持既有五步登记配置。
        steps: isExploreQuickEdit.value
          ? [{ title: "编辑数据源", component: "register-db" }]
          : config[props.registerClass]?.steps || [],

        currentStep: props.registerData.currentStep ?? props.currentStep ?? 0,

        updateFormRule: props.updateFormRule || [],

        registerMode: props.registerMode || "",

      };

    }

    refreshState(props.registerClass);

  },

  { immediate: true }

);

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
