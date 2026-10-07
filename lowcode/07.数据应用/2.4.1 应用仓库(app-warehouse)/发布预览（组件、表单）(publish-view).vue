<template>
  <div class="preview-wrapper" style="width: 100%; height: 100%; overflow: auto">
    <!-- 组件预览 -->
    <component :is="state.name" v-if="previewType === 'component'" />

    <!-- 表单预览 -->
    <div v-else-if="previewType === 'form'" ref="formPreviewRef" class="form-preview-container">
      <form-create v-if="formData.rule.length" :rule="formData.rule" :option="formData.option" />
      <div v-else class="loading-tip">加载中...</div>
    </div>

    <!-- 加载中 -->
    <div v-else class="loading-tip">正在加载...</div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, reactive, nextTick, getCurrentInstance } from "vue";
import { useRoute } from "vue-router";
import { compileCode } from "@/plugins/compiler/sfc-compiler";
import { appComponent } from "@/plugins/compiler/dynamicComponent.js";

const route = useRoute();
const tid = ref("");
const pid = ref("");
const previewType = ref<"component" | "form">("component");

// 组件预览状态
const state = reactive({
  code: "",
  name: "",
  compileJs: "",
  compileCss: "",
});

// 表单预览数据
const formData = reactive({
  rule: [] as any[],
  option: {} as any,
});

// 在 setup 顶层获取实例，避免异步调用时丢失
const instance = getCurrentInstance();
const app = instance?.appContext.app;

// ==================== 组件预览逻辑 ====================

const getSourceCode = async (tid: string) => {
  if (!tid) return;
  const res = (await $common.post(
    `/sym/component?action=getSourceCode&tid=${tid}`
  )) as unknown as string;
  if (res) {
    state.code = res;
    compileAndRender(state.code);
  }
};

// 编译并渲染组件
const compileAndRender = async (sourceCode: string) => {
  if (!sourceCode) return;

  try {
    // 编译源码
    const { compileJs, compileCss } = compileCode(sourceCode);

    // 生成唯一的组件名（驼峰格式，与 appComponent 保持一致）
    const rawName = `preview${tid.value}`;
    const componentName = rawName
      .replace(/-(\w)/g, (_, c) => c.toUpperCase())
      .replace(/^(\w)/, (c) => c.toUpperCase());

    // 传递给 appComponent 需要原始名称（内部会再转换一次）
    const appState = {
      name: rawName,
      compileJs,
      compileCss,
    };

    // 使用顶层获取的 app 实例注册组件
    if (app) {
      appComponent(app, appState);

      // 设置 state - 使用驼峰组件名
      state.compileJs = compileJs;
      state.compileCss = compileCss;
      state.name = componentName;
    } else {
      console.error("无法获取 Vue app 实例");
    }

    // 等待 DOM 更新后渲染
    await nextTick();
  } catch (error) {
    console.error("编译失败:", error);
  }
};

// ==================== 表单预览逻辑 ====================

const getFormData = async () => {
  if (!tid.value || !pid.value) {
    console.error("tid 或 pid 为空，无法获取表单数据");
    return;
  }

  try {
    const res = (await $common.post("/sym/form?action=get", {
      tid: tid.value,
      env: pid.value,
    })) as any;

    if (res && res.formJson) {
      const formJson = JSON.parse(res.formJson);
      // formJson 的结构: { rule: "[]", options: "{}" }
      const rules =
        typeof formJson.rule === "string" ? JSON.parse(formJson.rule) : formJson.rule || [];
      const options =
        typeof formJson.options === "string"
          ? JSON.parse(formJson.options)
          : formJson.options || {};

      formData.rule = rules;
      formData.option = {
        ...options,
        submitBtn: options.submitBtn !== false,
        resetBtn: options.resetBtn !== false,
        readonly: false,
      };

      console.log("表单数据加载成功，rule 数量:", rules.length);
    } else {
      console.warn("未获取到表单数据");
    }
  } catch (error) {
    console.error("获取表单数据失败:", error);
  }
};

// ==================== 初始化 ====================

onMounted(async () => {
  tid.value = route.query.tid as string;
  pid.value = route.query.pid as string;
  const type = route.query.type as string;

  previewType.value = type === "form" ? "form" : "component";

  if (previewType.value === "component") {
    // 组件预览
    await getSourceCode(tid.value);
  } else {
    // 表单预览
    await getFormData();
  }
});
</script>

<style scoped>
.preview-wrapper {
  background: #fff;
}

.form-preview-container {
  width: 100%;
  min-height: 100%;
  padding: 20px;
  background: #fff;
}

.loading-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #999;
  font-size: 14px;
}
</style>
