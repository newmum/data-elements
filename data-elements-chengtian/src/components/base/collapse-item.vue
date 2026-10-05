<template>
  <el-collapse-item v-bind="$attrs">
    <template #title>
      <div class="nav">
        <div class="z-1 font-bold text-20px flex-y-center gap-1 h-full overflow-hidden">
          <slot name="icon">
            <Icon icon="pointer" size="24px" />
          </slot>
          <slot name="title">
            {{ title }}
          </slot>
        </div>
      </div>
    </template>
    <slot></slot>
  </el-collapse-item>
</template>

<script setup lang="ts">
defineProps<{ title: string }>();
</script>

<style lang="scss" scoped>
:deep(.el-collapse-item__arrow) {
  display: none;
}
:deep(.el-collapse-item__wrap) {
  border-bottom: 0;
}
:deep(.el-collapse-item__content) {
  padding-bottom: 12px;
}
.nav {
  @apply pos-relative h-48px rounded-2 flex-y-center gap-2 pl-2;
  background-image: url("@/assets/images/19/title-bg.svg");
  background-size: cover;
}
</style>
