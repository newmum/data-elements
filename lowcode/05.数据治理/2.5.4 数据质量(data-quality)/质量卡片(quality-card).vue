<template>
  <el-card v-for="item in list" :key="item.label" class="report-item quality-card">
    <div class="flex">
      <div
        class="report-item-icon flex items-center justify-center"
        :style="{ backgroundColor: `${item.color}20` }"
      >
        <Icon :icon="item.icon" :color="item.color" :size="34"></Icon>
      </div>
      <div class="report-item-content">
        <span class="result-item_title">{{ item.label }}</span>
        <div class="result-item_body">
          <span
            class="result-item_value"
            :style="{ color: item.type === 'error' ? '#dc2626' : undefined }"
          >
            {{ item.value }}
          </span>
          <span
            class="result-item_unit"
            :style="{ color: item.type === 'error' ? '#dc2626' : undefined }"
          >
            {{ item.unit }}
          </span>
        </div>
      </div>
    </div>
    <div class="report-item-footer flex justify-between">
      <div v-for="(value, key) in item.extra" :key="key" class="result-item_extra">
        <span>{{ `${key}：` }}</span>
        <span>{{ value }}</span>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
interface ReportItem {
  label: string;
  value: string;
  unit: string;
  icon: string;
  color: string;
  type?: string;
  extra: Record<string, any>;
}

defineProps<{
  list: ReportItem[];
}>();
</script>

<style scoped lang="scss">
.quality-card {
  position: relative;
  padding: 0 20px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 0 12px #f3f4fa;
  border-radius: 6px;

  :deep(.el-card__body) {
    padding: 0;
  }

  &:hover {
    transform: translateY(-4px);
  }

  .report-item-icon {
    width: 60px;
    height: 60px;
    margin: 15px 15px 15px 0;
    border-radius: 12px;
  }

  .report-item-content {
    flex: 1;
    margin: 18px 0 0;

    .result-item_title {
      color: #8392a5;
      font-weight: 400;
      font-size: 14px;
    }

    .result-item_body {
      padding-top: 6px;
    }

    .result-item_value {
      font-size: 30px;
      color: rgb(32, 39, 58);
      font-weight: 700;
      margin-right: 5px;
    }

    .result-item_unit {
      color: #8392a5;
      font-weight: 400;
      font-size: 14px;
    }
  }

  .report-item-footer {
    border-top: 1px dashed #e0e0e0;
  }

  .result-item_extra {
    margin: 13px 0;
    color: #8392a5;
    text-align: right;
    line-height: 18px;
    font-weight: 400;
    font-size: 12px;
  }
}
</style>
