<template>
  <div class="card-container flex flex-col px-5 pt-5 pb-2">
    <div id="container" ref="containerRef"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from "vue";
import { PivotSheet } from "@antv/s2";

const containerRef = ref(null);
let s2Instance = null;

onMounted(async () => {
  try {
    // 获取数据
    const response = await fetch(
      "https://gw.alipayobjects.com/os/bmw-prod/2a5dbbc8-d0a7-4d02-b7c9-34f6ca63cff6.json"
    );
    const dataCfg = await response.json();

    // 配置数据元数据
    const s2DataConfig = {
      ...dataCfg,
      meta: [
        {
          field: "province",
          name: "省份",
        },
        {
          field: "city",
          name: "城市",
        },
        {
          field: "type",
          name: "商品类别",
        },
        {
          field: "sub_type",
          name: "子类别",
        },
        {
          field: "number",
          name: "数量",
          // 自定义格式化
          // formatter: (value, record, meta) => {
          //   return `${value / 100} %`;
          // },
        },
      ],
    };

    // 配置选项
    const s2Options = {
      width: 600,
      height: 480,
      hierarchyType: "grid",
      // 数值挂行头时, 自定义角头虚拟数值字段文本, 默认 "数值"
      cornerExtraFieldText: "自定义",
      interaction: {
        copy: {
          enable: true,
          withFormat: true,
          withHeader: true,
        },
      },
      // 显示序号
      // seriesNumber: {
      //   enable: true,
      //   自定义序号列文本, 默认 "序号"
      //   text: '自定义序号标题',
      // },
      frozen: {
        // 默认冻结行头, 行头和数值区域都会展示滚动条
        // rowHeader: false,
        // 冻结行头时, 行头宽度占表格的 1/2, 支持动态调整 (0 - 1)
        // rowHeader: 0.2,
      },
    };

    // 创建 PivotSheet 实例
    s2Instance = new PivotSheet(containerRef.value, s2DataConfig, s2Options);

    // 渲染表格
    await s2Instance.render();
  } catch (error) {
    console.error("渲染透视表失败:", error);
  }
});

onBeforeUnmount(() => {
  // 销毁实例
  if (s2Instance) {
    s2Instance.destroy();
    s2Instance = null;
  }
});
</script>

<style scoped></style>
