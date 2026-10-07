<template>
  <el-drawer
    v-model="open"
    :title="drawerTitle"
    size="950"
    :body-class="'access-apply'"
    :close-on-click-modal="false"
    @close="handleCancel"
  >
    <div class="access-apply-content">
      <u-title name="建表信息">
        <template #right>
          <el-button
            type="primary"
            :icon="'el-icon-CopyDocument'"
            link
            plain
            @click="handleShowDDL"
          >
            查看DDL创建语句
          </el-button>
        </template>
      </u-title>
      <JsonForm
        ref="jsonFormRef"
        v-model="formData"
        :rules="formRules"
        bordered
        class="mb-4"
      ></JsonForm>
      <v-table
        ref="tableRef"
        :options="tableColumns"
        :editable="editable"
        :show-operation="editable"
        class="api-param-table"
        @btn-click="handleTableBtnClick"
      >
        <template #primaryKey="{ row }">
          <el-checkbox
            :model-value="row.primaryKey === 1"
            :disabled="!editable"
            @update:model-value="(val) => (row.primaryKey = val ? 1 : 0)"
          />
        </template>
        <template #nullable="{ row }">
          <el-checkbox
            :model-value="row.nullable === 1"
            :disabled="!editable"
            @update:model-value="(val) => (row.nullable = val ? 1 : 0)"
          />
        </template>
      </v-table>
    </div>

    <template #footer>
      <div class="flex" style="justify-content: flex-end">
        <el-button v-if="editable" type="primary" :disabled="isSaveing" @click="handleSave">
          <Icon icon="save" class="mr-1" />
          提交
        </el-button>

        <el-button :icon="'el-icon-Close'" @click="handleCancel">关闭</el-button>
      </div>
    </template>
  </el-drawer>

  <!-- DDL创建语句弹窗 -->
  <el-drawer
    v-model="ddlDialogVisible"
    v-if="ddlDialogVisible"
    title="查看创建语句"
    size="700"
    class="ddl-preview-drawer"
    :close-on-click-modal="false"
  >
    <div class="ddl-preview-content">
      <div class="ddl-preview-editor">
        <CodeEditor
          v-model="ddlSql"
          lang="sql"
          theme="chrome"
          :height="'100%'"
          :show-option="false"
          :read-only="true"
        />
      </div>
    </div>
    <template #footer>
      <div class="flex" style="justify-content: flex-end">
        <el-button :icon="'el-icon-CopyDocument'" type="primary" @click="handleCopyDDL">
          一键复制
        </el-button>
        <el-button :icon="'el-icon-Close'" @click="ddlDialogVisible = false">关闭</el-button>
      </div>
    </template>
  </el-drawer>

  <!-- 默认字段弹窗 -->
  <el-drawer
    v-model="defaultFieldsVisible"
    title="默认字段添加"
    size="700"
    :close-on-click-modal="false"
  >
    <DataTable
      ref="defaultFieldsTableRef"
      :columns="defaultFieldsColumns"
      :data="defaultFieldsData"
      :show-selection="true"
      :show-page="false"
      :flex-type="'flex-[0_1_auto]'"
      :rowKey="'columnName'"
      @selection-change="handleSelectionChange"
    />
    <template #footer>
      <div class="flex justify-end">
        <el-button type="primary" @click="handleConfirmDefaultFields">确定</el-button>
        <el-button @click="defaultFieldsVisible = false">取消</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from "vue";
import { FormUtils } from "@/utils/form";
import { ElMessage } from "element-plus";
import { useDictStore } from "@/store";

// Props
const props = defineProps<{
  modelValue: boolean;
  type: string; // add/view
  id: string;
}>();

const emit = defineEmits<{
  (e: "close"): void;
  (e: "save"): void;
  (e: "update:modelValue", value: boolean): void;
}>();

const colTypeOptions = useDictStore().getDictItems("tableColType");
const formData = ref<any>({});
const jsonFormRef = ref<any>(null);
const drawerTitle = ref("接入申请");
const ddlDialogVisible = ref(false);
const defaultFieldsVisible = ref(false);
const ddlSql = ref("");
const isSaveing = ref(false);

// 默认字段数据
const defaultFieldsData = ref([
  {
    columnComment: "创建时间",
    columnName: "create_time",
    length: 20,
    dataType: "datetime",
    columnType: "datetime",
    autoIncrement: 0,
    charset: null,
    collation: null,
    defaultValue: null,
    extra: "",
    indexed: 1,
    isUnique: 1,
    nullable: 0,
    ordinalPosition: 1,
    precisionLength: 10,
    primaryKey: 0,
    scale: 0,
  },
  {
    columnComment: "更新时间",
    columnName: "update_time",
    length: 20,
    dataType: "datetime",
    columnType: "datetime",
    autoIncrement: 0,
    charset: null,
    collation: null,
    defaultValue: null,
    extra: "",
    indexed: 1,
    isUnique: 1,
    nullable: 0,
    ordinalPosition: 1,
    precisionLength: 10,
    primaryKey: 0,
    scale: 0,
  },
  {
    columnComment: "创建人",
    columnName: "created_by",
    length: 64,
    dataType: "varchar",
    columnType: "varchar",
    autoIncrement: 0,
    charset: null,
    collation: null,
    defaultValue: null,
    extra: "",
    indexed: 1,
    isUnique: 1,
    nullable: 0,
    ordinalPosition: 1,
    precisionLength: 10,
    primaryKey: 0,
    scale: 0,
  },
  {
    columnComment: "更新人",
    columnName: "updated_by",
    length: 64,
    dataType: "varchar",
    columnType: "varchar",
    autoIncrement: 0,
    charset: null,
    collation: null,
    defaultValue: null,
    extra: "",
    indexed: 1,
    isUnique: 1,
    nullable: 0,
    ordinalPosition: 1,
    precisionLength: 10,
    primaryKey: 0,
    scale: 0,
  },
  {
    columnComment: "备注",
    columnName: "remark",
    length: 500,
    dataType: "varchar",
    columnType: "varchar",
    autoIncrement: 0,
    charset: null,
    collation: null,
    defaultValue: null,
    extra: "",
    indexed: 1,
    isUnique: 1,
    nullable: 0,
    ordinalPosition: 1,
    precisionLength: 10,
    primaryKey: 0,
    scale: 0,
  },
  {
    columnComment: "数据状态",
    columnName: "data_status",
    length: 32,
    dataType: "int",
    columnType: "int",
    autoIncrement: 0,
    charset: null,
    collation: null,
    defaultValue: null,
    extra: "",
    indexed: 1,
    isUnique: 1,
    nullable: 0,
    ordinalPosition: 1,
    precisionLength: 10,
    primaryKey: 0,
    scale: 0,
  },
  {
    columnComment: "删除标志",
    columnName: "is_del",
    length: 1,
    dataType: "int",
    columnType: "int",
    autoIncrement: 0,
    charset: null,
    collation: null,
    defaultValue: null,
    extra: "",
    indexed: 1,
    isUnique: 1,
    nullable: 0,
    ordinalPosition: 1,
    precisionLength: 10,
    primaryKey: 0,
    scale: 0,
  },
]);
const editable = computed(() => props.type === "add");
const selectedDefaultFields = ref<any[]>([]);
const defaultFieldsTableRef = ref();
const formRules = FormUtils.fixJson([
  {
    field: "dbType",
    title: "目标库类型",
    type: "db-type-select",
    $required: true,
    col: {
      span: 12,
    },
    props: {
      disabled: !editable.value,
    },
  },
  {
    field: "dbId",
    title: "目标数据库",
    type: "select",
    $required: true,
    col: {
      span: 12,
    },
    props: {
      disabled: !editable.value,
    },
  },
  {
    field: "tableName",
    title: "目标表名",
    type: "input",
    $required: true,
    col: {
      span: 12,
    },
    props: {
      maxlength: 255,
      disabled: !editable.value,
    },
  },
  {
    field: "tableNameCn",
    title: "表注释",
    type: "input",
    col: {
      span: 12,
    },
    props: {
      maxlength: 255,
      disabled: !editable.value,
    },
  },
]);
const tableRef = ref();
const tableColumns = ref({
  columns: [
    { type: "seq", width: 70 },
    {
      title: "字段名",
      field: "columnName",
      editRender: {
        name: "VxeInput",
        props: {
          maxlength: 255,
        },
      },
      showOverflow: true,
    },
    {
      title: "业务备注",
      field: "columnComment",
      editRender: {
        name: "VxeInput",
        props: {
          maxlength: 255,
        },
      },
      showOverflow: true,
    },
    {
      title: "数据类型",
      field: "dataType",
      editRender: { name: "ElSelect", options: colTypeOptions },
    },
    {
      title: "主键",
      field: "primaryKey",
      width: 80,
      slots: { default: "primaryKey" },
    },
    {
      title: "不为空",
      field: "nullable",
      width: 80,
      slots: { default: "nullable" },
    },
    {
      title: "长度",
      field: "length",
      width: 100,
      editRender: {
        name: "VxeInput",
        props: {
          type: "number",
          min: 0,
        },
      },
    },
  ],
  editRules: {
    columnName: [{ required: true, message: "字段名不能为空", trigger: "blur" }],
    dataType: [{ required: true, message: "数据类型不能为空", trigger: "change" }],
    length: [
      { required: true, message: "长度不能为空", trigger: "blur" },
      {
        validator: ({ cellValue }: any) => {
          if (cellValue === "" || cellValue === null || cellValue === undefined) {
            return true;
          }
          const num = Number(cellValue);
          return !isNaN(num) && num >= 0;
        },
        message: "长度必须为正数",
        trigger: "blur",
      },
    ],
  },
  toolbarConfig: {
    buttons: [
      {
        code: "append_edit", // 底部新增
        icon: "vxe-icon-add",
        name: "新增数据项",
        status: "primary",
        className: "btn-primary",
      },
      {
        code: "default_field",
        icon: "vxe-icon-setting",
        name: "默认字段",
        status: "primary",
        className: "btn-plain",
      },
    ],
    tools: [],
  },
});
// 默认字段表格列配置
const defaultFieldsColumns = ref([
  { prop: "columnName", label: "字段名", minWidth: 180 },
  { prop: "columnComment", label: "字段注释", minWidth: 150 },
  { prop: "length", label: "长度", width: 100 },
  { prop: "dataType", label: "数据类型", options: "colTypeOptions" },
]);
const open = computed({
  get: () => props.modelValue,
  set: (val: boolean) => emit("update:modelValue", val),
});

const init = () => {
  $common
    .post("/dst/catalog/list", {
      assetType: "db",
    })
    .then((res: any) => {
      const options = res.map((item: any) => ({
        label: item.database,
        value: item.id,
      }));
      jsonFormRef.value?.updateFieldOptions("dbId", options);
    });
  const addUrl = "/ods/getTableTempalte";
  const viewUrl = "/ods/queryTargetTableInfo";
  $common
    .post(props.type === "add" ? addUrl : viewUrl, {
      tid: props.id,
    })
    .then((res: any) => {
      // 设置表单数据(表信息)
      jsonFormRef.value?.setValue(res.propList);
      // 设置表格数据(字段列表)
      if (res.tableItems?.length) {
        tableRef.value?.setData(res.tableItems);
      }
    });
};

const handleCopyDDL = () => {
  if (ddlSql.value) {
    $common.copyText(ddlSql.value);
  } else {
    ElMessage.warning("没有可复制的内容");
  }
};

const handleCancel = () => {
  emit("close");
  open.value = false;
};

const handleShowDDL = async () => {
  await jsonFormRef.value?.validate();
  ddlDialogVisible.value = true;
  const propList = await jsonFormRef.value?.getFormData();
  const tableItems = tableRef.value?.getData() || [];
  $common
    .post("/dst/database/metadata/getCreateTableDDL", {
      propList,
      tableItems,
    })
    .then((res: any) => {
      ddlSql.value = res;
    });
};

const handleSave = async () => {
  try {
    await jsonFormRef.value?.validate();
    await tableRef.value?.validate();
    isSaveing.value = true;

    const tableFields = tableRef.value?.getData();
    const formData = await jsonFormRef.value?.getFormData();
    await $common.post("/ods/createTapleApply", {
      propList: formData,
      tableItems: tableFields,
      tid: props.id,
    });
    ElMessage.success("提交成功");
    emit("save");
    isSaveing.value = false;
  } catch (error: any) {
    isSaveing.value = false;
    ElMessage.error(error.message || "提交失败");
  }
};

// Handle table button click
const handleTableBtnClick = async (params: any) => {
  const { code } = params;

  switch (code) {
    case "default_field":
      // 打开默认字段弹窗
      handleOpenDefaultFieldsDrawer();
      break;
  }
};

// 打开默认字段弹窗，同步选中状态
const handleOpenDefaultFieldsDrawer = () => {
  // 获取当前表格中的所有字段
  const currentFields = tableRef.value?.getData() || [];

  // 提取已存在的字段名
  const existingFieldNames = new Set(currentFields.map((field: any) => field.columnName));

  // 根据现有字段同步选中默认字段
  const fieldsToSelect = defaultFieldsData.value.filter((item) =>
    existingFieldNames.has(item.columnName)
  );

  // 打开弹窗
  defaultFieldsVisible.value = true;

  // 等待弹窗打开后，设置表格的选中行
  setTimeout(() => {
    if (defaultFieldsTableRef.value) {
      const table = defaultFieldsTableRef.value.$table;
      if (table) {
        // 先清除所有选中
        table.clearSelection();
        // 设置选中行
        fieldsToSelect.forEach((row) => {
          table.toggleRowSelection(row, true);
        });
      }
    }
  }, 100);
};

// 处理选中变化
const handleSelectionChange = (selection: any[]) => {
  selectedDefaultFields.value = selection;
};

// 确认添加默认字段
const handleConfirmDefaultFields = () => {
  if (selectedDefaultFields.value.length === 0) {
    ElMessage.warning("请至少选择一个字段");
    return;
  }

  // 获取当前表格中的所有字段
  const currentFields = tableRef.value?.getData() || [];
  const existingFieldNames = new Set(currentFields.map((field: any) => field.columnName));

  // 将选中的默认字段转换为表格所需的格式
  const newFields = selectedDefaultFields.value.filter(
    (item) => !existingFieldNames.has(item.columnName)
  );

  if (newFields.length === 0) {
    ElMessage.info("所选字段已全部存在于表格中");
    defaultFieldsVisible.value = false;
    return;
  }

  // 将字段添加到表格末尾
  newFields.forEach((field) => {
    tableRef.value?.$grid?.insertAt(field, -1);
  });

  ElMessage.success(
    `已添加 ${newFields.length} 个默认字段${
      selectedDefaultFields.value.length > newFields.length
        ? `（${selectedDefaultFields.value.length - newFields.length} 个已存在）`
        : ""
    }`
  );
  defaultFieldsVisible.value = false;
};

onMounted(() => {
  if (props.id) {
    init();
  }
});
</script>

<style lang="scss" scoped>
.access-apply {
  .access-apply-content {
    :deep(.vxe-toolbar .vxe-button--item-wrapper) {
      justify-content: end;
    }
    :deep(.btn-plain) {
      background-color: #fff !important;
      color: #1b67f8;
      border: 1px solid #8db3fc;
      &:hover {
        color: #4985f9 !important;
        border-color: #4985f9 !important;
        outline: none;
      }
    }
    :deep(.btn-primary) {
      &:hover {
        background-color: #5f95fa !important;
        border-color: #5f95fa !important;
        outline: none;
      }
    }
  }
}

// DDL drawer uses the remaining body height all the way down to the Ace editor.
:global(.ddl-preview-drawer .el-drawer__body) {
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

:global(.ddl-preview-drawer .ddl-preview-content) {
  min-height: 0;
  height: 100%;
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
}

:global(.ddl-preview-drawer .ddl-preview-editor) {
  min-height: 0;
  width: 100%;
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
}

:global(.ddl-preview-drawer .ddl-preview-editor > .el-scrollbar) {
  min-height: 0;
  height: 100%;
  width: 100%;
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
}

:global(.ddl-preview-drawer .ddl-preview-editor .el-scrollbar__wrap),
:global(.ddl-preview-drawer .ddl-preview-editor .el-scrollbar__view) {
  min-height: 0;
  height: 100%;
  flex: 1 1 auto;
}

:global(.ddl-preview-drawer .ddl-preview-editor .el-scrollbar__view) {
  display: flex;
  flex-direction: column;
}

:global(.ddl-preview-drawer .ddl-preview-editor .ace_editor) {
  min-height: 0 !important;
  height: 100% !important;
  flex: 1 1 auto;
}
</style>
