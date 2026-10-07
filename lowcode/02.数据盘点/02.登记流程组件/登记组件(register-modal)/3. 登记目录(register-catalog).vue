<template>
  <div class="catalog-register-layout">
    <LeftSelect
      v-model="currentDid"
      title="数据目录列表"
      icon="catalog"
      :list="leftList"
      :field-name="{ title: 'catalogName' }"
      @add="(item) => handleAction('add', item)"
      @delete="(item) => handleAction('delete', item)"
      @select="(item) => handleAction('select', item)"
    />

    <div class="catalog-register-main">
      <el-alert
        v-if="loadError"
        class="catalog-register-alert"
        type="warning"
        show-icon
        :closable="false"
        :title="loadError"
      />

      <div v-if="currentDid" class="catalog-register-content" :aria-busy="loading || undefined">
        <LoadingState
          v-if="loading"
          type="table"
          compact
          class="catalog-register-loading"
          title="正在读取目录和字段信息"
        />
        <div :class="['catalog-register-fields', { 'is-loading': loading }]">
          <JsonForm ref="formRef" class="catalog-register-form" bordered :rules="formRules">
            <template #toolbar>
              <el-popconfirm
                title="给未填写的必填项填充默认值？"
                confirm-button-text="确认"
                cancel-button-text="取消"
                @confirm="handleFillDefaults"
              >
                <template #reference>
                  <el-button plain>
                    <template #icon><Icon icon="apply1" /></template>
                    快捷填充
                  </el-button>
                </template>
              </el-popconfirm>
            </template>
          </JsonForm>
          <v-table ref="colRef" :options="tableOptions" show-operation editable>
          <template #toolbarButtons>
            <u-title name="数据项" style="padding: 4px 0">
              <div class="flex gap-2">
                <el-button
                  type="primary"
                  plain
                  class="ml-2"
                  icon="aim"
                  @click="handleAction('智能对标')"
                >
                  智能对标
                </el-button>
                <el-progress
                  v-if="percentage.show"
                  class="w-400px"
                  striped
                  :stroke-width="10"
                  :percentage="percentage.value"
                />
              </div>
            </u-title>
          </template>
          <template #colName_default="{ row }">
            <el-input v-model="row.colName" placeholder="请输入">
              <template #prefix>
                <Icon
                  v-if="row.isPk"
                  style="color: var(--el-color-warning); transform: rotate(35deg)"
                  icon="el-icon-key"
                />
              </template>
            </el-input>
            <div><el-text type="info">{{ row.colEn }}</el-text></div>
          </template>
          <template #colType_default="{ row }">
            <dict-label v-model="row.colType" options="colType" />
          </template>
          <template #colType_edit="{ row }">
            <dict-select v-model="row.colType" options="colType" />
          </template>
          <template #dataStandardId="{ row }">
            <dict-label :model-value="row.dataStandardId" options="dataStandard" />
          </template>
          <template #dataStandardId_edit="{ row }">
            <dict-select
              v-model="row.dataStandardId"
              options="dataStandard"
              placeholder="请选择数据标准"
            />
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
              />
            </div>
          </template>
          <template #codeTableId="{ row }">
            <div class="flex-x-between gap-1">
              <el-checkbox v-model="row.enableCodeTable" :label="''" />
              <dict-select
                v-if="row.enableCodeTable"
                v-model="row.codeTableId"
                options="codeTable"
              />
            </div>
          </template>
          </v-table>
        </div>
      </div>

      <div v-else class="catalog-register-empty">
        <Empty />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, nextTick, onMounted } from "vue";
import { useRegisterStore } from "@/store";
import { get, isString, set } from "lodash-es";

const loading = ref(false);
const loadError = ref("");
const formRef = ref();
const colRef = ref();
const formRules = ref([]);
const store = useRegisterStore();
const currentDid = ref("");
const formReady = ref(false);
const catalogLoadKey = ref("");
const percentage = reactive({ value: 0, show: false });

const app = computed(() => get(store.data, "app", {}));
const catalogs = computed({
  get: () => {
    if (!get(store.data, "catalog")) set(store.data, "catalog", []);
    return get(store.data, "catalog", []);
  },
  set: (val) => set(store.data, "catalog", val),
});
const tables = computed({
  get: () => {
    if (!get(store.data, "table")) set(store.data, "table", []);
    return get(store.data, "table", []);
  },
  set: (val) => set(store.data, "table", val),
});
const currentCatalog = computed(() =>
  catalogs.value.find((item) => item.did === currentDid.value) || {}
);
const leftList = computed(() => catalogs.value || []);

const normalizeCatalogs = () => {
  const normalized = (catalogs.value || []).filter(Boolean).map((item) => ({
    ...item,
    did: item.did || item.tid || $common.uuid(),
  }));
  catalogs.value = normalized;
  return normalized;
};

const initData = computed(() => {
  const value: Record<string, any> = {
    orgId: $user.orgId,
    manageUnit: $user.orgId,
    concatName: $user.realName,
    concatPhone: $user.phone,
  };
  if (app.value.tid) value.appId = app.value.tid;
  return value;
});

const getTableCols = async (catalog: Record<string, any>) => {
  const sourceTable = tables.value.find(
    (row) => (row.tid || row.tableId) === catalog.sourceTableId
  );
  const params = {
    tid: catalog.sourceTableId,
    dbId: catalog.dbId || sourceTable?.dbId || sourceTable?.datasourceId,
    tableName:
      catalog.sourceTableName ||
      sourceTable?.tableName ||
      sourceTable?.tableNameEn,
  };
  try {
    return (await $common.get("/dst/database/metadata/columns", params)) || [];
  } catch (error: any) {
    $message.warning(error?.message || "数据表字段加载失败");
    return [];
  }
};

const toCatalogItems = (columns: any[] = []) =>
  columns.map((column, index) => ({
    colName: column.columnComment || column.columnNameCn || column.columnName || `字段${index + 1}`,
    colEn: column.columnName || column.columnNameEn || "",
    colType: column.columnType || column.dataType || "varchar",
    colLength: Number(column.length || column.columnLength || column.precisionLength || 0) || undefined,
    isPk: column.primaryKey ? "1" : "0",
    isNullable: column.nullable === false || column.nullable === 0 || column.nullable === "0" ? "0" : "1",
    sourceTableColumnId: column.tid || column.id || column.columnId || "",
    enableCodeTable: 0,
  }));

const detailApi = (tid: string) =>
  Promise.all([
    $common.post("/dst/catalog/detail", { tid }),
    $common.post("/dst/catalog/catalog-items/list", { tid }),
  ]);

const applyCatalogItems = async (rows: any[] = []) => {
  await nextTick();
  colRef.value?.setData?.(rows);
  await colRef.value?.loadData?.(rows);
};

const selectCatalog = async (item: any) => {
  if (!item) return;
  if (!item.did) item.did = item.tid || $common.uuid();
  currentDid.value = item.did;
  if (!formReady.value) return;
  const loadKey = `${item.did}:${item.tid || "new"}`;
  if (loading.value && catalogLoadKey.value === loadKey) return;
  catalogLoadKey.value = loadKey;
  formRef.value?.resetFields?.();
  loadError.value = "";

  loading.value = true;
  try {
    await nextTick();
    if (item.tid) {
      const [data, catalogItems] = await detailApi(item.tid);
      await nextTick();
      formRef.value?.setValue?.(data || {});
      await applyCatalogItems(catalogItems || []);
      return;
    }

    const catalog = catalogs.value.find((row) => row.did === item.did) || {};
    formRef.value?.setValue?.({ ...catalog, ...initData.value });
    await applyCatalogItems(catalog.catalogItems || []);
    if (!catalog.sourceTableId) return;
    if (catalog.catalogItems?.length) return;

    const columns = await getTableCols(catalog);
    const catalogItems = toCatalogItems(columns);
    catalog.catalogItems = catalogItems;
    await applyCatalogItems(catalogItems);
  } catch (error: any) {
    loadError.value = error?.message || "生成目录数据项失败，请稍后重试";
    await applyCatalogItems([]);
  } finally {
    if (catalogLoadKey.value === loadKey) loading.value = false;
  }
};

const handleAction = (type: string, item?: any) => {
  switch (type) {
    case "init": {
      const list = normalizeCatalogs();
      if (!list.length) {
        const catalog = { did: $common.uuid(), catalogName: "待登记目录" };
        catalogs.value.push(catalog);
        currentDid.value = catalog.did;
      } else {
        const current = list.find((row) => row.did === currentDid.value) || list[0];
        currentDid.value = current.did;
      }
      break;
    }
    case "add": {
      const catalog = { did: $common.uuid(), catalogName: "待登记目录" };
      catalogs.value.push(catalog);
      currentDid.value = catalog.did;
      nextTick(() => selectCatalog(catalog));
      break;
    }
    case "delete":
      catalogs.value = catalogs.value.filter((row) => row.did !== item.did);
      tables.value = tables.value.filter((row) => item.sourceTableId !== row.tid);
      if (currentDid.value === item.did) {
        const next = catalogs.value[0];
        currentDid.value = next?.did || "";
        if (next) nextTick(() => selectCatalog(next));
      }
      break;
    case "select":
      selectCatalog(item);
      break;
    case "update": {
      const index = catalogs.value.findIndex((row) => row.did === currentDid.value);
      if (index === -1) catalogs.value.push({ did: currentDid.value, ...item });
      else catalogs.value.splice(index, 1, { ...catalogs.value[index], ...item });
      break;
    }
    case "智能对标": {
      percentage.show = true;
      percentage.value = 0;
      const rows = colRef.value?.getData?.() || [];
      rows.forEach((row) => {
        if (["国籍"].includes(row.colName)) row.codeTableId = row.colName;
        if (row.codeTableId) row.enableCodeTable = true;
        if (["姓名", "国籍", "证件号码", "入境日期"].includes(row.colName)) {
          row.dataStandardId = row.colName;
        }
        if (["姓名", "国籍", "证件号码", "主键"].includes(row.colName) || row.isNullable === "1") {
          row.qualityRule = "数据空值校验";
        }
      });
      window.setTimeout(() => { percentage.value = 50; }, 100);
      window.setTimeout(() => {
        percentage.value = 100;
        colRef.value?.setData?.(rows);
        window.setTimeout(() => { percentage.show = false; }, 500);
      }, 300);
      break;
    }
  }
};

onMounted(async () => {
  // 先初始化左侧目录，表单和数据表始终挂载，加载骨架仅覆盖显示。
  handleAction("init");
  loading.value = true;
  try {
    const rules = (await $form.get("登记目录")) || [];
    for (const rule of store.state.updateFormRule || []) {
      if (!rule.field) continue;
      const index = rules.findIndex((item) => item.field === rule.field);
      if (index === -1) continue;
      const finalProps = Object.assign(get(rules[index], "props", {}), rule.props || {});
      Object.assign(rules[index], rule);
      set(rules[index], "props", finalProps);
    }
    formRules.value = rules;
    await nextTick();
    formRef.value?.api?.display?.(true, ["appId", "dbId"]);
    formReady.value = true;
  } catch (error: any) {
    console.error("目录登记表单加载失败", error);
    loadError.value = error?.message || "目录登记表单加载失败，请稍后重试";
  } finally {
    loading.value = false;
  }
  const current = catalogs.value.find((item) => item.did === currentDid.value);
  if (current) await selectCatalog(current);
});

const handleFillDefaults = () => {
  const currentData = formRef.value?.getSaveData?.() || {};
  const defaultValues: Record<string, any> = {};
  formRules.value.forEach((rule: any) => {
    if (!rule.field || rule.display === false || !rule.$required) return;
    const currentValue = get(currentData, rule.field);
    if (currentValue !== undefined && currentValue !== null && currentValue !== "") return;
    if (rule.type !== "dict-select") return;
    const optionsKey = rule.props?.options;
    const options = isString(optionsKey) ? $dict.getDictItems(optionsKey) : optionsKey;
    if (options?.length) defaultValues[rule.field] = options[0].value;
  });
  if (Object.keys(defaultValues).length) {
    formRef.value?.setValue?.(defaultValues);
    $message.success(`已填充 ${Object.keys(defaultValues).length} 个字段的默认值`);
  } else {
    $message.info("没有需要填充的必填项字段");
  }
};

const saveApi = (data: Record<string, any>) => {
  const { catalogItems, ...props } = data;
  return $common.post("/dst/catalog/saveOrUpdate", {
    tid: currentCatalog.value.tid,
    assetType: "catalog",
    propList: props,
    catalogItems,
  });
};

const validate = async () => {
  await formRef.value?.validate?.();
  await colRef.value?.validate?.();
  return true;
};

const save = async () => {
  try {
    store.state.loadStatus.main = true;
    await validate();
    const formData = formRef.value?.getSaveData?.() || {};
    const catalogItems = colRef.value?.getData?.() || [];
    const data: any = await saveApi({ ...formData, catalogItems });
    const saved = { catalogItems, ...formData, ...(data || {}) };
    if (data?.catalogCode) formRef.value?.setValue?.({ catalogCode: data.catalogCode });
    handleAction("update", saved);
    $message.success("保存成功");
    return { success: true, data: saved };
  } catch (error: any) {
    throw new Error(error?.message || String(error));
  } finally {
    store.state.loadStatus.main = false;
  }
};

const next = () => {
  if (catalogs.value.some((item) => !item.tid)) throw new Error("请先保存全部数据目录");
};
const finish = () => {
  next();
  return [
    ...tables.value.filter((item) => item.assetStatus === 0).map((item) => ({ ...item, assetType: "table" })),
    ...catalogs.value.map((item) => ({ ...item, assetType: "catalog" })),
  ];
};
const commit = async () => finish();

defineExpose({ save, validate, next, finish, commit });

const tableOptions = reactive({
  cellConfig: {},
  toolbarConfig: {
    slots: { buttons: "toolbarButtons" },
    buttons: [],
    tools: [
      { code: "custom", icon: "vxe-icon-custom-column", mode: "text", status: "primary", name: "显示列" },
      { code: "import", icon: "vxe-icon-cloud-upload", mode: "text", status: "primary", name: "导入" },
      { code: "open_export", icon: "vxe-icon-download", mode: "text", status: "primary", name: "导出" },
      { code: "append_edit", icon: "vxe-icon-add", name: "新增数据项", status: "primary", mode: "text" },
    ],
  },
  columns: [
    { type: "seq", width: 70, field: "left" },
    { field: "colName", title: "数据项信息", minWidth: 200, editRender: { name: "VxeInput" } },
    { field: "colEn", title: "数据项英文名", minWidth: 200, editRender: { name: "VxeInput" } },
    {
      field: "colType",
      title: "数据项类型",
      width: 120,
      editRender: { name: "ElSelect", options: $dict.getDictItems("colType") },
    },
    {
      field: "dataStandardId",
      title: "数据标准",
      width: 180,
      editRender: {},
      slots: { default: "dataStandardId", edit: "dataStandardId_edit" },
    },
    { field: "codeTableId", title: "码表转换", width: 200, slots: { default: "codeTableId" } },
    {
      field: "operation",
      className: "catalog-item-operation",
      title: "操作",
      width: 72,
      fixed: "right",
      align: "center",
      headerAlign: "center",
      slots: { default: "operation" },
    },
  ],
  editRules: {
    colName: [{ required: true, content: "数据项名称不能为空", trigger: "blur" }],
    colEn: [{ required: true, content: "数据项英文名不能为空", trigger: "blur" }],
    colType: [{ required: true, content: "数据项类型不能为空", trigger: "blur" }],
  },
});
</script>

<style scoped lang="scss">
.catalog-register-content {
  position: relative;
  min-height: 420px;
}

.catalog-register-loading {
  position: absolute;
  inset: 0;
  z-index: 2;
  padding: 12px 0;
  background: #fff;
}

.catalog-register-fields.is-loading {
  visibility: hidden;
}

.catalog-register-layout {
  display: grid;
  grid-template-columns: minmax(220px, 25%) minmax(0, 75%);
  width: 100%;
  height: 100%;
  padding: 8px 20px 8px 0;
}

.catalog-register-main {
  min-width: 0;
  padding-left: 12px;
}

.catalog-register-form :deep(.jf-container__toolbar) {
  top: 5px;
  left: auto;
  right: 0;
}

.catalog-register-fields :deep(.catalog-item-operation .vxe-cell) {
  width: 100% !important;
}

.catalog-register-alert {
  margin-bottom: 12px;
}

.catalog-register-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 320px;
}
</style>
