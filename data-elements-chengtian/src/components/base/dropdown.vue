<template>
  <el-dropdown
    :trigger="trigger"
    :placement="placement"
    :disabled="disabled"
    @command="handleCommand"
  >
    <!-- 下拉触发元素 -->
    <div class="dropdown-trigger" :class="{ 'dropdown-disabled': disabled }">
      <slot>
        <el-button :icon="icon" type="text">
          下拉菜单
          <el-icon class="el-icon--right">
            <ArrowDown />
          </el-icon>
        </el-button>
      </slot>
    </div>

    <!-- 下拉菜单内容 -->
    <template #dropdown>
      <slot name="content">
        <el-dropdown-menu>
          <el-dropdown-item
            v-for="(option, index) in options"
            :key="index"
            :command="option.command || option.value"
            :disabled="option.disabled"
            :divided="option.divided"
          >
            <el-icon v-if="option.icon">
              <component :is="option.icon" />
            </el-icon>
            <span v-if="option.label">{{ option.label }}</span>
          </el-dropdown-item>
        </el-dropdown-menu>
      </slot>
    </template>
  </el-dropdown>
</template>

<script setup lang="ts">
import { ArrowDown } from "@element-plus/icons-vue";
import type { Component } from "vue";

// 定义下拉选项类型
interface DropdownOption {
  label: string; // 显示文本
  value?: string | number; // 选项值
  command?: string | number; // 点击时触发的命令（默认使用value）
  icon?: Component; // 图标组件
  disabled?: boolean; // 是否禁用
  divided?: boolean; // 是否显示分割线
}

// 组件参数
defineProps({
  // 下拉选项数组
  options: {
    type: Array as () => DropdownOption[],
    default: () => [],
  },
  // 触发方式（hover/click/contextmenu）
  trigger: {
    type: String as PropType<"hover" | "click" | "contextmenu">,
    default: "hover",
  },
  // 下拉框位置
  placement: {
    type: String,
    default: "bottom-end",
  },
  // 是否禁用
  disabled: {
    type: Boolean,
    default: false,
  },
  // 默认图标
  icon: {
    type: Object as () => Component,
    default: null,
  },
});

// 点击事件回调
const emit = defineEmits<{
  (e: "select", command: string): void;
}>();

const handleCommand = (command: string) => {
  emit("select", command);
};
</script>

<style scoped>
.dropdown-trigger {
  display: inline-flex;
  align-items: center;
  cursor: pointer;
}

.dropdown-disabled {
  cursor: not-allowed;
  opacity: 0.6;
}
</style>
