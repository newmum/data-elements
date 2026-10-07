<template>
  <v-table
    ref="tableRef"
    class="api-status"
    :options="statusTableOptions"
    :editable="!readonly"
    :show-operation="!readonly"
  />
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from "vue";

const props = defineProps<{
  enableDrag?: boolean;
  readonly?: boolean;
}>();

const tableRef = ref();

// 默认状态码（两个 Modal 完全相同）
const defaultStatusCodes = [
  { statusCode: "500", statusDesc: "服务器错误" },
  { statusCode: "400", statusDesc: "非法请求" },
  { statusCode: "401", statusDesc: "认证失败" },
  { statusCode: "403", statusDesc: "禁止，没有权限操作" },
  { statusCode: "404", statusDesc: "找不到" },
  { statusCode: "200", statusDesc: "请求成功" },
];

const statusTableOptions = reactive({
  rowConfig: props.enableDrag ? { drag: true } : {},
  toolbarConfig: {
    buttons: [
      {
        code: "append_edit",
        icon: "vxe-icon-add",
        name: "新增参数",
        status: "primary",
        mode: "text",
      },
    ],
    tools: [],
  },
  columns: [
    { field: "statusCode", title: "状态码", editRender: { name: "VxeInput" } },
    { field: "statusDesc", title: "状态描述", editRender: { name: "VxeInput" } },
  ],
  editRules: {
    statusCode: [{ required: true, content: "状态码不能为空", trigger: "blur" }],
    statusDesc: [{ required: true, content: "状态描述不能为空", trigger: "blur" }],
  },
});

// 初始化时填入默认状态码
onMounted(() => {
  tableRef.value?.setData([...defaultStatusCodes]);
});

// 暴露给父组件调用
defineExpose({
  /** 设置表格数据 */
  setData: (data: any[]) => tableRef.value?.setData(data),
  /** 获取表格数据 */
  getData: () => tableRef.value?.getData() || [],
  /** 验证表格数据 */
  validate: () => tableRef.value?.validate(),
  /** 重置为默认状态码 */
  reset: () => tableRef.value?.setData([...defaultStatusCodes]),
  /** 获取默认状态码（用于提交时比对） */
  getDefaultData: () => [...defaultStatusCodes],
});
</script>
<style scoped lang="scss">
.api-status {
  position: relative;
  :deep(.vxe-grid--toolbar-wrapper) {
    position: absolute;
    right: 0;
    top: -39px;
  }
}
</style>
