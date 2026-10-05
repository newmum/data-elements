<template>
  <div class="node">
    <!--  图标  -->
    <template v-if="showIcon">
      <slot name="icon">
        <Icon
          v-if="nodeType === 'dir' && nodeIcon"
          :icon="nodeIcon"
          class="color-#0b8bf9"
          size="23"
        ></Icon>
        <Icon v-if="nodeType === 'file' && nodeIcon" :icon="nodeIcon"></Icon>
      </slot>
    </template>

    <div class="node-title">
      <div class="node-title-content" :class="{ ell: !wrap }" :title="label">
        <template v-if="keyword && label.includes(keyword)">
          <template v-for="(part, index) in splitText" :key="index">
            <span v-if="part.toLowerCase() === keyword.toLowerCase()" class="highlight">
              {{ part }}
            </span>
            <span v-else>{{ part }}</span>
          </template>
        </template>
        <template v-else>{{ label }}</template>
      </div>
      <span v-if="(showCount || $slots.count) && countInLabel" class="count">
        <slot name="count" :count="count">({{ count ?? 0 }})</slot>
      </span>
    </div>

    <span v-if="(showCount || $slots.count) && !countInLabel" class="count">
      <slot name="count" :count="count">({{ count ?? 0 }})</slot>
    </span>
  </div>
</template>

<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    label: string;
    node?: any;
    keyword?: string;
    selected?: boolean;
    showCount?: boolean;
    count?: number | string;
    showIcon?: boolean;
    /*目录收缩、目录展开、节点三种图标*/
    icon?: Record<"unfold" | "fold" | "node", string>;
    /*节点类型, 默认通过node.isLeaf自动决定*/
    type?: "dir" | "file";
    /*节点是否换行*/
    wrap?: boolean;
    /*统计值是否放在节点里*/
    countInLabel?: boolean;
  }>(),
  {
    icon: () => ({ fold: "folder", unfold: "folder-open", node: "file" }),
    countInLabel: true,
  }
);

const nodeType = computed(() => {
  if (props.type) return props.type;
  return props.node.isLeaf ? "file" : "dir";
});
const nodeIcon = computed(() => {
  if (nodeType.value === "file") return props.icon.node;
  else {
    if (props.node.expanded) {
      return props.icon.unfold || props.icon.fold;
    }
    return props.icon.fold;
  }
});

// 将文本分割为高亮和非高亮部分
const splitText = computed(() => {
  if (!props.keyword) return [props.label];

  const regex = new RegExp(`(${props.keyword})`, "gi");
  return props.label.split(regex).filter(Boolean);
});
</script>

<style scoped lang="scss">
.node {
  --highlight: #ff0;
  --font-family: "Source Han Sans SC";
  --gap: 4px;
  --line-height: 26px;

  display: flex;
  flex: 1;
  gap: var(--gap);
  align-items: center;
  min-width: 0;

  overflow: hidden;
  font-family: var(--font-family), sans-serif;
  font-size: 14px;
  font-weight: 400;

  line-height: var(--line-height);
  color: var(--node-color);
  border-radius: 6px;
}

.node-title-content {
  //width: 100%;
  white-space: wrap;
  &.ell {
    //flex: 1 1 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.node-title {
  display: flex;
  flex: 1;
  align-items: center;
  min-width: 0;
}

.count {
  flex-shrink: 0;
  margin-right: 8px;
  color: var(--node-count-color);
}

.highlight {
  background-color: var(--highlight);
}
</style>
