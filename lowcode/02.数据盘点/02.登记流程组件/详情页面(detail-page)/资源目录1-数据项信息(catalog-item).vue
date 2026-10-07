<template>
  <div class="card-container h-auto px-5 pt-2 flex-1">
    <v-table
      ref="colRef"
      :options="tableOptions"
      show-operation
      :editable="props.editable"
      @btn-click="({ code }) => handleAction(code)"
    >
      <template #toolbarButtons></template>
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
      <template #colName_edit="{ row }">
        <el-input v-model="row.colName" placeholder="请输入">
          <template #prefix>
            <Icon
              v-if="row.isPk === '1'"
              style="color: var(--el-color-warning); transform: rotate(35deg)"
              icon="el-icon-key"
            />
          </template>
        </el-input>
      </template>
      <template #colType_default="{ row }">
        <dict-label v-model="row.colType" options="colType"></dict-label>
      </template>
      <template #colType_edit="{ row }">
        <dict-select v-model="row.colType" options="colType"></dict-select>
      </template>
      <template #targetCol_edit="{ row }">
        <el-input v-model="row.targetCol" placeholder="对标字段英文"></el-input>
      </template>
      <template #dataStandardId="{ row }">
        <dict-label
          :model-value="row.dataStandardId"
          options="dataStandard"
          empty-text=""
        ></dict-label>
      </template>
      <template #dataStandardId_edit="{ row }">
        <dict-select
          v-model="row.dataStandardId"
          options="dataStandard"
          placeholder="请选择数据标准"
        ></dict-select>
      </template>
      <template #qualityRule="{ row }">
        <div class="flex-center gap-2">
          <dict-select
            v-model="row.qualityRule"
            options="qualityRule"
            placeholder="默认空白"
            multiple
            collapse-tags
            collapse-tags-tooltip
            :max-collapse-tags="1"
            tag-type="primary"
            multiple-to-str
          ></dict-select>
        </div>
      </template>
      <template #codeTableId="{ row }">
        <dict-label v-model="row.codeTableId" options="codeTable"></dict-label>
      </template>
      <template #codeTableId_edit="{ row }">
        <div class="flex-x-between gap-1">
          <!--          <el-checkbox v-model="row.enableCodeTable" :label="row.enableCodeTable ? '' : '未启用'" />-->
          <dict-select v-model="row.codeTableId" options="codeTable"></dict-select>
        </div>
      </template>
    </v-table>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive, watch } from "vue";

const props = defineProps<{
  catalogId: string;
  editable?: boolean;
}>();

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
  if (!props.catalogId) return;

  colRef.value?.setLoading(true);
  try {
    const data = await $common.post("/dst/catalog/catalog-items/list", {
      sortField: "sortNo",
      sortDir: "asc",
      tid: props.catalogId,
    });
    colRef.value.setData(data || []);
  } catch (error) {
    console.error("获取catalog数据项失败", error);
  } finally {
    colRef.value?.setLoading(false);
  }
};

const save = async () => {
  if (!props.catalogId) return;

  colRef.value?.setLoading(true);
  try {
    let catalogItems = colRef.value.getData();
    // 重新设置sortNo
    catalogItems = catalogItems.map((item, index) => ({
      ...item,
      sortNo: index + 1,
    }));
    await $common.post("/dst/catalog/saveOrUpdate", {
      tid: props.catalogId,
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
    drag: true,
  },
  showOverflow: true,
  height: "100%",
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
        code: "import",
        icon: "vxe-icon-cloud-upload",
        mode: "text",
        status: "primary",
        name: "导入",
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
      slots: { default: "colName", edit: "colName_edit" },
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
      editRender: {},
      slots: { default: "colType_default", edit: "colType_edit" },
    },
    {
      field: "dataStandardId", // 可编辑-配置渲染
      title: "数据标准",
      width: 180,
      editRender: {},
      slots: { default: "dataStandardId", edit: "dataStandardId_edit" },
    },
    // {
    //   field: "qualityRule", // 可编辑-配置渲染
    //   title: "质检规则",
    //   slots: { default: "qualityRule" },
    //   width: 200,
    // },
    {
      field: "codeTableId",
      title: "码表转换",
      slots: { default: "codeTableId", edit: "codeTableId_edit" },
      width: 200,
    },
  ],
  editRules: {
    colName: [{ required: true, content: "数据项名称不能为空", trigger: "blur" }],
    colEn: [{ required: true, content: "数据项英文名不能为空", trigger: "blur" }],
    colType: [{ required: true, content: "数据项类型不能为空", trigger: "blur" }],
  },
});
</script>
