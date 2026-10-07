<template>
  <div style="display: grid; grid-template-columns: 25% 75%" class="pr-5 py-2">
    <LeftSelect
      v-model="currentDid"
      title="数据目录列表"
      icon="fileCatalog"
      :list="leftList"
      :field-name="{ title: 'fileCatalogName' }"
      @add="(item) => handleAction('add', item)"
      @delete="(item) => handleAction('delete', item)"
      @select="(item) => handleAction('select', item)"
    />
    <div>
      <el-skeleton v-if="currentDid" :loading="loading" animated>
        <JsonForm ref="formRef" bordered :rules="formRules" />
        <v-table
          ref="colRef"
          :options="tableOptions"
          :insert-data="{ colLength: '255', colType, isPk: 0, isNullable: 1 }"
          show-operation
          editable
          @btn-click="(params) => handleAction(params.code, params)"
        >
          <template #toolbarButtons>
            <u-title name="数据项" style="padding: 4px 0"></u-title>
          </template>
          <template #isPk="{ row }">
            <el-checkbox v-model="row.isPk" true-value="1" false-value="0"></el-checkbox>
          </template>
          <template #isNullable="{ row }">
            <!--    isNullable为1表示可为空，0表示不能为空    -->
            <el-checkbox v-model="row.isNullable" true-value="0" false-value="1"></el-checkbox>
          </template>
        </v-table>
      </el-skeleton>

      <div v-else class="flex-x-center">
        <Empty />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, nextTick, onMounted } from "vue";
import { useRegisterStore } from "@/store";
import { get, set } from "lodash-es";

const loading = ref(false);
const formRef = ref();
const colRef = ref();
const formRules = ref([]);
const store = useRegisterStore();
const currentDid = ref("");

const fileCatalogs = computed({
  get: () => {
    if (!get(store.data, "fileCatalog")) {
      set(store.data, "fileCatalog", []);
    }
    return get(store.data, "fileCatalog", []);
  },
  set: (val) => set(store.data, "fileCatalog", val),
});
const currentData = computed(() => {
  return fileCatalogs.value.find((el) => el.did === currentDid.value) || {};
});
const leftList = computed(() => {
  return fileCatalogs.value || [];
});
const initData = reactive({
  orgId: $user.orgId,
});

const colType = computed(
  () => $dict.getDictItems("colType").find((d) => d.label === "字符串型C")?.value
);

// 初始化表单规则
$form.get("登记文件目录").then((data) => {
  formRules.value = data;
  onMounted(
    nextTick(() => {
      handleAction("init");
    })
  );
});

const handleAction = (type: string, item?: any) => {
  switch (type) {
    case "init":
      if (!leftList.value.length) {
        handleAction("add");
      }
      break;
    case "add":
      fileCatalogs.value.push({ did: $common.uuid() });
      break;
    case "delete":
      fileCatalogs.value = fileCatalogs.value.filter((el) => el.did !== item.did);
      break;
    case "select":
      // 刷新右侧数据
      formRef.value?.resetFields();
      if (item.tid) {
        // 请求数据，覆盖数据
        loading.value = true;
        detailApi(item.tid).then((res) => {
          const [data, catalogItems] = res;
          loading.value = false;
          nextTick(() => {
            formRef.value.setValue(data);
            colRef.value.setData(catalogItems || []);
          });
        });
      } else {
        const fileCatalog = fileCatalogs.value.find((el) => el.did === item.did) || {};
        nextTick(() => {
          const init = { ...initData, ...fileCatalog };
          formRef.value?.setValue({ ...init });
          colRef.value?.setData(init.catalogItems || []);
        });
      }
      break;
    case "update": {
      const cache = fileCatalogs.value;
      const i = cache.findIndex((el) => el.did === currentDid.value);
      if (i === -1) {
        cache.push({ did: currentDid.value, ...item });
      } else {
        cache.splice(i, 1, Object.assign({}, cache[i], item));
      }
      break;
    }
  }
};

const validate = async (): Promise<boolean> => {
  if (formRef.value) {
    await formRef.value.validate();
  }
  await colRef.value.validate();

  if ((colRef.value.getData() || []).length === 0) {
    throw "请添加数据项";
  }
};

const detailApi = (tid: string) => {
  return Promise.all([
    $common.post("/dst/catalog/detail", { tid }),
    $common.post("/dst/catalog/catalog-items/list", { tid }),
  ]);
};

const saveApi = (data: Record<string, any>) => {
  const { catalogItems, ...props } = data;
  return $common.post("/dst/catalog/saveOrUpdate", {
    tid: currentData.value.tid,
    assetType: "fileCatalog",
    propList: props,
    catalogItems,
  });
};

const save = async () => {
  try {
    store.state.loadStatus["main"] = true;
    await validate();
    // 保存逻辑
    const formData = formRef.value?.getSaveData() || {};
    const catalogItems = colRef.value.getData();

    const data: any = await saveApi({ ...formData, catalogItems }); // 调用保存接口
    const newObj = Object.assign({ catalogItems }, formData, data);

    // 更新数据目录编码唯一标识符，此编码由后端生成
    const { uuid } = data;
    if (uuid) {
      formRef.value.setValue({ uuid });
    }

    handleAction("update", newObj);
    return {
      success: true,
      msg: "保存成功",
    };
  } catch (err: any) {
    return {
      success: false,
      msg: (err?.message || err) as string,
    };
  } finally {
    store.state.loadStatus["main"] = false;
  }
};

const next = () => {
  if (fileCatalogs.value.some((el) => !el.tid)) {
    throw "请保存";
  }
};

const finish = () => {
  next();
  const list = [];
  fileCatalogs.value.forEach((el) => {
    list.push({ ...el, assetType: "fileCatalog" });
  });
  return list;
};

// 暴露方法
defineExpose({
  save,
  validate,
  next,
  finish,
});

// 表格配置
const tableOptions = reactive({
  cellConfig: {},
  toolbarConfig: {
    slots: {
      buttons: "toolbarButtons",
    },
    buttons: [],
    tools: [
      // {
      //   code: "custom",
      //   icon: "vxe-icon-custom-column",
      //   mode: "text",
      //   status: "primary",
      //   name: "显示列",
      // },
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
      {
        code: "append_edit", // 底部新增
        icon: "vxe-icon-add",
        name: "新增数据项",
        status: "primary",
        mode: "text",
      },
    ],
  },
  columns: [
    { type: "seq", width: 70, field: "left" },
    {
      field: "colName", // 可编辑-插槽渲染
      title: "数据项信息",
      minWidth: 200,
      editRender: { name: "VxeInput" },
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
      width: 150,
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
      // editRender: {},
      slots: { default: "isPk" },
    },
    {
      field: "isNullable", // 可编辑-配置渲染
      title: "非空",
      width: 80,
      align: "center",
      // editRender: {},
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

<style scoped lang="scss"></style>
