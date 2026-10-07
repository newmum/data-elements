<template>
  <div class="quality-reports card-container flex flex-col px-5 pt-5 pb-2">
    <div class="quality-reports-content">
      <!-- 1. KPI 核心看板区 -->
      <div class="kpi-section">
        <quality-card :list="kpiData" />
      </div>

      <!-- 2. 全域质量画像与对标区 -->
      <div class="quality-profile-section">
        <!-- 全球质量维度雷达图 -->
        <div class="section-quality" style="width: calc(calc(100% - 48px) / 4)">
          <div class="section-header">
            <h3>
              <Icon :icon="'el-icon-Compass'" :size="16" class="header-icon" />
              质量维度分布
            </h3>
            <el-tooltip content="质量维度分布雷达图" placement="top">
              <Icon :icon="'info'" :size="14" class="header-info" />
            </el-tooltip>
          </div>
          <ECharts :options="radarOptions" width="100%" height="200px" />
          <div class="radar-tip">
            及时性维度存在
            <span class="tip-highlight">35%</span>
            的优化空间
          </div>
        </div>

        <!-- 部门数据质量 -->
        <div class="section-quality" style="flex: 1; margin: 0 16px">
          <div class="section-header">
            <h3>
              <Icon :icon="'city'" :size="16" class="header-icon" />
              处室数据质量
            </h3>
          </div>
          <ECharts :options="deptBarOptions" class="quality-bar" width="100%" height="300px" />
        </div>

        <!-- 业务域榜单 -->
        <div class="section-quality" style="width: 32%">
          <div class="section-header">
            <h3>
              <Icon :icon="'el-icon-Histogram'" :size="16" class="header-icon" />
              主题域数据质量
            </h3>
          </div>
          <div class="domain-list">
            <div v-for="(item, index) in domainData" :key="index" class="domain-item">
              <div class="domain-name">{{ item.name }}</div>
              <div class="domain-progress">
                <div
                  class="progress-bar"
                  :class="item.score > 90 ? 'excellent' : item.score > 80 ? 'good' : 'average'"
                  :style="{ width: `${item.score}%` }"
                ></div>
              </div>
              <span class="domain-value">{{ item.score }}分</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 3. 重点监控与预警区 -->
      <div class="monitoring-section">
        <!-- 重点目录质量监控 -->
        <div class="key-catalog-monitoring section-quality">
          <div class="section-header">
            <div class="monitoring-controls">
              <h3>
                <Icon :icon="'tree'" :size="18" class="header-icon" />
                目录监控
              </h3>
              <div class="monitoring-mode">
                <button
                  :class="['mode-btn', { active: monitorMode === 'top' }]"
                  @click="monitorMode = 'top'"
                >
                  优质榜单
                </button>
                <button
                  :class="['mode-btn', { active: monitorMode === 'bottom' }]"
                  @click="monitorMode = 'bottom'"
                >
                  风险预警
                </button>
              </div>
            </div>

            <div class="search-box">
              <el-input
                v-model="searchQuery"
                placeholder="检索海洋渔业资源目录..."
                prefix-icon="search"
                class="search-input"
              />
            </div>
          </div>
          <div class="monitoring-table">
            <DataTable
              :data="filteredMonitoring"
              :columns="tableColumns"
              show-index
              class="monitoring-el-table"
              style="height: 100%"
            >
              <template #column-name="{ row }">
                <el-text type="primary" cursor-pointer @click="handleDetailClick(row, 'category')">
                  <Icon icon="category"></Icon>
                  {{ row.name }}
                </el-text>
              </template>
              <template #column-db="{ row }">
                <el-text>
                  <Icon :icon="row.dbType"></Icon>
                  {{ row.name }}
                </el-text>
              </template>
              <template #column-score="{ row }">
                <div class="score-container">
                  <span :class="['score-value', row.score > 90 ? 'high' : 'low']">
                    {{ row.score }}
                  </span>
                </div>
              </template>
            </DataTable>
          </div>
        </div>

        <!-- 质量整改进展 -->
        <div class="progress-section section-quality">
          <div class="section-header" style="height: 32px">
            <h3>
              <Icon :icon="'tool'" :size="18" :color="'#f59e0b'" />
              质量整改进展
            </h3>
            <el-tag>今日更新</el-tag>
          </div>
          <div class="progress-content">
            <ECharts ref="pieChartRef" :options="pieOptions" width="100%" height="140px" />
            <div class="progress-content-legend">
              <div
                v-for="item in progressSource"
                :key="item.name"
                class="legend-item"
                @click="handleLegendClick(item)"
              >
                <div class="item-name flex items-center">
                  <div class="dot mr-2" :style="{ backgroundColor: item.color }"></div>
                  {{ item.name }}
                </div>
                <span class="item-value">
                  {{ item.value }}
                </span>
              </div>
            </div>
            <el-alert :title="progressTip" type="warning" show-icon :closable="false">
              <template #icon>
                <Icon :icon="'el-icon-Timer'" :size="18" style="margin-top: 8px" />
              </template>
            </el-alert>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick } from "vue";
import { useRouter } from "vue-router";

const router = useRouter();
// 响应式变量
const monitorMode = ref<"top" | "bottom">("top");
const searchQuery = ref("");
const pieChartRef = ref<any>(null);

// 模拟数据：KPI核心看板
const kpiData = ref([
  {
    label: "质检目录覆盖率",
    value: "87.5",
    unit: "%",
    icon: "qualityCover",
    color: "#8269f9",
    extra: {
      质检目录数: "330",
      总目录数: "338",
    },
  },
  {
    label: "数据标准绑定率",
    value: "76.2",
    unit: "%",
    icon: "align",
    color: "#0190f9",
    extra: {
      已对标: "1980",
      总字段: "3790",
    },
  },
  {
    label: "优质资源目录数",
    value: "142",
    unit: "个",
    icon: "highDirectory",
    color: "#00B42A",
    extra: {
      总目录数: "1980",
      异常目录数: "3790",
    },
  },
  {
    label: "数据异常修复率",
    value: "91.8",
    unit: "%",
    icon: "repairRate",
    color: "#ff8e5e",
    extra: {
      已修复问题: "1980",
      未修复问题: "2167",
    },
  },
]);

// 模拟数据：质量六维度雷达图
const radarData = ref([
  { subject: "准确性", A: 85, fullMark: 100 },
  { subject: "完整性", A: 92, fullMark: 100 },
  { subject: "一致性", A: 78, fullMark: 100 },
  { subject: "有效性", A: 90, fullMark: 100 },
  { subject: "及时性", A: 65, fullMark: 100 },
  { subject: "规范性", A: 88, fullMark: 100 },
]);

// 雷达图配置选项
const radarOptions = computed(() => ({
  radar: {
    indicator: radarData.value.map((item) => ({
      name: item.subject,
      max: item.fullMark,
    })),
    center: ["50%", "50%"],
    radius: "70%",
  },
  series: [
    {
      type: "radar",
      symbol: "none",
      data: [
        {
          value: radarData.value.map((item) => item.A),
          name: "全域平均",
          itemStyle: {
            color: "#165DFF",
          },
          areaStyle: {
            color: "rgba(22, 93, 255, 0.15)",
          },
          lineStyle: {
            color: "#165DFF",
            width: 1,
          },
        },
      ],
    },
  ],
  tooltip: {
    backgroundColor: "rgba(255, 255, 255, 0.95)",
    borderColor: "#E2E8F0",
    borderWidth: 1,
    textStyle: {
      color: "#334155",
      fontSize: 12,
    },
    padding: [8, 12],
    borderRadius: 8,
  },
}));

// 模拟数据：处室质量排行
const deptData = ref([
  { name: "办公室", score: 74, issues: 48, trend: "down" },
  { name: "渔业处", score: 82, issues: 25, trend: "stable" },
  { name: "科技与信息化处", score: 88, issues: 12, trend: "down" },
  { name: "海域海岛处", score: 92, issues: 5, trend: "up" },
  { name: "执法监督处", score: 95, issues: 2, trend: "up" },
  { name: "规划财务处", score: 88, issues: 8, trend: "up" },
]);

// 部门排行柱状图配置选项
const deptBarOptions = computed(() => ({
  tooltip: {
    trigger: "axis",
    axisPointer: {
      type: "shadow",
    },
    backgroundColor: "rgba(255, 255, 255, 0.95)",
    borderColor: "#E2E8F0",
    borderWidth: 1,
    textStyle: {
      color: "#334155",
      fontSize: 12,
    },
    padding: [8, 12],
    borderRadius: 12,
    boxShadow: "0 10px 25px rgba(0,0,0,0.1)",
  },
  legend: {
    top: "0%",
    right: "3%",
    icon: "circle",
    itemWidth: 6,
    itemHeight: 6,
    itemGap: 15,
  },
  grid: {
    left: "3%",
    right: "3%",
    bottom: "0%",
    top: "16%",
    containLabel: true,
  },
  xAxis: {
    type: "value",
    show: false,
  },
  yAxis: {
    type: "category",
    data: deptData.value.map((item) => item.name),
    axisLine: {
      show: false,
    },
    axisTick: {
      show: false,
    },
    axisLabel: {
      color: "#4E5969",
      fontWeight: "bold",
      fontSize: 11,
      width: 70,
      overflow: "truncate",
    },
  },
  series: [
    {
      name: "优质目录",
      type: "bar",
      data: deptData.value.map((item) => item.score),
      barWidth: 8,
      itemStyle: {
        color: "#6193ff",
        borderRadius: [0, 4, 4, 0],
      },
    },
    {
      name: "异常目录",
      type: "bar",
      data: deptData.value.map((item) => item.issues),
      barWidth: 8,
      itemStyle: {
        color: "#ff8e5e",
        borderRadius: [0, 4, 4, 0],
      },
    },
  ],
}));

// 模拟数据：业务域质量榜单
const domainData = ref([
  { name: "渔船管理", score: 96, change: "+1.2" },
  { name: "水产养殖", score: 91, change: "+0.8" },
  { name: "海域使用管理", score: 84, change: "-2.4" },
  { name: "海洋生态监测", score: 78, change: "+0.5" },
  { name: "渔业执法监管", score: 72, change: "-1.2" },
  { name: "产业运营分析", score: 80, change: "-1.2" },
]);

// 模拟数据：目录监控
const keyCatalogData = ref([
  {
    id: 1,
    name: "渔船基础档案信息",
    db: "渔船管理库",
    dbType: "mysql",
    score: 98.5,
    issues: 0,
    core: true,
    num: "6/7",
  },
  {
    id: 2,
    name: "渔港作业运行日志数据",
    db: "基础资源库",
    score: 96.2,
    dbType: "mysql",
    issues: 2,
    core: true,
    num: "13/16",
  },
  {
    id: 3,
    name: "养殖海域备案信息",
    db: "养殖业务库",
    score: 94.0,
    issues: 5,
    dbType: "mysql",
    core: false,
    num: "10/11",
  },
  {
    id: 4,
    name: "水产品交易订单明细数据",
    db: "市场监测库",
    score: 62.4,
    issues: 156,
    dbType: "mysql",
    core: true,
    num: "1/6",
  },
  {
    id: 5,
    name: "渔业装备设备基础信息",
    db: "设备信息表",
    score: 52.4,
    issues: 125,
    dbType: "mysql",
    core: true,
    num: "1/7",
  },
  {
    id: 6,
    name: "渔业补贴发放明细",
    db: "补贴明细表",
    score: 69.4,
    issues: 126,
    dbType: "mysql",
    core: true,
    num: "1/16",
  },
  {
    id: 7,
    name: "海水养殖品类资源信息",
    db: "养殖品类表",
    score: 55.4,
    issues: 125,
    dbType: "mysql",
    core: true,
    num: "5/6",
  },
  {
    id: 8,
    name: "休渔期管控台账数据",
    db: "休渔管控表",
    score: 52.4,
    issues: 146,
    dbType: "mysql",
    core: true,
    num: "3/7",
  },
  {
    id: 9,
    name: "水产经营主体备案信息",
    db: "经营主体表",
    score: 85.6,
    issues: 123,
    dbType: "mysql",
    core: true,
    num: "5/9",
  },
  {
    id: 10,
    name: "海洋灾害故障报修记录",
    db: "故障申报记录表",
    score: 89.9,
    issues: 186,
    dbType: "mysql",
    core: true,
    num: "1/18",
  },
  {
    id: 11,
    name: "近海监测站点配置信息",
    db: "监测站点配置表",
    score: 85.6,
    issues: 144,
    dbType: "mysql",
    core: true,
    num: "1/8",
  },
  {
    id: 12,
    name: "渔业资源存量预警数据",
    db: "资源风险表",
    score: 82.4,
    issues: 156,
    dbType: "mysql",
    core: true,
    num: "1/6",
  },
]);

const tableColumns = ref([
  { prop: "name", label: "目录资源名称" },
  { prop: "db", label: "业务库" },
  { prop: "num", label: "质检字段数", align: "center", width: 150 },
  { prop: "score", label: "当前分值", align: "center" },
  { prop: "issues", label: "异常数据量", align: "center" },
  {
    prop: "operation",
    label: "操作",
    type: "buttons",
    fixed: "right",
    buttons: [
      {
        label: "质量报告",
        type: "primary",
        link: true,
        click: (row: any) => navigateToReport(row),
      },
    ],
  },
]);

// 质量整改进展
const progressSource = ref([
  { name: "待分配", value: 124, color: "#ff8e5e" },
  { name: "处理中", value: 86, color: "#10b981" },
  { name: "待复核", value: 42, color: "#54c0fb" },
  { name: "已闭环", value: 312, color: "#7f99ff" },
]);
const pieOptions = computed(() => ({
  legend: {
    show: false,
  },
  grid: {
    left: "0%",
    right: "0%",
    bottom: "0%",
    top: "0%",
  },
  series: [
    {
      name: "质量整改进展",
      type: "pie",
      radius: ["70%", "90%"],
      avoidLabelOverlap: false,
      padAngle: 5,
      itemStyle: {
        borderRadius: 0,
      },
      label: {
        show: false,
        position: "center",
      },
      emphasis: {
        label: {
          show: true,
          fontSize: 14,
          fontWeight: "bold",
          formatter(params: any) {
            return `{value|${params.value}}\n{name|${params.name}}`;
          },
          rich: {
            name: {
              fontSize: 16,
              fontWeight: "bold",
              color: "#323643",
              lineHeight: 24,
            },
            value: {
              fontSize: 20,
              fontWeight: "bold",
              color: "#323643",
              lineHeight: 28,
            },
          },
        },
      },
      labelLine: {
        show: false,
      },
      data: progressSource.value.map((item) => ({
        ...item,
        itemStyle: {
          color: item.color,
        },
      })),
    },
  ],
}));
const progressTip = ref("检测到12条海洋渔业历史异常数据因业务规则变更导致整改超时，请尽快重新核验。");

// 计算属性：过滤后的监控数据
const filteredMonitoring = computed(() => {
  const list = keyCatalogData.value.filter((item) =>
    item.name.toLowerCase().includes(searchQuery.value.toLowerCase())
  );
  if (monitorMode.value === "top") {
    return list.filter((i) => i.score >= 80).sort((a, b) => b.score - a.score);
  } else {
    return list.filter((i) => i.score < 80).sort((a, b) => a.score - b.score);
  }
});

// 导航到报告页面
const navigateToReport = (row: any) => {
  router.push({
    path: "detail",
    query: { id: row.id, type: "category", title: row.name, tab: "数据质量" },
  });
};

const handleDetailClick = (row: any, type: string) => {
  router.push({
    path: "detail",
    query: { id: row.id, type, title: row.name },
  });
};

// 处理图例卡片点击事件
const handleLegendClick = (item: any) => {
  nextTick(() => {
    if (pieChartRef.value) {
      try {
        pieChartRef.value.triggerLegendClick(item.name);
      } catch (error) {
        console.error("Error triggering legend click:", error);
      }
    } else {
      console.warn("Chart ref is not available yet");
    }
  });
};
</script>

<style scoped lang="scss">
.quality-reports {
  overflow-y: auto;
  .quality-reports-content {
    display: flex;
    flex-direction: column;
    gap: 24px;
    width: 100%;
  }

  .kpi-section {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 16px;
  }

  .quality-profile-section {
    display: flex;
    // 业务域榜单
    .domain-list {
      width: 100%;
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 28px;
      overflow-y: auto;
      margin-top: 15px;
      .domain-item {
        display: flex;
        gap: 10px;

        .domain-name {
          font-size: 11px;
          font-weight: 700;
          color: #475569;
          width: 80px;
          text-align: right;
        }

        .domain-value {
          font-size: 11px;
          font-weight: 800;
          color: #1e293b;
          width: 30px;
        }

        .domain-progress {
          height: 8px;
          background-color: #f1f5f9;
          border-radius: 2px;
          overflow: hidden;
          flex: 1;

          .progress-bar {
            height: 100%;
            border-radius: 2px;
            transition: width 1s ease-in-out;

            &.excellent {
              background-color: #10b981;
            }

            &.good {
              background-color: #6193ff;
            }

            &.average {
              background-color: #ff8e5e;
            }
          }
        }
      }
    }
  }

  .section-quality {
    background-color: #ffffff;
    box-shadow: 0 0 12px #f3f4fa;
    border-radius: 6px;
    border: 1px solid #e2e8f0;
    padding: 20px;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 16px;
    position: relative;

    .quality-bar {
      position: absolute !important;
      top: 18px;
      left: 0;
      right: 0;
    }

    .section-header {
      width: 100%;
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8px;

      h3 {
        font-size: 14px;
        font-weight: 700;
        color: #334155;
        display: flex;
        align-items: center;
        gap: 8px;
        margin: 0;
        line-height: 1;
      }

      .header-icon {
        color: #165dff;
      }

      .header-info {
        color: #cbd5e1;
        cursor: pointer;
      }
    }

    .radar-tip {
      font-size: 11px;
      font-weight: 500;
      color: #94a3b8;
      background-color: #f8fafc;
      padding: 6px 12px;
      border-radius: 20px;
      margin-top: 8px;

      .tip-highlight {
        font-weight: 700;
        color: #f77234;
      }
    }
  }

  .monitoring-section {
    display: flex;
    .key-catalog-monitoring {
      width: calc(68% - 16px);
      margin-right: 16px;
      .monitoring-controls {
        display: flex;
        align-items: center;
        gap: 25px;
      }
      .monitoring-mode {
        display: flex;
        background-color: #f3f5f8;
        border-radius: 8px;
        padding: 2px;
        gap: 2px;

        .mode-btn {
          padding: 6px 16px;
          border-radius: 6px;
          border: none;
          font-size: 11px;
          font-weight: 700;
          color: #64748b;
          background-color: transparent;
          cursor: pointer;
          transition: all 0.2s ease;

          &.active {
            background-color: #ffffff;
            color: #3b82f6;
            box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
          }

          &:hover:not(.active) {
            color: #334155;
          }
        }
      }

      .search-box {
        .search-input {
          width: 200px;

          :deep(.el-input__wrapper) {
            border-radius: 8px;
          }
        }
      }
      .monitoring-table {
        flex: 1;
        width: 100%;

        .score-container {
          display: flex;
          flex-direction: column;
          align-items: center;
          gap: 4px;

          .score-value {
            font-size: 14px;
            font-weight: 800;

            &.high {
              color: #10b981;
            }

            &.low {
              color: #ef4444;
            }
          }
        }
      }
    }

    // 预警提醒区
    .progress-section {
      width: 32%;
      .progress-content {
        flex: 1;
        width: 100%;
        :deep(.el-alert) {
          align-items: flex-start;
        }
      }
      .progress-content-legend {
        display: grid;
        grid-template-columns: repeat(2, 1fr);
        gap: 12px;
        margin: 12px 0;
        .legend-item {
          display: flex;
          align-items: center;
          justify-content: space-between;
          border: 1px solid #e2e8f0;
          background-color: #f9fafb;
          padding: 12px 12px;
          border-radius: 8px;
          cursor: pointer;
          transition: all 0.3s ease;
          &:hover {
            transform: translateY(-4px);
          }
          .dot {
            width: 5px;
            height: 5px;
            border-radius: 50%;
          }
          .item-value {
            font-size: 16px;
            font-weight: 600;
          }
        }
      }
    }
  }
}
</style>