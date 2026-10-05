<template>
  <div v-highlight v-html="html" />
</template>

<script setup lang="ts">
import { createPatch } from "diff";
import * as Diff2Html from "diff2html";
import hljs from "highlight.js";
import "highlight.js/styles/googlecode.css";
import "diff2html/bundles/css/diff2html.min.css";
import { computed, DirectiveBinding } from "vue";

// 声明 props 并设置默认值
const props = withDefaults(
  defineProps<{
    oldString?: string;
    newString?: string;
    context?: number;
    outputFormat?: "line-by-line" | "side-by-side";
    drawFileList?: boolean;
    renderNothingWhenEmpty?: boolean;
    fileName?: string;
  }>(),
  {
    context: -1,
    outputFormat: "side-by-side",
    fileName: "",
    renderNothingWhenEmpty: false,
    drawFileList: true,
  }
);

// 定义高亮指令
const vHighlight = {
  mounted(el: HTMLElement) {
    const blocks = el.querySelectorAll("code");
    blocks.forEach((block) => {
      hljs.highlightElement(block as HTMLElement);
    });
  },
  updated(el: HTMLElement, binding: DirectiveBinding) {
    // 内容更新时重新高亮
    if (binding.value !== binding.oldValue) {
      const blocks = el.querySelectorAll("code");
      blocks.forEach((block) => {
        hljs.highlightElement(block as HTMLElement);
      });
    }
  },
};

// 生成 diff 对比的 HTML
const html = computed(() => {
  // 处理无变更时的显示逻辑
  const processedOld = props.oldString;
  const processedNew = props.newString;

  // 无差异时直接显示完整代码
  if (processedOld === processedNew) {
    return `
      <div class="border border-#ddd rounded-3px">
        <div class="d2h-file-header">
          <span class="d2h-file-name-wrapper">
            <svg
              aria-hidden="true"
              class="d2h-icon"
              height="16"
              version="1.1"
              viewBox="0 0 12 16"
              width="12"
            >
              <path
                d="M6 5H2v-1h4v1zM2 8h7v-1H2v1z m0 2h7v-1H2v1z m0 2h7v-1H2v1z m10-7.5v9.5c0 0.55-0.45 1-1 1H1c-0.55 0-1-0.45-1-1V2c0-0.55 0.45-1 1-1h7.5l3.5 3.5z m-1 0.5L8 2H1v12h10V5z"
              ></path>
            </svg>
            <span class="d2h-file-name">d6a05c0a657b4ac68eac76d7f413ac4d</span>
            <span class="d2h-tag d2h-changed d2h-changed-tag">CHANGED</span>
          </span>
          <label class="d2h-file-collapse">
            <input class="d2h-file-collapse-input" type="checkbox" name="viewed" value="viewed" />
            Viewed
          </label>
        </div>
        <!-- 未修改提示 -->
        <div class="bg-[#f8fafd] h-200px text-[rgba(0,0,0,.3)] text-center line-height-200px">
          <span>文件没有被修改</span>
        </div>
      </div>
    `;
  }

  // 创建 diff patch - ensure all required parameters are strings
  const args = [
    props.fileName || "",
    processedOld || "",
    processedNew || "",
    "",
    "",
    { context: props.context },
  ] as const;
  const patch = createPatch(...args);

  // 解析并生成 HTML
  const parsedDiff = Diff2Html.parse(patch, {
    outputFormat: props.outputFormat,
    drawFileList: props.drawFileList,
    matching: "words",
    renderNothingWhenEmpty: props.renderNothingWhenEmpty,
  });

  let diffHtml = Diff2Html.html(parsedDiff, {
    outputFormat: props.outputFormat,
    drawFileList: props.drawFileList,
    matching: "words",
    renderNothingWhenEmpty: props.renderNothingWhenEmpty,
  });

  // 注入 highlight.js 所需的 code 标签
  diffHtml = diffHtml.replace(
    /<span class="d2h-code-line-ctn">(.+?)<\/span>/g,
    '<span class="d2h-code-line-ctn"><code>$1</code></span>'
  );

  return diffHtml;
});
</script>

<style lang="scss">
.hljs {
  display: inline-block;
  height: 17px;
  padding: 0;
  vertical-align: middle;
  background: transparent;
}

.d2h-wrapper {
  position: relative;
}

.d2h-file-list-wrapper {
  display: none;
}

.d2h-wrapper .d2h-files-diff {
  position: relative;
}

.d2h-wrapper .d2h-file-side-diff {
  margin-bottom: -5px;
}

.d2h-wrapper .d2h-code-side-emptyplaceholder {
  max-height: 19px;
}

.d2h-wrapper .d2h-code-side-line,
.d2h-wrapper .d2h-code-line {
  display: block;
  width: auto;
}

.d2h-wrapper .d2h-code-side-line.d2h-info {
  height: 18px;
}

.d2h-wrapper .d2h-code-linenumber,
.d2h-code-side-linenumber {
  height: 19px;
}
</style>
