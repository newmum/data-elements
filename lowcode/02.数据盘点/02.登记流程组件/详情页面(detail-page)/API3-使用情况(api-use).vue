<template>
  <div class="api-use card-container flex px-5 pt-5 flex-col">
    <div class="part1">
      <u-title name="今日概况"></u-title>
      <el-row :gutter="20" class="first-row">
        <el-col :span="8" class="first-col">
          <div class="grid-content">
            <div class="document-img" style="background: #5ad8a6">
              <div class="img images-server-apicount3"></div>
            </div>
            <div class="document-xms">
              <div class="img-fen-title">今日调用次数</div>
              <div class="img-fen-num">
                <el-statistic title="" :value="detailData.dayRequestCount || 0" />
                <span style="font-size: 14px">次</span>
              </div>
              <div class="img-fen-total">
                累计调用次数：
                {{ detailData.requestCount || 0 }}
                次
              </div>
            </div>
          </div>
        </el-col>
        <el-col :span="8" class="first-col">
          <div class="grid-content">
            <div class="document-img" style="background: #5b8ff9">
              <div class="images-server-apicount2 img"></div>
            </div>
            <div class="document-xms">
              <div class="img-fen-title">平均访问时长</div>
              <div class="img-fen-num">{{ detailData.dayResponseTime || 0 }}ms</div>
              <div class="img-fen-total">
                累计平均访问时长：{{ detailData.responseTime || 0 }}ms
              </div>
            </div>
          </div>
        </el-col>
        <el-col :span="8" class="first-col">
          <div class="grid-content">
            <div class="document-img" style="background: #f6bd16">
              <div class="images-server-apicount1 img"></div>
            </div>
            <div class="document-xms">
              <div class="img-fen-title">错误率</div>
              <div class="img-fen-num">
                {{ Math.round(detailData.dayErrorPercent * 100) / 100 }}%
              </div>
              <div class="img-fen-total">
                累计错误率：{{ Math.round(detailData.errorPercent * 100) / 100 }}%
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>
    <div class="part1">
      <el-row>
        <el-col :span="8">
          <u-title name="调用统计"></u-title>
        </el-col>
      </el-row>
      <el-row>
        <el-col :span="24">
          <el-space
            v-show="!countTimeRange || countTimeRange.length == 0"
            style="float: right; margin-right: 15px"
          >
            <el-radio-group v-model="radio" :options="plainOptions" @change="onChange" />
            <el-radio-group v-model="day" class="ml-5" @change="changeRadio">
              <el-radio-button value="day">日</el-radio-button>
              <el-radio-button value="week">周</el-radio-button>
              <el-radio-button value="month">月</el-radio-button>
              <el-radio-button value="year">年</el-radio-button>
              <!--                <el-radio value="e">累计</el-radio>-->
            </el-radio-group>
          </el-space>
        </el-col>
        <el-col :span="10"></el-col>
      </el-row>
      <el-row
        v-if="countTimeRange && countTimeRange.length > 0"
        style="margin-top: 0px"
        class="first-row"
      >
        <el-col class="first-col" :span="7">
          <div class="grid-content" style="width: 303px">
            <div class="document-img" style="background: #5ad8a6">
              <div class="images-server-apicount3 img"></div>
            </div>
            <div class="document-xms">
              <div class="img-fen-title">请求次数</div>
              <div class="img-fen-num" style="margin-top: 10px">
                <el-statistic title="" :value="requestCount || 0" />
                次
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
      <el-row v-else>
        <el-col v-loading="chartLoading" :span="24">
          <empty v-if="chartError" description="调用统计接口异常" style="margin-top: 10px" />
          <ECharts
            v-else
            :options="chartOptions"
            height="300px"
            style="width: 100%; margin-top: 10px"
          />
        </el-col>
      </el-row>
    </div>
    <div class="part1" style="padding-right: 20px">
      <el-row>
        <el-col :span="8">
          <u-title name="调用明细"></u-title>
        </el-col>
        <el-col :span="16">
          <div style="float: right; display: flex">
            <el-space>
              <span>调用日期：</span>
              <el-date-picker
                v-model="times"
                type="datetimerange"
                style="width: 450px"
                @change="changeDate"
              />
            </el-space>
          </div>
        </el-col>
      </el-row>
      <DataTable
        ref="tableRef"
        :columns="tableColumns"
        :data="fetchData"
        show-index
        :show-pagination-total="false"
      ></DataTable>
    </div>

    <!-- 调用明细查看弹窗 -->
    <UModal
      v-model="detailVisible"
      :title="detailFormData.serviceName || '' + '详情'"
      width="900px"
      :show-footer="false"
      :close-on-click-modal="false"
      :draggable="false"
      @close="handleDetailClose"
    >
      <el-scrollbar v-loading="detailLoading" style="height: 500px">
        <JsonForm
          v-if="!detailLoading && logDetailData.id"
          :key="logDetailData.id"
          :rules="detailRules"
          :data="detailFormData"
          bordered
          preview
          label-width="160px"
        />
        <empty
          v-if="!detailLoading && detailError"
          description="获取调用详情失败，请稍后重试"
          style="height: 500px"
        />
      </el-scrollbar>
    </UModal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { FormUtils } from "@/utils/form";

const route = useRoute();
const apiId = ref((route.query.id as string) || "");
const tableRef = ref<any>(null);
const day = ref("day"); // 统计维度: day/week/month/year
const times = ref(""); // 选择时间
const tableColumns = ref([
  {
    label: "服务名",
    prop: "serviceName",
  },
  {
    label: "请求时间",
    prop: "startTime",
  },
  {
    label: "请求方法",
    prop: "method",
  },
  {
    label: "状态码",
    prop: "status",
  },
  {
    label: "访问时长",
    prop: "elapsedTime",
    formatter: (row: any, column: any, cellValue: number) => {
      if (cellValue === null || cellValue === undefined) {
        return "0ms";
      }
      return cellValue + "ms";
    },
  },
  // {
  //   label: "调用部门",
  //   prop: "departmentName",
  // },
  {
    label: "调用应用",
    prop: "applicationName",
  },
  {
    prop: "operation",
    label: "操作",
    width: 120,
    buttons: [
      {
        label: "查看",
        type: "primary",
        link: true,
        click: (row: any) => {
          handleEdit(row, "detail");
        },
      },
    ],
  },
]);
const xAxis = ref([]); //x轴值
const yUnit = ref("（次）"); //x轴值
const plainOptions = ref([
  {
    label: "调用次数",
    value: "调用次数",
  },
  {
    label: "错误率",
    value: "错误率",
  },
  {
    label: "平均访问时长",
    value: "平均访问时长",
  },
]);
const radio = ref("调用次数");
const detailData = ref({
  requestCount: 0, //累计调用次数
  responseTime: 0, //累计平均响应时长，单位ms
  errorPercent: 0, //累计错误率，单位%
  dayRequestCount: 0, //今日调用次数
  dayResponseTime: 0, //今日平均响应时长，单位ms
  dayErrorPercent: 0, //今日错误率，单位%
});
const countTimeRange = ref([]);
const requestCount = ref(0);
const chartOptions = ref({});
const chartError = ref(false);
const chartLoading = ref(false);

/** 获取服务调用概况 */
const fetchOverview = async () => {
  try {
    const res = await $common.post("/dws/gateway/apiCallOverview", {
      apiId: apiId.value,
    });
    if (res) {
      detailData.value = {
        requestCount: res.requestCount ?? 0,
        responseTime: res.responseTime ?? 0,
        errorPercent: res.errorPercent ?? 0,
        dayRequestCount: res.dayRequestCount ?? 0,
        dayResponseTime: res.dayResponseTime ?? 0,
        dayErrorPercent: res.dayErrorPercent ?? 0,
      };
    }
  } catch (e) {
    console.error("获取服务调用概况失败", e);
  }
};

onMounted(() => {
  fetchOverview();
  changeRadio();
  // initTable();
});

// ========== 调用明细查看弹窗 ==========
const detailVisible = ref(false);
const detailLoading = ref(false);
const detailError = ref(false);
const logDetailData = ref<Record<string, any>>({});

/** 详情表单规则（含 UTitle 分段标题） */
const detailRules = ref<any[]>([]);
detailRules.value = FormUtils.transFormText([
  { type: "UTitle", props: { name: "环节1: 请求信息" } },
  { type: "input", field: "appName", title: "应用名称", col: { span: 12 } },
  { type: "input", field: "reqId", title: "请求ID", col: { span: 12 } },
  { type: "input", field: "startTime", title: "请求开始时间", col: { span: 12 } },
  { type: "input", field: "elapsed", title: "执行时长(ms)", col: { span: 12 } },
  { type: "input", field: "clientIp", title: "应用IP", col: { span: 12 } },
  { type: "input", field: "callerUri", title: "调用方URI", col: { span: 12 } },
  { type: "UTitle", props: { name: "环节2: 网关执行结果" } },
  { type: "input", field: "gatewayResult", title: "网关执行结果", col: { span: 12 } },
  { type: "input", field: "errorCode", title: "错误码", col: { span: 12 } },
  { type: "input", field: "errorMessage", title: "错误信息", col: { span: 24 } },
  { type: "UTitle", props: { name: "环节3: 服务方返回信息" } },
  { type: "input", field: "serviceName", title: "服务名称", col: { span: 12 } },
  { type: "input", field: "svcStartTime", title: "请求开始时间", col: { span: 12 } },
  { type: "input", field: "svcElapsed", title: "执行时长(ms)", col: { span: 12 } },
  { type: "input", field: "beforeForwardTime", title: "转发前执行时长(ms)", col: { span: 12 } },
  { type: "input", field: "afterForwardTime", title: "转发后执行时长(ms)", col: { span: 12 } },
  { type: "input", field: "providerIp", title: "提供方IP", col: { span: 12 } },
  { type: "input", field: "providerMethod", title: "提供方请求的方式", col: { span: 12 } },
  { type: "input", field: "providerStatus", title: "提供方响应状态", col: { span: 12 } },
  { type: "input", field: "svcReqId", title: "请求ID", col: { span: 12 } },
]);

/** 详情表单数据 */
const detailFormData = ref<Record<string, any>>({});

/** 获取调用明细详情 */
const fetchLogDetail = async (requestId: string) => {
  detailLoading.value = true;
  detailError.value = false;
  try {
    const res = await $common.post("/dws/gateway/proxy", {
      action: "V2ServiceLogDetail",
      requestId,
    });
    const data = res.data || res;

    if (data.resultCode) {
      ElMessage.error(data.errorMessage || "获取调用明细详情失败");
      detailError.value = true;
      return;
    }

    // 网关执行结果映射
    const isSuccess = data.status == 0 || data.status == 200;

    // 组装表单数据
    detailFormData.value = {
      appName: data.applicationName,
      reqId: data.requestId,
      startTime: data.startTime,
      elapsed: data.elapsedTime,
      clientIp: data.ip,
      callerUri: data.uri,
      gatewayResult: isSuccess ? "成功" : "失败",
      errorCode: data.errorCode,
      errorMessage: data.errorMessage,
      serviceName: data.serviceName,
      svcStartTime: data.startTime,
      svcElapsed: data.elapsedTime,
      beforeForwardTime: data.beforeForwardTime,
      afterForwardTime: data.afterForwardTime,
      providerIp: data.ip,
      providerMethod: data.method,
      providerStatus: data.status,
      svcReqId: data.requestId,
    };

    logDetailData.value = data;
  } catch (e) {
    detailError.value = true;
    console.error("获取调用明细详情失败", e);
  } finally {
    detailLoading.value = false;
  }
};

/** 关闭弹窗 */
const handleDetailClose = () => {
  detailVisible.value = false;
  detailError.value = false;
  logDetailData.value = {};
};

const handleEdit = (row: any, type: string) => {
  if (type === "detail") {
    detailVisible.value = true;
    const reqId = row.requestId || row.request_id || "";
    if (reqId) {
      fetchLogDetail(reqId);
    }
  }
};

const onChange = () => {
  if (radio.value == "调用次数") {
    yUnit.value = "（次）";
  } else if (radio.value == "错误率") {
    yUnit.value = "（%）";
  } else if (radio.value == "平均访问时长") {
    yUnit.value = "（毫秒）";
  }
  getChart();
};

const changeRadio = () => {
  getChart();
};
/** 统计类型映射：radio 值 → API type 参数 */
const typeMap: Record<string, string> = {
  调用次数: "request_count",
  错误率: "error_rate",
  平均访问时长: "avg_elapsed_time",
};

const getChart = async () => {
  chartLoading.value = true;
  chartError.value = false;
  try {
    const res = await $common.post("/dws/gateway/callStatistics", {
      apiId: apiId.value,
      dimension: day.value,
      type: typeMap[radio.value] || "request_count",
    });

    const list: (string | number)[] = [];
    const xAxisData: string[] = [];
    if (res && Array.isArray(res)) {
      res.forEach((item: { date: string; value: number }) => {
        xAxisData.push(item.date);
        list.push(item.value);
      });
    }

    xAxis.value = xAxisData;

    chartOptions.value = {
      grid: {
        left: 20,
        right: 25,
        top: 50,
        bottom: 10,
        containLabel: true,
      },
      tooltip: {
        trigger: "axis",
      },
      xAxis: {
        type: "category",
        data: xAxis.value,
        boundaryGap: true,
        axisLabel: {
          interval: day.value == "month" ? 1 : 0,
        },
        axisTick: {
          alignWithLabel: true,
        },
      },
      yAxis: {
        type: "value",
        name: yUnit.value,
        minInterval: radio.value === "调用次数" ? 1 : 0.1,
      },
      series: [
        {
          data: list,
          type: "line",
          smooth: true,
          symbolSize: 6,
          symbol: "circle",
          itemStyle: {
            color: "#5b8ff9",
          },
          lineStyle: {
            color: "#5b8ff9",
            width: 3,
          },
          areaStyle: {
            color: {
              type: "linear",
              x: 0,
              y: 0,
              x2: 0,
              y2: 1,
              colorStops: [
                {
                  offset: 0,
                  color: "rgb(205,221,252)",
                },
                {
                  offset: 1,
                  color: "rgb(255,255,255)",
                },
              ],
            },
          },
        },
      ],
    };
  } catch (e) {
    chartError.value = true;
    console.error("获取调用统计趋势失败", e);
  } finally {
    chartLoading.value = false;
  }
};

const changeDate = () => {
  tableRef.value.refresh(false);
};
const fetchData = async ({ pageNo, pageSize }: { pageNo: number; pageSize: number }) => {
  const response = await $common.post("/dws/gateway/proxy", {
    action: "V2ServiceLogList",
    apiId: apiId.value,
    beginTime: times.value?.length === 2 ? times.value[0].getTime() : "",
    endTime: times.value?.length === 2 ? times.value[1].getTime() : "",
    pageIndex: pageNo,
    pageSize,
  });
  const data = response.data || response;
  const list = data.items || [];
  const total = data.totalSize || 0;
  return {
    list,
    total,
  };
};
</script>

<style scoped lang="scss">
.api-use {
  overflow: auto;
}

.main-title {
  font-size: 16px;
  font-weight: bold;
  //margin: 0 30px;
}
.part1 {
  //background: #f8f8fa;
  padding: 0 0 22px 0px;
}
.part1:first-child {
  padding-top: 0;
}
.first-row {
  height: 107px;
  width: 100%;
  padding-left: 10px;
  //padding: 0 10px 0 30px;
  .first-col {
    height: 100%;
    .grid-content {
      background: #ffffff;
      height: 100%;
      display: flex;
      box-shadow: 0 0 15px 0 #e1e1e1;
      //border: 1px solid #1890ff;
    }
    .document-img {
      width: 70px;
      height: 70px;
      margin: 15px 0 15px 15px;
      .img {
        width: 42px;
        height: 42px;
        margin: 14px;
        background-size: 100% 100%;
      }
    }
    .document-xms {
      font-size: 14px;
      margin-top: 13px;
      width: 57%;
      margin-left: 20px;
      .img-fen-title {
        color: #999999;
        font-size: 14px;
      }
      .img-fen-num {
        font-size: 24px;
        font-weight: bold;
        display: flex;
        align-items: baseline;
        line-height: 1.5751;
      }
      .img-fen-total {
        font-size: 12px;
        color: #999999;
        display: flex;
      }
      .main-title {
        font-size: 15px;
        font-weight: 600;
      }
    }
  }
}
</style>
