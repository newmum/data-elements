<template>
  <div class="dj-container">
    <breadcrumb class="bg-white bread"></breadcrumb>
    <div class="main card-container py-2 px-5 flex flex-col flex-1">
      <div>
        <json-form ref="formRef" bordered :rules="state.rules" />
        <u-title name="数据资源选择" style="margin-bottom: -8px">
          <template #right>
            <el-button type="primary" icon="plus">添加待对账资源</el-button>
          </template>
        </u-title>
        <v-table ref="tableRef" :options="state.tableOptions">
          <template #provider="{ row }">
            <el-checkbox v-model="row.provider" />
          </template>
          <template #colType="{ row }">
            <el-text>
              <Icon icon="time" />
              {{ row.colType }}
            </el-text>
          </template>
          <template #action="{ row }">
            <el-button type="primary" link>对账条件</el-button>
            <el-button type="danger" link @click="tableRef.removeRow(row)">删除</el-button>
          </template>
          <template #empty>
            <empty description="暂无数据，请点击“新增”按钮添加资源" />
          </template>
        </v-table>
      </div>

      <div class="action">
        <el-button>
          <template #icon>
            <Icon icon="el-icon-back" />
          </template>
          取消并返回
        </el-button>
        <el-button type="primary">
          <template #icon>
            <Icon icon="save" />
          </template>
          发布对账策略
        </el-button>
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { FormUtils } from "@/utils/form";

const formRef = ref();
const tableRef = ref();
const state = reactive<any>({
  tab: 1,
  cols: [], // 申请单表格配置
  rules: [],
  tableOptions: {},
});

onMounted(() => {
  formRef.value.setValue({
    name: "新闻联播收视率每日对账策略",
    type: 1, // 按日循环对账
    range: ["2025-12-01", "2025-12-31"], // 对账时间区间
    diff: 1,
    remark: "该策略用于央视新闻联播每日收视率数据对账",
  });
  tableRef.value?.setData([
    {
      resEn: "tb_target_news",
      resName: "新闻联播收视率",
      time: "create_time",
      colType: "TIMESTAMP",
      provider: true,
    },
    {
      resEn: "tb_source_news",
      resName: "新闻联播收视率表",
      time: "create_time",
      colType: "TIMESTAMP",
      provider: true,
    },
  ]);
});

state.rules = FormUtils.fixJson([
  {
    type: "UTitle",
    props: {
      name: "基本信息",
    },
  },
  {
    type: "input",
    title: "策略名称",
    field: "name",
    props: {
      maxlength: 30,
      showWordLimit: true,
    },
    col: {
      span: 12,
    },
    $required: true,
  },
  {
    type: "DictSelect",
    title: "对账方式",
    field: "type",
    props: {
      options: [{ label: "单次对账", value: 1 }],
    },
    col: {
      span: 12,
    },
    $required: true,
  },
  {
    type: "date-picker",
    title: "时间区间",
    field: "range",
    props: {
      type: "daterange",
      startPlaceholder: "开始日期",
      endPlaceholder: "结束日期",
    },
    col: {
      span: 12,
    },
    $required: true,
  },
  // {
  //   type: "date-picker",
  //   title: "对账频率",
  //   field: "range",
  //   props: {
  //     type: "daterange",
  //     startPlaceholder: "开始日期",
  //     endPlaceholder: "结束日期",
  //   },
  //   col: {
  //     span: 12,
  //   },
  //   $required: true,
  // },
  {
    type: "radio",
    title: "差异分析策略",
    field: "diff",
    info: "差异分析策略",
    value: 1,
    props: {
      options: [
        { label: "无", value: 1 },
        { label: "主键差异分析", value: 2 },
        { label: "内容差异分析", value: 3 },
      ],
    },
    col: {
      span: 12,
    },
  },
  {
    type: "input",
    title: "更新时间",
    field: "updateTime",
    props: {
      disabled: true,
    },
    col: {
      span: 12,
    },
    $required: true,
    update(val, rule, api, { origin }) {
      if (origin === "init") {
        const t = new Date();
        const year = t.getFullYear();
        const month = (t.getMonth() + 1).toString().padStart(2, "0");
        const day = t.getDate().toString().padStart(2, "0");
        const hours = t.getHours().toString().padStart(2, "0");
        const minutes = t.getMinutes().toString().padStart(2, "0");
        const seconds = t.getSeconds().toString().padStart(2, "0");
        rule.value = `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
      }
    },
  },
  {
    type: "input",
    title: "备注",
    field: "remark",
    props: {
      maxlength: 50,
      showWordLimit: true,
    },
    col: {
      span: 12,
    },
  },
]);
state.tableOptions = {
  toolbarConfig: {
    buttons: [],
    tools: [],
  },
  columns: [
    { type: "seq", width: 70, field: "left" },
    {
      field: "resEn", // 可编辑-插槽渲染
      title: "资源英文名称",
      editRender: { name: "VxeInput" },
    },
    {
      field: "resName", // 可编辑-插槽渲染
      title: "资源中文名称",
      editRender: { name: "VxeInput" },
    },
    {
      field: "time", // 可编辑-插槽渲染
      title: "时间字段",
      editRender: { name: "VxeInput" },
    },
    {
      field: "colType", // 可编辑-插槽渲染
      title: "字段类型",
      editRender: {
        name: "ElSelect",
        options: [
          { label: "VARCHAR", value: "VARCHAR" },
          { label: "TEXT", value: "TEXT" },
          { label: "DATETIME", value: "DATETIME" },
          { label: "TIMESTAMP", value: "TIMESTAMP" },
          { label: "DATE", value: "DATE" },
          { label: "TINYINT", value: "TINYINT" },
          { label: "BOOLEAN", value: "BOOLEAN" },
          { label: "NUMBER", value: "NUMBER" },
          { label: "INT", value: "INT" },
          { label: "BIGINT", value: "BIGINT" },
          { label: "DECIMAL(10,2)", value: "DECIMAL(10,2)" },
        ],
      },
    },
    {
      field: "provider", // 可编辑-插槽渲染
      title: "设置提供方",
      slots: { default: "provider" },
    },
    {
      field: "action", // 可编辑-插槽渲染
      title: "操作",
      slots: { default: "action" },
    },
  ],
};
</script>

<style scoped lang="scss">
.dj-container {
  flex: 1;
  display: grid;
  grid-template-rows: 40px 1fr;
  gap: 16px;
  .bread {
    padding: 12px;
    margin: -16px -16px 0;
    background: #fff;
  }

  .main {
    flex: 1;
    position: relative;
    display: grid;
    grid-template-rows: 1fr 40px;
    gap: 1rem;
    .action {
      display: flex;
      justify-content: center;
    }
  }
}
</style>
