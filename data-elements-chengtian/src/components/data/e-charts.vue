<!--
 * 基于 ECharts 的 Vue3 图表组件
 * 参考：https://echarts.apache.org/handbook/zh/basics/import/#%E6%8C%89%E9%9C%80%E5%BC%95%E5%85%A5-echarts-%E5%9B%BE%E8%A1%A8%E5%92%8C%E7%BB%84%E4%BB%B6
 -->

<template>
  <div ref="chartRef" :style="{ width, height }"></div>
</template>

<script setup lang="ts">
// 引入 echarts 核心模块，核心模块提供了 echarts 使用必须要的接口。
import * as echarts from "echarts/core";
// 引入柱状、折线和饼图常用图表
import { BarChart, LineChart, PieChart, RadarChart } from "echarts/charts";
// 引入标题，提示框，直角坐标系，数据集，内置数据转换器组件，
import { GridComponent, TooltipComponent, LegendComponent } from "echarts/components";
// 引入 Canvas 渲染器，注意引入 CanvasRenderer 或者 SVGRenderer 是必须的一步
import { CanvasRenderer } from "echarts/renderers";

import { useResizeObserver } from "@vueuse/core";
import { ref, watch, onMounted, nextTick, onBeforeUnmount } from "vue";

// 按需注册组件
echarts.use([
  CanvasRenderer,
  BarChart,
  LineChart,
  PieChart,
  RadarChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
]);

const props = defineProps<{
  options: echarts.EChartsCoreOption;
  width?: string;
  height?: string;
}>();

const chartRef = ref<HTMLDivElement | null>(null);
let chartInstance: echarts.ECharts | null = null;

// 初始化图表
const initChart = () => {
  if (chartRef.value) {
    chartInstance = echarts.init(chartRef.value);
    if (props.options) {
      chartInstance.setOption(props.options);
    }
  }
};

// 监听尺寸变化，自动调整
useResizeObserver(chartRef, () => {
  chartInstance?.resize();
});

// 监听 options 变化，更新图表
watch(
  () => props.options,
  (newOptions) => {
    if (chartInstance && newOptions) {
      chartInstance.setOption(newOptions);
    }
  },
  { deep: true }
);

// 暴露方法给父组件
const emit = defineEmits<{
  (e: "chart-init", instance: echarts.ECharts): void;
}>();

// 触发图例点击事件
const triggerLegendClick = (name: string) => {
  chartInstance?.dispatchAction({
    type: "legendToggleSelect",
    name,
  });
};

// 暴露方法
defineExpose({
  triggerLegendClick,
  chartInstance,
});

onMounted(() => {
  nextTick(() => {
    initChart();
    // 图表初始化完成后，触发事件
    if (chartInstance) {
      emit("chart-init", chartInstance);
    }
  });
});

onBeforeUnmount(() => {
  chartInstance?.dispose();
});
</script>
