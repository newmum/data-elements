<template>
  <div style="display: grid; grid-template-columns: 25% 75%" class="pr-5 py-2 h-full">
    <LeftSelect
      v-model="currentDid"
      title="数据目录列表"
      icon="fileCatalog"
      :list="leftList"
      :show-add="false"
      @add="(item) => handleAction('add', item)"
      @delete="(item) => handleAction('delete', item)"
      @select="(item) => handleAction('select', item)"
    />
    <div class="h-full flex">
      <el-skeleton v-if="currentDid" :loading="loading" animated>
        <FileCatalogData
          v-if="currentData"
          class="class-clear"
          title="导入数据"
          :show-upload="false"
          :file-catalog-id="currentData?.tid"
        />
        <!--        <v-table ref="colRef" :options="tableOptions">-->
        <!--          <template #toolbarButtons>-->
        <!--            <u-title class="pb-2! pt-0!" name="导入数据">-->
        <!--              <template #right>-->
        <!--                <el-button-->
        <!--                  v-if="currentData.uploadType === '1'"-->
        <!--                  type="danger"-->
        <!--                  icon="delete"-->
        <!--                  link-->
        <!--                  :disabled="!colRef?.getCheckData().length"-->
        <!--                  @click="handleAction('batch-delete-row')"-->
        <!--                >-->
        <!--                  批量删除-->
        <!--                </el-button>-->
        <!--                <el-button type="primary" link @click="handleAction('upload')">-->
        <!--                  <template #icon>-->
        <!--                    <Icon icon="el-icon-uploadFilled" size="16" />-->
        <!--                  </template>-->
        <!--                  {{ currentData.uploadType === "1" ? "导入数据" : "上传文件" }}-->
        <!--                </el-button>-->
        <!--              </template>-->
        <!--            </u-title>-->
        <!--          </template>-->

        <!--          <template #action="{ row }">-->
        <!--            <el-button v-if="currentData.uploadType === '2'" type="primary" link>预览</el-button>-->
        <!--            <el-button type="danger" link @click="handleAction('delete-row', row)">删除</el-button>-->
        <!--          </template>-->
        <!--        </v-table>-->
      </el-skeleton>

      <div v-if="!currentDid" class="flex-x-center">
        <Empty />
      </div>
    </div>

    <el-dialog
      v-model="state.open"
      width="800px"
      title="导入数据"
      :footer="null"
      header-class="pb-2"
    >
      <el-segmented
        v-if="currentData.uploadType !== '2'"
        v-model="state.type"
        :options="['增量导入', '全量导入']"
      />
      <DragUpload
        v-model="state.fileList"
        class="mt-3"
        :accept="currentData.uploadType === '1' ? '.xlsx,xls' : '*'"
        :title="currentData.uploadType === '1' ? `上传表格` : '上传文档、图片、音视频等自由格式'"
        :upload="upload"
        @success="handleAction('upload-success')"
      >
        <template v-if="currentData.uploadType !== '2'" #tip>
          <el-button
            class="mt-3"
            type="primary"
            link
            icon="download"
            @click="handleAction('download')"
          >
            下载模版
          </el-button>
        </template>
      </DragUpload>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from "vue";
import { useRegisterStore } from "@/store";
import { get, set } from "lodash-es";

const loading = ref(false);
const colRef = ref();
const store = useRegisterStore();
const currentDid = ref("");
const state = reactive({
  open: false,
  type: "增量导入",
  fileList: [],
});

// 表格配置
const tableOptions = reactive({
  cellConfig: {},
  toolbarConfig: {
    slots: {
      buttons: "toolbarButtons",
    },
    buttons: [],
    tools: [],
  },
  columns: [],
});
const fileCatalogs = computed({
  get: () => {
    if (!get(store.data, "fileCatalog")) {
      set(store.data, "fileCatalog", []);
    }
    return get(store.data, "fileCatalog", []);
  },
  set: (val) => set(store.data, "fileCatalog", val),
});
const leftList = computed(() => {
  return fileCatalogs.value || [];
});
const currentData = computed(() => {
  return fileCatalogs.value.find((el) => el.did === currentDid.value) || {};
});

onMounted(() => {
  handleAction("init");
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
    case "select":
      // 刷新右侧数据
      if (item.tid) {
        select(item);
      }
      break;
    case "download": {
      $common.download("/dst/catalog/files/download", { tid: currentData.value.tid });
      break;
    }
    case "upload": {
      state.open = true;
      break;
    }
    case "upload-success": {
      if (currentData.value.uploadType === "1") {
        // 刷新数据
        handleAction("select", currentData.value);
      } else {
        // 非结构化数据
        colRef.value.setData([]);
      }
      break;
    }
    case "delete-row":
      colRef.value?.setLoading(true);

      try {
        const rowId = item["unique_id"] || item["UNIQUE_ID"];
        deleteRowApi(currentData.value.tid, rowId).then((data) => {
          $message.success("删除成功");
          colRef.value.removeRow(item);
        });
      } finally {
        colRef.value?.setLoading(false);
      }
      break;
    case "batch-delete-row":
      colRef.value?.setLoading(true);
      try {
        const rowIds = colRef.value
          ?.getCheckData()
          ?.map((el) => {
            return el["unique_id"] || el["UNIQUE_ID"];
          })
          .join(",");
        deleteRowApi(currentData.value.tid, rowIds).then((data) => {
          $message.success("删除成功");
          handleAction("select", currentData.value);
        });
      } finally {
        colRef.value?.setLoading(false);
      }
      break;
  }
};

const select = (item: any) => {
  try {
    // 物化建表
    if (!item.tableId) {
      // 根据目录建表
      createTable(item.tid).then((data) => {
        nextTick(() => {
          handleAction("update", { ...item, tableId: data.tableId });
        });
      });
    }
    if (colRef.value) {
      colRef.value.setLoading(true);
    }

    // 获取表结构以及数据; 首次：结构化数据、非结构化数据-物化建表
    detailApi(item.tid).then((data) => {
      if (item.uploadType === "1") {
        const { columns = [], rows = [] } = data;
        const cols = columns?.map((col) => ({ title: col, field: col, minWidth: 120 }));

        const tableCols = [{ type: "checkbox", width: 60, field: "left" }, ...cols];
        tableCols.push({
          title: "操作",
          field: "action",
          width: 80,
          slots: { default: "action" },
        });
        // 转换数据格式
        const tableData = rows;
        nextTick(() => {
          colRef.value.gridOptions.columns = tableCols;
          colRef.value.gridOptions.data = tableData;
          colRef.value.setLoading(false);
        });
      } else {
        // 非结构化数据
        tableOptions.columns = [
          { type: "checkbox", width: 60, field: "left" },
          { title: "文件名称", field: "name", slots: { default: "fileName" } },
          { title: "文件类型", field: "type" },
          { title: "文件大小", field: "size" },
          {
            title: "操作",
            field: "action",
            width: 140,
            slots: { default: "action" },
          },
        ];

        nextTick(() => {
          colRef.value?.setData([
            { name: "国产电视剧报审表", type: "docx", size: "2.2MB" },
            { name: "电视剧拍摄制作备案公示表", type: "docx", size: "3.4MB" },
            { name: "主题曲歌词模板", type: "pdf", size: "1.5MB" },
          ]);
        });
      }
    });
  } finally {
    if (colRef.value) {
      colRef.value.setLoading(false);
    }
  }
};

const upload = (file: File) => {
  const formData = new FormData();
  formData.append("tid", currentData.value.tid);
  formData.append("file", file);
  formData.append("isFull", state.type === "全量导入");
  return $common.post("/dst/catalog/files/import", formData, {
    "Content-Type": "multipart/form-data",
  });
};

const createTable = (tid: string) => {
  return $common.get("/dst/catalog/files/create-table", { tid });
};

const detailApi = (tid: string) => {
  return $common.get("/dst/catalog/files/curd-data?action=get", { tid });
};

const deleteRowApi = (tid: string, rowId: string) => {
  return $common.get("/dst/catalog/files/curd-data?action=delete", { tid, rowId });
};

const finish = () => {
  const list = [];
  fileCatalogs.value.forEach((el) => {
    list.push({ ...el, assetType: "fileCatalog" });
  });
  return list;
};

// 暴露方法
defineExpose({
  finish,
  save: () => {
    $message.success("保存成功");
  },
});
</script>

<style scoped lang="scss">
.class-clear {
  box-shadow: none !important;
  padding: 0 !important;
}
</style>
