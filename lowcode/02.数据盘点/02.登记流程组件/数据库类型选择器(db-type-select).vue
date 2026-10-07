<template>
  <el-select v-model="dbType" placeholder="请选择" @change="emit('change', $event)">
    <el-option-group v-for="group in dbTypeList" :key="group.value || group.label || group.name" :label="group.label || group.name">
      <el-option
        v-for="item in group.options"
        :key="item.value"
        :label="item.label"
        :value="item.value"
      >
        <div class="flex-y-center">
          <Icon :icon="resolveIcon(item)" size="18px" class="mr-2" />
          <span>{{ item.label }}</span>
        </div>
      </el-option>
    </el-option-group>
    <template #label="{ label, value }">
      <div class="flex-y-center">
        <Icon class="mr-2" :icon="selectedIcon(value)" />
        <span>{{ label }}</span>
      </div>
    </template>
  </el-select>
</template>

<script setup lang="ts">
import { computed } from "vue";

const emit = defineEmits(["change"]);
const props = defineProps<{
  /** The form-create rule owns the selectable datasource types and icons. */
  options?: Array<{ label?: string; name?: string; value?: string; options?: Array<{ label: string; value: string; icon?: string }> }>;
}>();
const dbType = defineModel("modelValue", {
  type: String,
  required: true,
  default: "",
});

const legacyDbTypeList = [
  {
    name: "关系型数据库",
    options: [
      { label: "Mysql", value: "mysql" },
      { label: "Oracle", value: "oracle" },
      { label: "OceanBase-Mysql", value: "oceanbasemysql" },
      { label: "OceanBase-Oracle", value: "oceanbaseoracle" },
      { label: "GaussDb", value: "gaussdb" },
      { label: "Gbase8a", value: "gbase8a" },
      { label: "sqlServer", value: "sqlserver" },
      { label: "Hive", value: "hive" },
      { label: "MaxCompute", value: "maxcompute" },
      { label: "Vertica", value: "vertica" },
      { label: "Dameng", value: "dameng" },
      { label: "Postgresql", value: "postgresql" },
      { label: "Kingbase8", value: "kingbase8" },
    ],
  },
  { name: "非关系型数据库", options: [
      { label: "Minio", value: "minio" },
      { label: "Ftp数据源", value: "ftp" },
      { label: "API接口", value: "api" },
      { label: "Kafka", value: "kafka" },
    ] 
    
  },
];

const dbTypeList = computed(() =>
  Array.isArray(props.options) && props.options.length > 0 ? props.options : legacyDbTypeList
);

const iconAliases: Record<string, string> = {
  dm: "dameng",
  postgres: "postgresql",
  pg: "postgresql",
  mssql: "sqlserver",
  kingbase: "kingbase8",
};

const normalizeIcon = (value: unknown) => {
  const key = String(value || "").trim().toLowerCase().replace(/[\s_-]/g, "");
  return iconAliases[key] || key || "database-network";
};

const resolveIcon = (item?: { value?: string; icon?: string }) => normalizeIcon(item?.icon || item?.value);
const selectedIcon = (value: unknown) => {
  for (const group of dbTypeList.value) {
    const selected = group.options?.find((item) => item.value === value);
    if (selected) return resolveIcon(selected);
  }
  return normalizeIcon(value);
};
</script>

<style scoped lang="scss"></style>
