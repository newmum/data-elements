<template>
  <div v-loading="state.loading" class="card-container h-auto px-5 pt-2 flex-1 flex flex-col">
    <div class="flex-1 pb-2">
      <v-table
        ref="colRef"
        :options="tableOptions"
        :page="{ total: state.total }"
        show-page
        show-toolbar
        @page-change="handleAction('page-change', $event)"
      >
        <template #toolbarButtons>
          <u-title class="pb-1! pt-2!" :name="title">
            <template #right>
              <el-button
                v-if="showUpload"
                type="primary"
                link
                icon="RefreshRight"
                :disabled="!colRef?.getCheckData().length"
                @click="handleAction('批量上传')"
              >
                批量上传
              </el-button>
              <el-button
                v-if="showUpload"
                type="primary"
                link
                icon="RefreshRight"
                @click="handleAction('全量上传')"
              >
                全量上传
              </el-button>
              <el-button
                v-if="isStruct"
                type="danger"
                icon="delete"
                link
                :disabled="!colRef?.getCheckData().length"
                @click="handleAction('批量删除')"
              >
                批量删除
              </el-button>
              <el-button type="primary" link @click="handleAction('upload')">
                <template #icon>
                  <Icon icon="el-icon-uploadFilled" size="16" />
                </template>
                {{ isStruct ? "导入数据" : "上传文件" }}
              </el-button>
            </template>
          </u-title>
        </template>

        <template #action="{ row }">
          <el-button v-if="!isStruct" type="primary" link>预览</el-button>
          <el-button type="danger" link @click="handleAction('delete-row', row)">删除</el-button>
        </template>
      </v-table>
    </div>

    <el-dialog
      v-model="state.open"
      width="800px"
      title="导入数据"
      :footer="null"
      header-class="pb-2!"
    >
      <el-segmented v-if="isStruct" v-model="state.type" :options="['增量导入', '全量导入']" />
      <DragUpload
        v-model="state.fileList"
        class="mt-3"
        :accept="isStruct ? '.xlsx,xls' : '*'"
        :title="isStruct ? `上传表格` : '上传文档、图片、音视频等自由格式'"
        :upload="upload"
        @success="handleAction('upload-success')"
      >
        <template v-if="isStruct" #tip>
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
import { ref, onMounted, reactive, nextTick, computed } from "vue";

const props = defineProps({
  fileCatalogId: {
    type: String,
    required: true,
  },
  showUpload: {
    type: Boolean,
    default: true,
  },
  title: {
    type: String,
    default: "数据预览",
  },
});
const state = reactive({
  open: false,
  loading: false,
  type: "增量导入",
  fileList: [],
  fileCatalog: {},
  total: 0,
  pageNo: 1,
  pageSize: 20,
});
// refs
const colRef = ref();
const isStruct = computed(() => true);

onMounted(() => {
  handleAction("init");
});

const handleAction = (type: string, item?: any) => {
  switch (type) {
    case "init":
      init();
      break;
    case "download": {
      downloadApi(props.fileCatalogId);
      break;
    }
    case "upload": {
      state.open = true;
      break;
    }
    case "upload-success": {
      state.open = false;
      handleAction("init");
      break;
    }
    case "delete-row":
      colRef.value?.setLoading(true);
      deleteRowApi(props.fileCatalogId, item["unique_id"] || item["UNIQUE_ID"])
        .then(() => {
          $message.success("删除成功");
          handleAction("init");
        })
        .catch(() => {
          colRef.value?.setLoading(false);
        });
      break;
    case "批量删除": {
      const rowIds = colRef.value
        ?.getCheckData()
        ?.map((el) => {
          return el["unique_id"] || el["UNIQUE_ID"];
        })
        .join(",");
      colRef.value?.setLoading(true);
      $common.handle({
        url: "/dst/catalog/files/curd-data?action=delete",
        method: "get",
        params: {
          tid: props.fileCatalogId,
          rowId: rowIds,
        },
        info: "是否删除?",
        action: "",
        done: (data) => {
          $message.success("删除成功");
          handleAction("init");
        },
        fail: () => {
          colRef.value?.setLoading(false);
        },
      });
      break;
    }
    case "page-change":
      state.pageNo = item.pageNo;
      state.pageSize = item.pageSize;
      handleAction("init");
      break;

    case "批量上传":
      // 互联网操作
      state.loading = true;
      $common
        .post("/dst/catalog/files/upload", {
          tid: props.fileCatalogId,
          data: colRef.value?.getCheckData().map((el) => {
            const { _X_ROW_KEY, ...rest } = el;
            return rest;
          }),
        })
        .then((data) => {
          $message.success(data);
          state.loading = false;
        })
        .catch(() => {
          state.loading = false;
        });
      break;

    case "全量上传":
      // 互联网操作
      state.loading = true;
      $common.handle({
        url: "/dst/catalog/files/upload",
        method: "POST",
        data: {
          op: "all",
          tid: props.fileCatalogId,
        },
        info: "是否全量上传?",
        action: "",
        done: (data) => {
          $message.success(data);
          state.loading = false;
        },
        fail: () => {
          state.loading = false;
        },
      });
      break;
  }
};

const init = async () => {
  if (!props.fileCatalogId) return;

  state.loading = true;
  colRef.value?.setLoading(true);
  if (colRef.value) {
    state.loading = false;
  }
  try {
    // 加载详情
    await detailApi(props.fileCatalogId).then((data) => {
      const { columns = [], rows = [], total } = data;
      // 分页处理
      if (total > 0 && rows.length === 0 && state.pageNo > 1) {
        state.pageNo = state.pageNo - 1;
        handleAction("init");
        return;
      }
      state.total = total;
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
        nextTick(() => {
          colRef.value.gridOptions.columns = tableCols;
          colRef.value.gridOptions.data = tableData;
          colRef.value.setLoading(false);
        });
      });
    });
  } finally {
    colRef.value?.setLoading(false);
  }
};

const upload = (file: File) => {
  const formData = new FormData();
  formData.append("tid", props.fileCatalogId);
  formData.append("file", file);
  formData.append("isFull", state.type === "全量导入");
  return $common
    .post("/dst/catalog/files/import", formData, {
      "Content-Type": "multipart/form-data",
    })
    .then((data) => {
      $message.success(data);
    });
};

const detailApi = (tid: string) => {
  return $common.get("/dst/catalog/files/curd-data?action=get", {
    tid,
    pageNo: state.pageNo,
    pageSize: state.pageSize,
  });
};

const deleteRowApi = (tid: string, rowId: string) => {
  return $common.get("/dst/catalog/files/curd-data?action=delete", { tid, rowId });
};

const downloadApi = (tid: string) => {
  $common.download("/dst/catalog/files/download", {
    tid,
    isFull: state.type === "全量导入" ? "1" : "0",
  });
};

// 表单规则
// 表格配置
const tableOptions = reactive({
  height: "100%",
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
</script>
