<template>
  <div class="card-container h-auto px-5 pt-2 flex-1">
    <v-table
      ref="colRef"
      :options="tableOptions"
      show-operation
      @btn-click="({ code }) => handleAction(code)"
    >
      <template #toolbarButtons></template>
      <template #columnName="{ row }">
        <el-text>
          <Icon
            v-if="row.primaryKey === 1"
            style="color: var(--el-color-warning); transform: rotate(35deg)"
            icon="el-icon-key"
          />
          {{ row.columnName }}
        </el-text>
      </template>
      <template #columnComment="{ row }">
        <el-text>
          {{ row.columnComment || row.columnName || "" }}
        </el-text>
      </template>
      <template #columnComment_edit="{ row }">
        <el-input v-model="row.columnComment" placeholder="请输入">
          <template #prefix>
            <Icon
              v-if="row.primaryKey === 1"
              style="color: var(--el-color-warning); transform: rotate(35deg)"
              icon="el-icon-key"
            />
          </template>
        </el-input>
      </template>
      <template #primaryKey="{ row }">
        <Icon v-if="row.primaryKey === 1" icon="yes" />
      </template>
      <template #primaryKey_edit="{ row }">
        <el-checkbox v-model="row.primaryKey" true-label="1" false-label="0" />
      </template>
      <template #nullable="{ row }">
        <Icon v-if="row.nullable === 0" icon="yes" />
      </template>
      <template #nullable_edit="{ row }">
        <el-checkbox v-model="row.nullable" true-label="0" false-label="1" />
      </template>
    </v-table>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from "vue";

const props = defineProps({
  tableId: String,
});

// refs
const colRef = ref();

onMounted(() => {
  handleAction("init");
});

const handleAction = (type: string, item?: any) => {
  switch (type) {
    case "init":
      init();
      break;
  }
};

const init = async () => {
  const tableId = props.tableId;
  if (!tableId) return;

  colRef.value?.setLoading(true);
  try {
    const data = await $common.get("/dst/database/metadata/columns", { tid: tableId });
    colRef.value.setData(data || []);
  } finally {
    colRef.value?.setLoading(false);
  }
};

// 表单规则
const tableOptions = reactive({
  rowConfig: {
    drag: true,
  },
  showOverflow: true,
  height: "100%",
  toolbarConfig: {
    buttons: [
      {
        code: "append_edit", // 底部新增
        icon: "vxe-icon-add",
        name: "新增字段",
        status: "primary",
        mode: "text",
      },
      {
        code: "保存",
        icon: "vxe-icon-save",
        name: "保存",
        status: "primary",
        mode: "text",
      },
    ],
    tools: [
      {
        code: "custom",
        icon: "vxe-icon-custom-column",
        mode: "text",
        status: "primary",
        name: "显示列",
      },
      // {
      //   code: "import",
      //   icon: "vxe-icon-cloud-upload",
      //   mode: "text",
      //   status: "primary",
      //   name: "导入",
      // },
      {
        code: "open_export",
        icon: "vxe-icon-download",
        mode: "text",
        status: "primary",
        name: "导出",
      },
    ],
  },
  columns: [
    { type: "seq", width: 70 },
    {
      field: "columnName", // 可编辑-插槽渲染
      title: "字段名称",
      minWidth: 200,
      editRender: {},
      slots: { default: "columnName" },
    },
    {
      field: "columnComment", // 可编辑-插槽渲染
      title: "字段注释",
      minWidth: 200,
      editRender: {},
      slots: { default: "columnComment", edit: "columnComment_edit" },
    },
    {
      field: "dataType", // 可编辑-配置渲染
      title: "字段类型",
      width: 150,
      editRender: { name: "ElSelect", options: $dict.getDictItems("tableColType") },
    },
    {
      field: "length", // 可编辑-配置渲染
      title: "字段长度",
      editRender: { name: "ElInputNumber", props: { controlsPosition: "right" } },
    },
    {
      field: "primaryKey", // 可编辑-配置渲染
      title: "主键",
      editRender: {},
      width: 100,
      align: "center",
      slots: { default: "primaryKey", edit: "primaryKey_edit" },
    },
    {
      field: "nullable",
      title: "非空",
      editRender: {},
      width: 100,
      align: "center",
      slots: { default: "nullable", edit: "nullable_edit" },
    },
  ],
  editRules: {
    columnComment: [{ required: true, content: "字段名称不能为空", trigger: "blur" }],
    columnName: [{ required: true, content: "英文名称不能为空", trigger: "blur" }],
    dataType: [{ required: true, content: "字段类型不能为空", trigger: "blur" }],
  },
});
</script>
