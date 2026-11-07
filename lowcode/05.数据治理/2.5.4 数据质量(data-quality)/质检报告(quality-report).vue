<template>
  <section class="quality-entry" aria-label="质检任务与执行记录">
    <div class="quality-entry__copy">
      <h2>质检任务与执行记录</h2>
      <p v-if="qualityPath">
        在数据质量页面可按数据表名称查找质检任务、查看执行结果和处理整改工单。
      </p>
      <p v-else>当前账号未开通数据质量入口，请联系管理员。</p>
    </div>
    <el-button
      v-if="qualityPath"
      type="primary"
      :tag="RouterLink"
      :to="qualityPath"
    >
      前往数据质量
      <Icon class="quality-entry__arrow" icon="el-icon-ArrowRight" />
    </el-button>
  </section>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { RouterLink, useRouter } from "vue-router";
import { usePermissionStore } from "@/store";

const router = useRouter();
const permissions = usePermissionStore();
const qualityPath = computed(() => {
  if (!permissions.isDynamicRoutesGenerated) return undefined;
  // 各租户的父级菜单不同，已授权的隐藏路由也可作为详情入口。
  return router
    .getRoutes()
    .find(
      (record) =>
        record.meta.runtimeSfcRoot === "data-quality" &&
        !record.aliasOf &&
        !record.redirect,
    )?.path;
});
</script>

<style scoped>
.quality-entry {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px 24px;
  padding: 24px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.quality-entry__copy {
  min-width: 0;
}

.quality-entry h2 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 16px;
  font-weight: 600;
  line-height: 1.5;
}

.quality-entry p {
  margin: 6px 0 0;
  color: var(--el-text-color-regular);
  font-size: 14px;
  line-height: 1.6;
}

.quality-entry__arrow {
  margin-inline-start: 4px;
}
</style>
