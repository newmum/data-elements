<template>
  <div
    class="loading-state"
    :class="[`loading-state--${type}`, { 'loading-state--compact': compact }]"
    role="status"
    aria-live="polite"
  >
    <div class="loading-state__visual" aria-hidden="true">
      <template v-if="type === 'tree'">
        <div class="loading-state__tree-search skeleton-block"></div>
        <div
          v-for="(level, index) in treeLevels"
          :key="index"
          class="loading-state__tree-row"
          :style="{ paddingLeft: `${level * 20}px` }"
        >
          <span class="loading-state__tree-toggle skeleton-block"></span>
          <span class="loading-state__tree-icon skeleton-block"></span>
          <span
            class="loading-state__tree-label skeleton-block"
            :style="{ width: `${treeWidths[index]}%` }"
          ></span>
        </div>
      </template>
      <template v-else-if="type === 'table'">
        <div class="loading-state__table-head">
          <span v-for="index in 5" :key="index" class="skeleton-block"></span>
        </div>
        <div v-for="row in 6" :key="row" class="loading-state__table-row">
          <span
            v-for="column in 5"
            :key="column"
            class="skeleton-block"
            :style="{ width: tableCellWidth(row, column) }"
          ></span>
        </div>
      </template>
      <template v-else>
        <div class="loading-state__list-toolbar">
          <span class="loading-state__list-action skeleton-block"></span>
          <span class="loading-state__list-search skeleton-block"></span>
        </div>
        <div v-for="row in 5" :key="row" class="loading-state__list-row">
          <span class="loading-state__list-avatar skeleton-block"></span>
          <span class="loading-state__list-content">
            <span
              class="loading-state__list-title skeleton-block"
              :style="{ width: `${54 + (row % 3) * 9}%` }"
            ></span>
            <span
              class="loading-state__list-detail skeleton-block"
              :style="{ width: `${72 - (row % 2) * 12}%` }"
            ></span>
          </span>
          <span class="loading-state__list-tail skeleton-block"></span>
        </div>
      </template>
    </div>
    <span class="loading-state__announcement">{{ accessibleText }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";

const treeLevels = [0, 0, 1, 1, 2, 1, 2];
const treeWidths = [58, 72, 64, 78, 54, 68, 46];

const props = withDefaults(
  defineProps<{
    type?: "table" | "tree" | "list";
    title?: string;
    description?: string;
    compact?: boolean;
  }>(),
  {
    type: "list",
    title: "",
    description: "",
    compact: false,
  }
);

const defaultTitle = computed(() => {
  if (props.type === "table") return "正在加载表格数据";
  if (props.type === "tree") return "正在加载树形数据";
  return "正在加载数据";
});

const accessibleText = computed(() =>
  [props.title || defaultTitle.value, props.description].filter(Boolean).join("，")
);

const tableCellWidth = (row: number, column: number) => {
  const widths = [66, 84, 58, 72, 48];
  return `${Math.max(34, widths[column - 1] - ((row + column) % 3) * 8)}%`;
};
</script>

<style scoped lang="scss">
.loading-state {
  box-sizing: border-box;
  width: 100%;
  min-height: 220px;
  padding: 18px 20px;
}

.loading-state--compact {
  min-height: 176px;
  padding: 12px 14px;
}

.loading-state__visual {
  width: 100%;
  max-width: 100%;
}

.loading-state--tree .loading-state__visual {
  max-width: 320px;
}

.loading-state__announcement {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.skeleton-block {
  position: relative;
  overflow: hidden;
  background: #edf1f6;
  border-radius: 4px;
}

.skeleton-block::after {
  position: absolute;
  inset: 0;
  content: "";
  background: linear-gradient(
    90deg,
    transparent 0%,
    rgba(255, 255, 255, 0.72) 46%,
    transparent 100%
  );
  transform: translateX(-100%);
  animation: loading-state-shimmer 1.45s ease-in-out infinite;
}

.loading-state__table-head,
.loading-state__table-row {
  display: grid;
  grid-template-columns: 0.45fr 1.7fr 1fr 1fr 0.65fr;
  gap: clamp(8px, 1.5vw, 20px);
  align-items: center;
  min-height: 42px;
  padding: 0 16px;
  border-bottom: 1px solid #eef1f5;
}

.loading-state__table-head {
  min-height: 40px;
  background: #f7f9fc;
  border: 1px solid #e9edf3;
  border-radius: 4px 4px 0 0;
}

.loading-state__table-row {
  border-right: 1px solid #eef1f5;
  border-left: 1px solid #eef1f5;
}

.loading-state__table-row:last-child {
  border-radius: 0 0 4px 4px;
}

.loading-state__table-head span {
  width: 68%;
  height: 10px;
}

.loading-state__table-row span {
  height: 9px;
}

.loading-state__tree-search {
  width: 100%;
  height: 30px;
  margin-bottom: 14px;
}

.loading-state__tree-row {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 30px;
}

.loading-state__tree-toggle {
  flex: 0 0 8px;
  width: 8px;
  height: 8px;
  border-radius: 2px;
}

.loading-state__tree-icon {
  flex: 0 0 16px;
  width: 16px;
  height: 16px;
  border-radius: 3px;
}

.loading-state__tree-label {
  height: 10px;
}

.loading-state__list-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding-bottom: 14px;
  border-bottom: 1px solid #eef1f5;
}

.loading-state__list-action {
  width: 88px;
  height: 30px;
}

.loading-state__list-search {
  width: min(280px, 42%);
  height: 30px;
}

.loading-state__list-row {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 54px;
  border-bottom: 1px solid #f0f2f5;
}

.loading-state__list-avatar {
  flex: 0 0 28px;
  width: 28px;
  height: 28px;
  border-radius: 50%;
}

.loading-state__list-content {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.loading-state__list-title {
  max-width: 460px;
  height: 10px;
}

.loading-state__list-detail {
  max-width: 620px;
  height: 8px;
}

.loading-state__list-tail {
  flex: 0 0 52px;
  width: 52px;
  height: 10px;
}

@keyframes loading-state-shimmer {
  60%,
  100% {
    transform: translateX(100%);
  }
}

@media (prefers-reduced-motion: reduce) {
  .skeleton-block::after {
    animation: none;
  }
}

@media (max-width: 640px) {
  .loading-state {
    padding-inline: 12px;
  }

  .loading-state__table-head,
  .loading-state__table-row {
    grid-template-columns: 0.5fr 1.5fr 1fr;
    padding-inline: 10px;
  }

  .loading-state__table-head span:nth-child(n + 4),
  .loading-state__table-row span:nth-child(n + 4) {
    display: none;
  }
}
</style>
