<template>
  <div class="card-container flex flex-col px-5 pt-5 pb-2">
    <SheetComponent
      ref="s2Ref"
      :data-cfg="s2DataConfig"
      :options="s2Options"
      sheet-type="gridAnalysis"
      :header="headerConfig"
      @data-cell-click="onDataCellClick"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, shallowRef, reactive, onMounted, h } from "vue";
import { LayoutWidthType, isUpDataValue, type SpreadSheet } from "@antv/s2";
import { SheetComponent } from "@antv/s2-vue";
interface S2DataConfig {
  fields: Record<string, any>;
  data: Record<string, any>[];
  meta?: Record<string, any>[];
}

interface S2Options {
  width: number;
  height: number;
  tooltip: {
    enable: boolean;
  };
  style: {
    layoutWidthType: LayoutWidthType;
    dataCell: {
      width: number;
      height: number;
      valuesCfg: {
        widthPercent: number[];
      };
    };
  };
  conditions: Record<string, any>;
}

// 静态数据定义
const staticData = {
  dataCfg: {
    fields: {
      rows: ["country", "city"],
      columns: ["type", "sub_type"],
      values: ["price", "cost", "rate", "growth"],
      valueInCols: true,
    },
    data: [
      {
        country: "中国",
        city: "成都",
        type: "家具",
        sub_type: "桌子",
        price: 1000,
        cost: 120,
        rate: 0.23,
        growth: 0.15,
      },
      {
        country: "中国",
        city: "成都",
        type: "家具",
        sub_type: "沙发",
        price: 2000,
        cost: 150,
        rate: 0.15,
        growth: 0.05,
      },
      {
        country: "中国",
        city: "杭州",
        type: "家具",
        sub_type: "桌子",
        price: 1500,
        cost: 140,
        rate: 0.33,
        growth: -0.12,
      },
      {
        country: "中国",
        city: "杭州",
        type: "家具",
        sub_type: "沙发",
        price: 2500,
        cost: 180,
        rate: 0.25,
        growth: 0.32,
      },
      {
        country: "中国",
        city: "西安",
        type: "家具",
        sub_type: "桌子",
        price: 1200,
        cost: 130,
        rate: 0.18,
        growth: 0.11,
      },
      {
        country: "中国",
        city: "西安",
        type: "家具",
        sub_type: "沙发",
        price: 1800,
        cost: 160,
        rate: 0.28,
        growth: 0.25,
      },
    ],
    meta: [
      {
        field: "price",
        name: "价格",
      },
      {
        field: "cost",
        name: "成本",
      },
      {
        field: "rate",
        name: "环比率",
      },
      {
        field: "growth",
        name: "环比差值",
      },
    ],
  },
  drillDownDataCfg: {
    fields: {
      rows: ["country", "city", "district"],
      columns: ["type", "sub_type"],
      values: ["price", "cost", "rate", "growth"],
      valueInCols: true,
    },
    data: [
      {
        country: "中国",
        city: "成都",
        district: "高新区",
        type: "家具",
        sub_type: "桌子",
        price: 1000,
        cost: 120,
        rate: 0.23,
        growth: 0.15,
      },
      {
        country: "中国",
        city: "成都",
        district: "锦江区",
        type: "家具",
        sub_type: "沙发",
        price: 2000,
        cost: 150,
        rate: 0.15,
        growth: 0.05,
      },
      {
        country: "中国",
        city: "杭州",
        district: "西湖区",
        type: "家具",
        sub_type: "桌子",
        price: 1500,
        cost: 140,
        rate: 0.33,
        growth: -0.12,
      },
      {
        country: "中国",
        city: "杭州",
        district: "滨江区",
        type: "家具",
        sub_type: "沙发",
        price: 2500,
        cost: 180,
        rate: 0.25,
        growth: 0.32,
      },
      {
        country: "中国",
        city: "西安",
        district: "雁塔区",
        type: "家具",
        sub_type: "桌子",
        price: 1200,
        cost: 130,
        rate: 0.18,
        growth: 0.11,
      },
      {
        country: "中国",
        city: "西安",
        district: "莲湖区",
        type: "家具",
        sub_type: "沙发",
        price: 1800,
        cost: 160,
        rate: 0.28,
        growth: 0.25,
      },
    ],
    meta: [
      {
        field: "price",
        name: "价格",
      },
      {
        field: "cost",
        name: "成本",
      },
      {
        field: "rate",
        name: "环比率",
      },
      {
        field: "growth",
        name: "环比差值",
      },
    ],
  },
};

const s2Ref = shallowRef<SpreadSheet | null>(null);
const s2DataConfig = ref<S2DataConfig>(staticData.dataCfg);
const drillDownField = ref<string>("");

const s2Options: S2Options = reactive({
  width: 1600,
  height: 600,
  tooltip: {
    enable: false,
  },
  style: {
    layoutWidthType: LayoutWidthType.ColAdaptive,
    dataCell: {
      width: 400,
      height: 100,
      valuesCfg: {
        widthPercent: [40, 20, 20, 20],
      },
    },
  },
  conditions: {
    text: [
      {
        mapping: (value: any, cellInfo: any) => {
          const { colIndex } = cellInfo;

          if (colIndex <= 1) {
            return {
              fill: "#000",
            };
          }

          return {
            fill: isUpDataValue(value) ? "#FF4D4F" : "#29A294",
          };
        },
      },
    ],
  },
});

const headerConfig = reactive({
  title: "人群网络分析",
  advancedSort: { open: true },
  extra: () => h(Breadcrumb),
});

const Breadcrumb = {
  setup() {
    const resetDrillDown = () => {
      s2DataConfig.value = staticData.dataCfg;
      drillDownField.value = "";
    };

    return () =>
      drillDownField.value
        ? h("div", { class: "antv-s2-breadcrumb" }, [
            h("span", { class: "antv-s2-breadcrumb-all", onClick: resetDrillDown }, "全部"),
            h("span", ` / ${drillDownField.value}`),
          ])
        : null;
  },
};

const DataCellTooltip = (viewMeta: any) => {
  const { spreadsheet, fieldValue } = viewMeta;

  const handleDrillDown = () => {
    s2DataConfig.value = staticData.drillDownDataCfg;
    drillDownField.value = fieldValue.label || "下钻数据";
    s2Ref.value?.hideTooltip();
  };

  const handleMergeCells = () => {
    spreadsheet.interaction.mergeCells();
    s2Ref.value?.hideTooltip();
  };

  return h("div", {}, [
    h("div", { class: "antv-s2-tooltip-operator" }, [
      h("div", { class: "antv-s2-tooltip-action", onClick: handleDrillDown }, "下钻"),
      h("div", { class: "antv-s2-tooltip-action", onClick: handleMergeCells }, "合并"),
    ]),
    h("div", { class: "antv-s2-tooltip-divider" }),
    h("div", { class: "antv-s2-tooltip-head-info-list" }, fieldValue.label || "未知"),
    h(
      "div",
      { class: "antv-s2-tooltip-detail-list" },
      (fieldValue.values || []).map((item: any, key: number) =>
        h("div", { key, class: "antv-s2-tooltip-detail-item" }, [
          h("span", { class: "antv-s2-tooltip-detail-item-key" }, item[0]),
          h(
            "span",
            { class: "antv-s2-tooltip-detail-item-val antv-s2-tooltip-highlight" },
            `${item[1]} | 环比率：${item[2]} | 环比差值：${item[3]}`
          ),
        ])
      )
    ),
    h("div", { class: "antv-s2-tooltip-infos" }, "按住 Shift 多选单元格进行人群合并"),
  ]);
};

const onDataCellClick = ({ viewMeta, event }: any) => {
  if (!viewMeta) {
    return;
  }

  const position = {
    x: event.clientX,
    y: event.clientY,
  };

  s2Ref.value?.showTooltip({
    position,
    content: h(DataCellTooltip, viewMeta),
  });
};

// 添加样式
const addStyles = () => {
  const style = document.createElement("style");
  style.innerHTML = `
    .antv-s2-tooltip-operator {
      display: flex;
    }
    .antv-s2-tooltip-action {
      width: 50%;
      text-align: center;
      cursor: pointer;
      padding: 4px 0;
    }
    .antv-s2-tooltip-action:hover {
      background-color: #f5f5f5;
    }
    .antv-s2-breadcrumb {
      position: absolute;
      left: 130px;
      top: 11px;
      font-size: 14px;
    }
    .antv-s2-breadcrumb-all {
      color: #706f6f;
      cursor: pointer;
    }
    .antv-s2-breadcrumb-all:hover {
      color: #873bf4;
    }
    .antv-s2-advanced-sort {
      display: none;
    }
    .antv-s2-header {
      margin: 0px !important;
    }
    .antv-s2-tooltip-divider {
      height: 1px;
      background: #e8e8e8;
      margin: 8px 0;
    }
    .antv-s2-tooltip-head-info-list {
      font-weight: bold;
      margin-bottom: 8px;
    }
    .antv-s2-tooltip-detail-item {
      display: flex;
      justify-content: space-between;
      margin-bottom: 4px;
    }
    .antv-s2-tooltip-detail-item-key {
      margin-right: 10px;
    }
    .antv-s2-tooltip-highlight {
      font-weight: bold;
    }
    .antv-s2-tooltip-infos {
      font-size: 12px;
      color: #999;
      margin-top: 8px;
    }
  `;
  document.head.appendChild(style);
};

onMounted(() => {
  addStyles();
});
</script>

<style lang="scss" scoped>
// SCSS样式可以在这里添加
</style>
```
