<template>
  <div class="card-container h-auto px-5 pt-2 flex-1">
    <u-title name="数据项信息"></u-title>
    <v-table
      ref="colRef"
      :options="tableOptions"
      show-operation
      :show-toolbar="false"
      @btn-click="({ code }) => handleAction(code)"
    >
      <template #colName="{ row }">
        <el-text>
          <Icon
            v-if="row.isPk === '1'"
            style="color: var(--el-color-warning); transform: rotate(35deg)"
            icon="el-icon-key"
          />
          {{ row.colName }}
        </el-text>
      </template>
      <template #isPk="{ row }">
        <Icon v-if="row.isPk === '1'" icon="yes"></Icon>
      </template>
      <template #isNullable="{ row }">
        <!--    isNullable为1表示可为空，0表示不能为空    -->
        <Icon v-if="row.isNullable === '0'" icon="yes"></Icon>
      </template>
    </v-table>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from "vue";

const props = defineProps({
  fileCatalogId: String,
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
    case "保存":
      save();
      break;
  }
};

const init = async () => {
  if (!props.fileCatalogId) return;

  colRef.value?.setLoading(true);
  try {
    const data = await $common.post("/dst/catalog/catalog-items/list", {
      sortField: "sortNo",
      sortDir: "asc",
      tid: props.fileCatalogId,
    });
    colRef.value.setData(data || []);
  } catch (error) {
    console.error("获取catalog数据项失败", error);
  } finally {
    colRef.value?.setLoading(false);
  }
};

const save = async () => {
  if (!props.fileCatalogId) return;

  colRef.value?.setLoading(true);
  try {
    let catalogItems = colRef.value.getData();
    // 重新设置sortNo
    catalogItems = catalogItems.map((item, index) => ({
      ...item,
      sortNo: index + 1,
    }));
    await $common.post("/dst/catalog/saveOrUpdate", {
      tid: props.fileCatalogId,
      assetType: "catalog",
      catalogItems,
    });
    $message.success("保存成功");
  } finally {
    colRef.value?.setLoading(false);
  }
};

// 表单规则
const tableOptions = reactive({
  rowConfig: {
    drag: false,
  },
  toolbarConfig: {
    buttons: [
      {
        code: "append_edit", // 底部新增
        icon: "vxe-icon-add",
        name: "新增数据项",
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
    { type: "seq", width: 70, dragSort: true },
    {
      field: "colName", // 可编辑-插槽渲染
      title: "数据项信息",
      minWidth: 200,
      editRender: {},
      slots: { default: "colName" },
    },
    {
      field: "colEn", // 可编辑-插槽渲染
      title: "数据项英文名",
      minWidth: 200,
      editRender: { name: "VxeInput" },
    },
    {
      field: "colType", // 可编辑-配置渲染
      title: "数据项类型",
      minWidth: 200,
      editRender: { name: "ElSelect", options: $dict.getDictItems("colType"), enabled: false },
    },
    {
      field: "colLength", // 可编辑-配置渲染
      title: "长度",
      width: 120,
      editRender: { name: "ElInputNumber", enabled: false },
    },
    {
      field: "isPk", // 可编辑-配置渲染
      title: "主键",
      width: 80,
      align: "center",
      slots: { default: "isPk" },
    },
    {
      field: "isNullable", // 可编辑-配置渲染
      title: "非空",
      width: 80,
      align: "center",
      slots: { default: "isNullable" },
    },
  ],
  editRules: {
    colName: [{ required: true, content: "数据项名称不能为空", trigger: "blur" }],
    colEn: [{ required: true, content: "数据项英文名不能为空", trigger: "blur" }],
    colType: [{ required: true, content: "数据项类型不能为空", trigger: "blur" }],
  },
});
</script>
