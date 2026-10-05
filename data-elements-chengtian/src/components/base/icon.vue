<template>
  <!-- svg图标 element plus 图标 -->
  <el-icon v-if="normalizedIcon" :size="size" :title="title" :color="color">
    <component :is="iconComponent" v-if="isElIcon" />
    <div v-else :class="`i-svg:${normalizedIcon}`" class="icon" />
  </el-icon>
</template>

<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    icon?: string;
    title?: string;
    size?: string | number;
    color?: string;
  }>(),
  {
    size: "16px",
  }
);

const isElIcon = computed(() => props.icon?.startsWith("el-icon"));
const iconComponent = computed(() => props.icon?.replace("el-icon-", ""));
const normalizedIcon = computed(() => {
  const raw = String(props.icon || "").trim();
  if (!raw || raw.startsWith("el-icon")) return raw;
  const key = raw.toLowerCase().replace(/[\s_-]/g, "");
  const aliases: Record<string, string> = {
    pg: "postgresql",
    postgres: "postgresql",
    postgresql: "postgresql",
    mssql: "sqlserver",
    sqlserver: "sqlserver",
    dm: "dameng",
    dameng: "dameng",
    kingbase: "kingbase8",
    kingbasees: "kingbase8",
    kingbase8: "kingbase8",
    oceanbasemysql: "oceanbasemysql",
    oceanbaseoracle: "oceanbaseoracle",
    es: "elasticsearch",
    elastic: "elasticsearch",
    elasticsearch: "elasticsearch",
    other: "database-network",
  };
  return aliases[key] || raw;
});
</script>

<style lang="scss" scoped>
.icon {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 1em;
  height: 1em;
  font-size: inherit;
  vertical-align: -0.2em;
  color: currentcolor;
}
</style>
