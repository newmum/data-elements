<template>
  <div class="list-toolbar" :class="{ 'list-toolbar--compact': compact }">
    <div v-if="$slots.actions" class="list-toolbar__actions">
      <slot name="actions" />
    </div>
    <div v-if="$slots.default" class="list-toolbar__filters">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    compact?: boolean;
  }>(),
  {
    compact: false,
  }
);
</script>

<style scoped lang="scss">
.list-toolbar {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 32px;
  margin-bottom: 20px;
}

.list-toolbar--compact {
  margin-bottom: 12px;
}

.list-toolbar__actions,
.list-toolbar__filters {
  display: flex;
  align-items: center;
  gap: 10px;
}

.list-toolbar__filters {
  justify-content: flex-end;
  min-width: 0;
  margin-left: auto;
}

:deep(.list-toolbar__select) {
  width: 150px;
}

:deep(.list-toolbar__keyword) {
  width: 300px;
}

@media (max-width: 1180px) {
  .list-toolbar,
  .list-toolbar__filters {
    flex-wrap: wrap;
  }

  :deep(.list-toolbar__select) {
    width: 138px;
  }

  :deep(.list-toolbar__keyword) {
    width: 260px;
  }
}
</style>
