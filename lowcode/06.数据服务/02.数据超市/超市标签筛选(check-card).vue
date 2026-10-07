<template>
  <div class="check-card">
    <div v-if="hasSelected" class="checked-tags">
      <div class="checked-label">已选条件：</div>
      <div class="tag-list">
        <template v-for="(tags, key) in checkedFilter" :key="key">
          <el-tag
            v-for="el in tags"
            :key="el.value"
            closable
            disable-transitions
            @close="clearTag(String(key), el)"
          >
            <span class="tag-label">{{ el.label }}</span>
          </el-tag>
        </template>
      </div>
      <el-button type="primary" link @click="clearTagAll">
        <Icon icon="el-icon-delete" />
        清空
      </el-button>
    </div>

    <div class="check-filters">
      <template v-for="(condition, idx) in props.filters" :key="condition.title">
        <div
          v-if="condition.options && condition.options.length > 0"
          class="filter-row"
          :class="{ 'row-expanded-top': expanded[condition.title] && idx === 0 }"
        >
          <check-tags
            v-model="filterValues[condition.title]"
            :title="condition.title"
            :options="condition.options"
            @change="(val) => onFilterChange(condition.title, val)"
          />
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { defineProps, defineEmits, reactive, computed, watch, onMounted } from "vue";

type Option = { label: string; value: string };
type Condition = { title: string; options: Option[] };

const props = defineProps<{ filters: Condition[] }>();
const emit = defineEmits<{
  (e: "tag-close", tag: string): void;
  (e: "clear-tags"): void;
  (e: "change", type: string | number, value: any): void;
}>();

const filterValues = defineModel("modelValue", {
  type: String,
  required: true,
  default: {},
});
const expanded = reactive<Record<string, boolean>>({});

const initFilterValues = () => {
  props.filters?.forEach((c) => {
    if (!Array.isArray(filterValues.value[c.title])) filterValues.value[c.title] = [];
    if (expanded[c.title] === undefined) expanded[c.title] = false;
  });
};
onMounted(initFilterValues);
watch(
  () => props.filters,
  () => initFilterValues(),
  { deep: true }
);

const checkedFilter = computed(() => {
  const map: Record<string, { label: string; value: string }[]> = {};
  props.filters?.forEach((con) => {
    const key = con.title;
    map[key] = [];
    const selected = filterValues.value[key] || [];
    selected.forEach((val) => {
      const found = con.options.find((o) => JSON.stringify(o.value) === JSON.stringify(val));
      if (found) map[key].push({ label: found.label, value: val });
    });
  });
  return map;
});

const hasSelected = computed(() =>
  Object.values(checkedFilter.value).some((arr) => arr.length > 0)
);

const onFilterChange = (type: string, vals: any[]) => {
  emit("change", type, vals);
};

const clearTag = (key: string, el: { label: string; value: string }) => {
  const vals = filterValues.value[key] || [];
  filterValues.value[key] = vals.filter((v) => v !== el.value);
  emit("tag-close", el.value);
  emit("change", key, filterValues.value[key]);
};

const clearTagAll = () => {
  Object.keys(filterValues.value).forEach((k) => (filterValues.value[k] = []));
  emit("clear-tags");
  emit("change");
};

defineExpose({ clearTagAll });
</script>

<style scoped lang="scss">
.check-card {
  padding: 11px 24px;
  background-color: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  font-family:
    Source Han Sans CN,
    sans-serif;
  line-height: 40px;

  .filter-row {
    display: grid;
    grid-template-columns: minmax(85px, auto) 1fr auto;
    gap: 0;
    align-items: center;
    margin-bottom: 2px;
  }
  .filter-title {
    flex-shrink: 0;
    min-width: 85px;
    font-size: 14px;
    color: #686c80;
  }
}

.checked-tags {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 16px;
  align-items: center;
  padding-bottom: 12px;
  margin-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;

  .checked-label {
    font-family: "Source Han Sans CN";
    font-size: 14px;
    font-weight: 400;
    color: #686c80;
  }
  .tag-list {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;

    :deep(.el-tag) {
      height: 30px;
      padding: 4px 10px;
      font-size: 14px;
      color: var(--el-color-primary);
      background-color: rgba(240, 245, 255, 1);
      border: none;
      border-radius: 8px;
    }
    :deep(.el-tag .el-tag__close) {
      margin-left: 6px;
    }
    :deep(.el-tag__close) {
      background-color: transparent;
      color: var(--el-color-primary);
    }
  }
  :deep(.el-button .el-icon) {
    margin-right: 4px;
  }
}

.check-filters {
  :deep(.el-checkbox-group) {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
  }
  .group-single {
    display: flex;
    flex-wrap: nowrap;
    align-items: center;
    overflow: hidden;
    white-space: nowrap;
  }
  .group-multi {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    overflow: visible;
    white-space: normal;
  }
  .row-expanded-top {
    align-items: flex-start;
  }
  .row-expanded-top .filter-title {
    text-align: left;
  }
  .row-expanded-top :deep(.el-checkbox-group) {
    justify-content: flex-start;
  }
  :deep(.el-checkbox-button) {
    margin: 0;
  }
  :deep(.el-checkbox-button__inner) {
    padding: 4px 12px;
    line-height: 20px;
    color: #606266;
    background: transparent;
    border: none;
    border-radius: 4px;
    box-shadow: none;
  }
  :deep(.el-checkbox-button.is-checked .el-checkbox-button__inner) {
    color: var(--el-color-primary);
    background-color: rgba(240, 245, 255, 1);
    border: none;
    box-shadow: none;
    border-radius: 8px;
  }
  .filter-action {
    line-height: 28px;
  }
}

:deep(.el-checkbox-button) {
  margin-right: 8px;
  margin-bottom: 8px;
}
</style>
