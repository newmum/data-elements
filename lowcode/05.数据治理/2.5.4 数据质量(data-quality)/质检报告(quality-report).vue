<template>
  <div class="quality-report card-container flex flex-col px-5 pt-5 pb-2">
    <template v-if="categoryId == '1'">
      <quality-empty></quality-empty>
    </template>
    <template v-else>
      <div class="quality-report-list">
        <quality-card :list="list" />
      </div>
      <u-title name="质检规则执行明细" class="quality-report-detail">
        <template #default>
          <div class="flex items-center ml-2">
            <Icon icon="el-icon-timer" :color="'#8392a5'"></Icon>
            <span class="quality-report-time">{{ qualityTime }}</span>
          </div>
        </template>
        <template #right>
          <el-button type="danger" plain>
            <Icon icon="info" class="mr-2"></Icon>
            查看异常数据
          </el-button>
          <el-button type="primary" @click="handlerQuality">
            <Icon icon="shandian" class="mr-2"></Icon>
            去质检
          </el-button>
        </template>
      </u-title>
      <DataTable :columns="columns" :data="tableData" show-index class="flex-1">
        <template #column-dataItemCode="{ row }">
          <el-tag type="info" style="color: #8392a5">{{ row.dataItemCode }}</el-tag>
        </template>
        <template #column-rule="{ row }">
          <template v-if="row.rule">
            <el-tag v-for="item in row.rule.split(',')" :key="item" class="mr-2">
              {{ item }}
            </el-tag>
          </template>
        </template>
      </DataTable>
      <register-modal
        v-if="registerModalVisible"
        v-model="registerModalVisible"
        :register-class="'quality'"
        :quality-data="qualityData"
        @close="closeRegisterModal"
      ></register-modal>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from "vue";
import { useRoute } from "vue-router";
// import QualityCard from "@/views/quality-card.vue";

// 获取路由实例
const route = useRoute();

const categoryId = ref("");
const qualityTime = ref("2025-08-01 10:00:00");

// 初始化时获取路由参数
const initCategoryId = () => {
  categoryId.value = route.query?.categoryId || route.query?.id || "";
};

const qualityData = ref({
  name: "qualitycheck",
  title: "创建成功",
  code: "TSK_QUALITY_001",
  time: "2025-08-01 16:11:00",
  type: "quality",
});
const list = ref([
  {
    label: "异常字段数",
    value: "2",
    unit: "个",
    icon: "field2",
    color: "#fda937",
    extra: {
      总字段: 9,
      未质检: 7,
    },
  },
  {
    label: "异常数据量",
    value: "1600",
    unit: "条",
    icon: "error",
    type: "error",
    color: "#f56c6c",
    extra: {
      总数据量: 3000,
      异常率: "56%",
    },
  },
  {
    label: "字段对标率",
    value: "80",
    unit: "%",
    icon: "align",
    color: "#0190f9",
    extra: {
      对标数: 9,
      未对标数: 1,
    },
  },
  {
    label: "质检规则数",
    value: "6",
    unit: "个",
    icon: "rule",
    color: "#8269f9",
    extra: {
      字段级: 5,
      表级: 1,
    },
  },
]);
const columns = ref([
  {
    prop: "dataItemName",
    label: "数据项中文名",
  },
  {
    prop: "dataItemCode",
    label: "数据项英文名",
  },
  {
    prop: "dimension",
    label: "指标维度",
    options: "qualityDimension",
  },
  {
    prop: "rule",
    label: "指标规则",
  },
  {
    prop: "checkMethod",
    label: "检查方式",
  },
  {
    prop: "checkTotal",
    label: "检查总量",
    formatter: (row: any, column: any, cellValue: number) => {
      if (cellValue === null || cellValue === undefined) {
        return "0";
      }
      return cellValue.toLocaleString();
    },
  },
  {
    prop: "checkException",
    label: "异常量",
  },
  {
    prop: "checkExceptionRate",
    label: "异常率",
  },
]);
const tableData = ref([
  {
    dataItemName: "客户名称",
    dataItemCode: "customer_name",
    dimension: "0",
    rule: "非空校验,空字符串检查,长度检查",
    checkMethod: "全量",
    checkTotal: 1000,
    checkException: 100,
    checkExceptionRate: "10%",
  },
  {
    dataItemName: "内容摘要",
    dataItemCode: "content_summary",
    dimension: "0",
    rule: "空字符串检查",
    checkMethod: "全量",
    checkTotal: 3000,
    checkException: 0,
    checkExceptionRate: "0%",
  },
  {
    dataItemName: "备案号",
    dataItemCode: "record_number",
    dimension: "1",
    rule: "长度检查",
    checkMethod: "增量",
    checkTotal: 1200,
    checkException: 110,
    checkExceptionRate: "5%",
  },
  {
    dataItemName: "审核日期",
    dataItemCode: "approval_date",
    dimension: "1",
    rule: "日期格式检查",
    checkMethod: "增量",
    checkTotal: 1540,
    checkException: 90,
    checkExceptionRate: "6.5%",
  },
  {
    dataItemName: "电视剧标识",
    dataItemCode: "drama_id",
    dimension: "3",
    rule: "主键唯一性",
    checkMethod: "增量",
    checkTotal: 4640,
    checkException: 80,
    checkExceptionRate: "16.5%",
  },
]);
const registerModalVisible = ref(false);

const closeRegisterModal = () => {
  registerModalVisible.value = false;
};
const handlerQuality = () => {
  registerModalVisible.value = true;
};

// 监听路由变化
watch(
  () => route.query,
  () => {
    initCategoryId();
  },
  { deep: true }
);

onMounted(() => {
  initCategoryId();
});
</script>

<style scoped lang="scss">
.quality-report {
  // width: 100%;
  .quality-report-list {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 20px;
    margin-bottom: 10px;
    .report-item {
      position: relative;
      padding: 0 20px;
      display: flex;
      flex-direction: column;
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
  }

  .quality-report-title {
    font-weight: 700;
    margin: 0;
  }
  .quality-report-time {
    color: #8392a5;
    font-weight: 400;
    font-size: 12px;
    height: 10px;
  }

  .quality-report-card {
    box-shadow: 0 0 12px #f3f4fa;
    border-radius: 6px;
  }
  .quality-report-detail {
    :deep(.toolbar) {
      align-items: center;
    }
  }
}
</style>
