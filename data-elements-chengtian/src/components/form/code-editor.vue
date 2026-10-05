<template>
  <div v-if="showOption" class="font-bold flex-y-center gap-4 mb-4">
    <el-select
      v-model="stateProps.lang"
      :options="[
        { label: 'Vue', value: 'vue' },
        { label: 'JSON', value: 'json' },
        { label: 'SQL', value: 'sql' },
        { label: 'JavaScript', value: 'javascript' },
        { label: 'yaml', value: 'yaml' },
      ]"
      placeholder="Select"
      style="width: 150px"
    />
    <el-select
      v-model="stateProps.theme"
      :options="[
        { label: 'monokai', value: 'monokai' },
        { label: 'chrome', value: 'chrome' },
        { label: 'github', value: 'github' },
      ]"
      placeholder="Select"
      style="width: 150px"
    />
  </div>

  <el-scrollbar
    class="flex-1 flex flex-col"
    :view-style="showOption ? 'height:calc(100% - 50px)' : 'height:100%'"
  >
    <div v-if="showCopy" class="absolute z-36 right-5 top-2">
      <el-link type="primary" @click="handleCopyCode">
        <el-icon>
          <CopyDocument />
        </el-icon>
        一键复制
      </el-link>
    </div>
    <!-- 代码编辑器组件 -->
    <VAceEditor
      v-if="editorVisible"
      ref="editorRef"
      v-model:value="code"
      v-bind="stateProps"
      style="min-height: 200px; font-size: 14px; border-radius: 6px"
      :style="{ height }"
      @change="handleContentChange"
    />
  </el-scrollbar>
</template>

<script setup lang="ts">
import { VAceEditor } from "vue3-ace-editor";
// 引入 Ace 核心库
import "ace-builds/src-noconflict/ace";
// 引入 语言模式（语法高亮、语法校验）
import "ace-builds/src-noconflict/mode-json";
import "ace-builds/src-noconflict/mode-sql";
import "ace-builds/src-noconflict/mode-javascript";
import "ace-builds/src-noconflict/mode-yaml";
import "ace-builds/src-noconflict/mode-vue";
import "ace-builds/src-noconflict/snippets/javascript";
// 引入主题（monokai 是深色主题，也可选择 github 等浅色主题）
import "ace-builds/src-noconflict/theme-monokai";
import "ace-builds/src-noconflict/theme-github";
import "ace-builds/src-noconflict/theme-chrome";
// 引入语言工具扩展（用于代码提示）
import "ace-builds/src-noconflict/ext-language_tools";
import "ace-builds/src-noconflict/ext-searchbox.js";

const emits = defineEmits<{
  (e: "update:modelValue", value: string): void;
  (e: "change", value: string): void;
}>();

const props = withDefaults(
  defineProps<{
    modelValue: string;
    code?: string;
    lang?: "json" | "javascript" | "yaml" | "sql" | "vue";
    theme?: string;
    readOnly?: boolean;
    height?: string;
    placeholder?: string;
    showOption?: boolean;
    showCopy?: boolean;
  }>(),
  {
    lang: "json",
    theme: "chrome",
    height: "100%",
    placeholder: "请输入...",
  }
);

const code = props.code ? ref(props.code) : useVModel(props, "modelValue", emits);

/** v-if 控制编辑器显隐，readOnly 变化时先隐藏 → 更新 options → 再显示（强制 ACE 重建） */
const editorVisible = ref(true);
watch(
  () => props.readOnly,
  async (newVal) => {
    // 1. 先保存当前内容
    const current = code.value;
    // 2. 隐藏编辑器（销毁旧 ACE 实例）
    editorVisible.value = false;
    // 3. 同步更新 stateProps（新实例将读取此值）
    stateProps.options.readOnly = !!newVal;
    // 等 DOM 销毁完成
    await nextTick();
    // 4. 重新显示（创建新的 ACE 实例，读取最新的 readOnly）
    editorVisible.value = true;
    // 5. 等新实例挂载完成后回填内容
    await nextTick();
    code.value = current;
  }
);

// 语言模式（支持 json、sql、javascript、html 等）
const stateProps = reactive({
  lang: props.lang,
  theme: props.theme,
  placeholder: props.placeholder,
  options: {
    readOnly: props.readOnly, // 是否只读
    enableBasicAutocompletion: true, // 启用基础自动补全
    enableLiveAutocompletion: true, // 启用实时补全（输入时自动提示）
    enableSnippets: true, // 启用代码片段
    showLineNumbers: true, // 显示行号
    tabSize: 2, // 缩进空格数
    wrap: true, // 自动换行
  },
});

// 监听内容变化，解析 JSON
const handleContentChange = () => {
  emits("change", code.value);
};

const { copy, copied } = useClipboard();

/** 一键复制 */
const handleCopyCode = () => {
  if (code.value) {
    copy(code.value);
  }
};

watch(copied, () => {
  if (copied.value) {
    ElMessage.success("复制成功");
  }
});

// 监听 props 变化，更新 stateProps
watch(
  () => props.theme,
  (newTheme) => {
    stateProps.theme = newTheme;
  }
);
watch(
  () => props.lang,
  (val) => {
    stateProps.lang = val;
  }
);

defineExpose({
  copy: handleCopyCode,
});
</script>
