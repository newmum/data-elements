import { Directive } from "vue";

// 点击事件处理函数
const handleClick = async (e: Event) => {
  const el = e.target as HTMLElement;
  const text = el.dataset.copyText || "";
  console.log(1, el.dataset);
  if (!text) {
    ElMessage.warning("暂无可复制内容！");
    return;
  }
  await $common.copyText(text);
};

export const copy: Directive = {
  // 指令绑定到元素时触发（仅一次）
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    // 存储要复制的文本
    el.dataset.copyText = binding.value;
    // 绑定点击事件
    el.addEventListener("click", handleClick);
  },
  // 指令的值更新时触发
  updated(el: HTMLElement, binding: DirectiveBinding) {
    el.dataset.copyText = binding.value;
  },
  // 元素卸载时移除事件（避免内存泄漏）
  unmounted(el: HTMLElement) {
    el.removeEventListener("click", handleClick);
  },
};
