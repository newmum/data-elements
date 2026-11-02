<template>
  <el-select
    :model-value="modelValue"
    class="datasource-type-filter"
    aria-label="选择数据源类型"
    :placeholder="statisticsError ? '类型统计暂不可用' : '请选择数据源类型'"
    clearable
    filterable
    :loading="loading"
    :disabled="disabled"
    @update:model-value="update"
  >
    <template #prefix>
      <Icon v-if="selected" :icon="selected.icon" :style="{ color: selected.color }" />
    </template>
    <el-option value="" label="全部类型">
      <span class="datasource-type-option">
        <Icon icon="database-network" style="color: #1677ff" />
        <span class="datasource-type-option__label">全部类型</span>
        <span class="datasource-type-count">{{ countText(total) }}</span>
      </span>
    </el-option>
    <el-option
      v-for="option in displayedOptions"
      :key="option.value"
      :value="option.value"
      :label="option.label"
    >
      <span class="datasource-type-option">
        <Icon :icon="option.icon" :style="{ color: option.color }" />
        <span class="datasource-type-option__label">{{ option.label }}</span>
        <span
          class="datasource-type-count"
          :class="{ 'is-zero': !statisticsLoading && !statisticsError && option.count === 0 }"
        >
          {{ countText(option.count) }}
        </span>
      </span>
    </el-option>
    <template v-if="statisticsError" #footer>
      <div class="statistics-error" role="alert">
        <span>{{ statisticsError }}</span>
        <el-button link type="primary" @click="emit('retry')">重试统计</el-button>
      </div>
    </template>
  </el-select>
</template>

<script setup lang="ts">
import { computed } from "vue";
interface TypeOption {
  value: string;
  label: string;
  icon: string;
  color: string;
  count: number;
}
const props = withDefaults(
  defineProps<{
    modelValue?: string;
    options?: TypeOption[];
    counts?: Record<string, number>;
    dbTypes?: Record<string, number>;
    total?: number;
    loading?: boolean;
    disabled?: boolean;
    statisticsLoading?: boolean;
    statisticsError?: string;
  }>(),
  { modelValue: "", counts: () => ({}), dbTypes: () => ({}), total: 0 }
);
const emit = defineEmits<{
  (event: "update:modelValue", value: string): void;
  (event: "change", value: string): void;
  (event: "retry"): void;
}>();
const names: Record<string, string> = {
  mysql: "MySQL",
  oracle: "Oracle",
  oceanbasemysql: "OceanBase MySQL",
  oceanbaseoracle: "OceanBase Oracle",
  gaussdb: "GaussDB",
  gbase8a: "GBase 8a",
  sqlserver: "SQL Server",
  hive: "Hive",
  vertica: "Vertica",
  dameng: "达梦数据库",
  postgresql: "PostgreSQL",
  kingbase8: "人大金仓",
  maxcompute: "MaxCompute",
  minio: "MinIO",
  ftp: "FTP",
  api: "API",
  kafka: "Kafka",
  elasticsearch: "Elasticsearch",
};
const modes: Record<string, { label: string; icon: string; color: string }> = {
  "mode:receive": { label: "数据推送方式", icon: "el-icon-Download", color: "#079669" },
  "mode:upload": { label: "数据上报方式", icon: "el-icon-UploadFilled", color: "#7c3aed" },
  "mode:proxy": { label: "代理访问方式", icon: "el-icon-Connection", color: "#1677ff" },
};
const normalize = (value: string) => value.toLowerCase().replace(/[\s_-]/g, "");
const displayedOptions = computed<TypeOption[]>(() => {
  if (props.options) {
    return props.options.map((option) => ({
      ...option,
      label: names[option.value] || (option.value === "other" ? "其他类型" : option.label),
    }));
  }
  const keys = new Set([
    ...Object.keys(names),
    ...Object.keys(modes),
    "other",
    ...Object.keys(props.counts),
  ]);
  return [...keys].map((value) => ({
    value,
    label:
      modes[value]?.label ||
      names[value] ||
      (value === "other"
        ? "其他类型"
        : Object.keys(props.dbTypes).find((raw) => normalize(raw) === value) || value),
    icon: modes[value]?.icon || (names[value] ? value : "database-network"),
    color: modes[value]?.color || "#1677ff",
    count: Number(props.counts[value] || 0),
  }));
});
const selected = computed(() =>
  displayedOptions.value.find((option) => option.value === props.modelValue)
);
const countText = (value: number) =>
  props.statisticsLoading
    ? "…"
    : props.statisticsError
      ? "—"
      : Number(value || 0).toLocaleString("zh-CN");
const update = (value: string | undefined) => {
  emit("update:modelValue", value || "");
  emit("change", value || "");
};
</script>

<style scoped lang="scss">
.datasource-type-filter {
  width: var(--datasource-type-filter-width, 230px);
}
.datasource-type-option {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  > :first-child {
    flex-shrink: 0;
    font-size: 18px;
  }
}
.datasource-type-option__label {
  flex: 1;
  white-space: nowrap;
}
.datasource-type-count {
  min-width: 26px;
  padding: 0 7px;
  border-radius: 10px;
  background: #ecf4ff;
  color: #175cd3;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  line-height: 20px;
  text-align: center;
  &.is-zero {
    background: #f2f4f7;
    color: #667085;
  }
}
.statistics-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: var(--el-color-danger);
  font-size: 12px;
}
</style>
