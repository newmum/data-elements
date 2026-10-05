<template>
  <div
    class="app-empty"
    :class="[`app-empty--${scene.key}`, { 'app-empty--compact': compact }]"
    v-bind="$attrs"
  >
    <slot name="image">
      <div class="app-empty__visual" :style="visualStyle">
        <div class="app-empty__halo"></div>
        <div class="app-empty__card app-empty__card--back"></div>
        <div class="app-empty__card app-empty__card--front">
          <Icon :icon="icon || scene.icon" :size="iconSize" />
        </div>
        <div class="app-empty__dot app-empty__dot--one"></div>
        <div class="app-empty__dot app-empty__dot--two"></div>
      </div>
    </slot>

    <div class="app-empty__title">{{ title || scene.title }}</div>
    <div class="app-empty__description">
      <slot name="description">{{ description || scene.description }}</slot>
    </div>

    <div v-if="$slots.default || actionText" class="app-empty__actions">
      <slot>
        <el-button v-if="actionText" type="primary" plain @click="$emit('action')">
          {{ actionText }}
        </el-button>
      </slot>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";

defineOptions({
  inheritAttrs: false,
});

const props = withDefaults(
  defineProps<{
    type?: string;
    title?: string;
    description?: string;
    icon?: string;
    imageSize?: number | string;
    compact?: boolean;
    actionText?: string;
  }>(),
  {
    type: "auto",
    imageSize: 108,
    compact: false,
  }
);

defineEmits<{
  (e: "action"): void;
}>();

interface EmptyScene {
  key: string;
  icon: string;
  title: string;
  description: string;
}

const scenes: Record<string, EmptyScene> = {
  default: {
    key: "default",
    icon: "box",
    title: "暂无数据",
    description: "当前还没有可展示的数据，调整条件或稍后再试。",
  },
  table: {
    key: "table",
    icon: "table",
    title: "暂无数据",
    description: "当前筛选条件下没有记录，可调整查询条件或新增业务数据。",
  },
  list: {
    key: "list",
    icon: "menu",
    title: "暂无列表数据",
    description: "当前列表还没有内容，可切换范围、刷新页面或新增一条记录。",
  },
  catalog: {
    key: "catalog",
    icon: "category",
    title: "暂无目录资源",
    description: "暂未归集到目录资源，可先完成资源登记或目录编制。",
  },
  asset: {
    key: "asset",
    icon: "db",
    title: "暂无数据资产",
    description: "当前范围内没有数据资产，可导入表、文件或接口资源。",
  },
  api: {
    key: "api",
    icon: "api",
    title: "暂无接口服务",
    description: "还没有发布可调用的 API 服务，可从数据服务配置开始。",
  },
  app: {
    key: "app",
    icon: "app",
    title: "暂无应用系统",
    description: "暂无关联应用，可登记应用后接入数据资源。",
  },
  file: {
    key: "file",
    icon: "file",
    title: "暂无文件数据",
    description: "还没有上传或同步文件，可上传数据文件后查看。",
  },
  image: {
    key: "image",
    icon: "view",
    title: "暂无图片",
    description: "当前没有可预览的图片资源。",
  },
  notice: {
    key: "notice",
    icon: "bell",
    title: "暂无消息通知",
    description: "新的通知、待办和系统提醒会显示在这里。",
  },
  search: {
    key: "search",
    icon: "search",
    title: "未找到匹配结果",
    description: "换个关键词、减少筛选条件，可能会有新的发现。",
  },
  permission: {
    key: "permission",
    icon: "safe",
    title: "暂无权限数据",
    description: "当前角色或用户还没有配置权限，可先维护菜单、角色或授权关系。",
  },
  form: {
    key: "form",
    icon: "document",
    title: "暂无表单数据",
    description: "暂未收集到表单内容，可提交或导入后查看。",
  },
  task: {
    key: "task",
    icon: "todo",
    title: "暂无待办任务",
    description: "当前没有需要处理的任务，新的流程待办会自动展示。",
  },
};

const scene = computed(() => {
  const explicitScene = scenes[props.type || ""];
  if (explicitScene && props.type !== "auto") return explicitScene;

  const text = `${props.title || ""}${props.description || ""}`;
  if (/消息|通知|提醒/.test(text)) return scenes.notice;
  if (/搜索|筛选|匹配|查询/.test(text)) return scenes.search;
  if (/目录|资源/.test(text)) return scenes.catalog;
  if (/资产|数据库|数据源|库/.test(text)) return scenes.asset;
  if (/表格|列表|记录|明细|字段|表/.test(text)) return scenes.table;
  if (/接口|API|服务/.test(text)) return scenes.api;
  if (/应用|系统/.test(text)) return scenes.app;
  if (/文件|上传|附件/.test(text)) return scenes.file;
  if (/图片|图像|预览/.test(text)) return scenes.image;
  if (/权限|角色|用户|菜单|授权/.test(text)) return scenes.permission;
  if (/表单/.test(text)) return scenes.form;
  if (/待办|任务|流程|工单/.test(text)) return scenes.task;

  return scenes.default;
});

const visualSize = computed(() => Number(props.imageSize) || 108);
const iconSize = computed(() => Math.max(28, Math.round(visualSize.value * 0.38)));
const visualStyle = computed(() => ({
  width: `${visualSize.value}px`,
  height: `${visualSize.value}px`,
}));
</script>

<style scoped lang="scss">
.app-empty {
  --empty-primary: #2f6df6;
  --empty-secondary: #57b5ff;
  --empty-soft: #eef5ff;

  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 220px;
  padding: 32px 24px;
  text-align: center;
}

.app-empty--compact {
  min-height: 128px;
  padding: 16px;
}

.app-empty--notice {
  --empty-primary: #7c5cff;
  --empty-secondary: #9b8cff;
  --empty-soft: #f2efff;
}

.app-empty--catalog,
.app-empty--asset {
  --empty-primary: #1677ff;
  --empty-secondary: #33c2ff;
  --empty-soft: #edf7ff;
}

.app-empty--api,
.app-empty--app {
  --empty-primary: #0f9f8f;
  --empty-secondary: #43d6be;
  --empty-soft: #e9fbf7;
}

.app-empty--permission,
.app-empty--task {
  --empty-primary: #f59e0b;
  --empty-secondary: #ffd166;
  --empty-soft: #fff8e7;
}

.app-empty--file,
.app-empty--image,
.app-empty--form {
  --empty-primary: #4f63ff;
  --empty-secondary: #9aa6ff;
  --empty-soft: #f0f3ff;
}

.app-empty__visual {
  position: relative;
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  margin-bottom: 16px;
}

.app-empty--compact .app-empty__visual {
  margin-bottom: 10px;
}

.app-empty__halo {
  position: absolute;
  inset: 12%;
  background: radial-gradient(
    circle,
    color-mix(in srgb, var(--empty-secondary) 30%, transparent),
    transparent 68%
  );
  border-radius: 999px;
  filter: blur(1px);
}

.app-empty__card {
  position: absolute;
  border: 1px solid color-mix(in srgb, var(--empty-primary) 20%, #ffffff);
  border-radius: 18px;
  box-shadow: 0 16px 34px rgba(47, 109, 246, 0.12);
}

.app-empty__card--back {
  width: 58%;
  height: 42%;
  background: linear-gradient(135deg, #ffffff, var(--empty-soft));
  transform: translate(14%, -18%) rotate(8deg);
}

.app-empty__card--front {
  display: grid;
  place-items: center;
  width: 62%;
  height: 54%;
  color: var(--empty-primary);
  background: linear-gradient(145deg, #ffffff 0%, var(--empty-soft) 100%);
  transform: translate(-3%, 3%) rotate(-5deg);
}

.app-empty__dot {
  position: absolute;
  width: 9px;
  height: 9px;
  background: var(--empty-secondary);
  border-radius: 999px;
  opacity: 0.8;
}

.app-empty__dot--one {
  top: 20%;
  right: 19%;
}

.app-empty__dot--two {
  bottom: 22%;
  left: 20%;
  width: 6px;
  height: 6px;
  background: var(--empty-primary);
}

.app-empty__title {
  margin-bottom: 6px;
  font-size: 15px;
  font-weight: 700;
  line-height: 22px;
  color: #34405f;
}

.app-empty__description {
  max-width: 360px;
  font-size: 13px;
  line-height: 21px;
  color: #7b849b;
}

.app-empty--compact .app-empty__title {
  font-size: 14px;
}

.app-empty--compact .app-empty__description {
  max-width: 260px;
  font-size: 12px;
  line-height: 19px;
}

.app-empty__actions {
  margin-top: 16px;
}
</style>
