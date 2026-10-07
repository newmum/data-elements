<template>

</template>

<script setup>
/*
系统内置组件
表格：data-table
页签：u-tabs
列表：page-list
动态表单： json-form
字典选择器("select", "radio", "checkbox")： dict-select
面包屑： breadcrumb
复制按钮： copy-button
图表： ECharts
空： Empty
文本： FormText
图标： Icon
富文本： WangEditor
时间轴：TimeLine 

系统内置方法
状态管理 import { useUserStore, useDictStore } from "@/store";
请求 $request({})
提示 $messgae.success('...') 等同ElMessage
提示 $dialog.confirm("是否确认提交?", "提示", { type: "warning" }) 等同ElMessageBox
*/
</script>

<style>
:root {
  --el-border-radius-base: 6px;
  --el-card-border-radius: 8px;
  --el-text-color-regular: #323643;
  --el-color-error: #ff4d4f;
  --el-color-warning: #ffa224;
  --el-dialog-padding-primary: 20px 24px !important;
  --el-border-radius-small: 4px;
  --el-font-weight-primary: 400px;
  --el-color-primary-light-9: rgba(27, 103, 248, 0.06) !important; 
  --el-mask-color: rgba(255, 255, 255, 0.4) !important;
}

.bg-cover {
  background-size: cover;
}

/* el-card */
.el-card {
  --el-card-border-radius: 8px !important;
}

/* el-table */
.el-table thead {
  font-family:
    Source Han Sans SC,
    PingFang,
    sans-serif;
  font-size: var(--el-font-size-base);
  border-radius: 8px !important;
  --el-table-header-text-color: #587aa8;
  --el-table-header-bg-color: #f4f7fc;
}

// 按钮
.el-button--primary.is-plain {
  --el-button-hover-bg-color: transparent !important;
  --el-button-bg-color: transparent !important;
  --el-button-border-color: var(--el-color-primary) !important;
  --el-button-hover-text-color: var(--el-color-primary) !important;
}
.el-button--primary.is-plain:hover {
  --el-button-hover-border-color: var(--el-color-primary-light-2) !important;
  --el-button-hover-text-color: var(--el-color-primary-light-2) !important;
}
.el-button--small span{
  line-height: 13px;
}
// 页签
.el-tabs :deep(.el-tabs__nav-wrap:after ){
  height: 1px;
}

// vxe icon 图标样式
[class*=vxe-icon]:before {
  position: relative;
  top: 2px;
}

.vxe-grid .vxe-grid--table-wrapper {
  border-radius: 8px 8px 0 0;
}
.el-table table>thead>tr:first-child >*:first-child {
    border-start-start-radius: 8px;
}
.el-table table>thead>tr:first-child >*:last-child {
  border-start-end-radius: 8px;
}
.overflow-y-auto {
  overflow-y: auto
}
.max-h-60 {
  max-height: 240px;
}
</style>