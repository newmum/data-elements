<template>
  <div
    v-loading="saving"
    :element-loading-text="savingText"
    class="card-container px-5"
    style="display: flex; flex-direction: column; height: 100%; overflow: hidden"
  >
    <div class="flex flex-nowrap">
      <div
        v-if="!showCode"
        class="w-[25%] pt-5 lh-32px font-bold color-#464c64 text-16px mb-2 flex items-center gap-2 whitespace-nowrap overflow-hidden"
      >
        <span class="overflow-hidden text-ellipsis">页面组件</span>
        <el-button
          link
          type="primary"
          size="small"
          :title="magicApiEditorTitle"
          @click="openMagicApiEditor"
        >
          <Icon icon="el-icon-Link" size="14" />
          查看接口
        </el-button>
      </div>
      <div
        class="flex-1 flex items-center flex-nowrap overflow-hidden pt-4 pb-2 border-b border-gray-100 gap-4"
      >
        <!-- 左侧：编码/预览切换 -->
        <div class="flex items-center flex-shrink-0">
          <div class="flex bg-gray-100 p-1 rounded-lg">
            <button
              :class="`px-3 h-8 rounded-md text-[13px] font-bold flex items-center gap-1.5 cursor-pointer transition-all whitespace-nowrap ${editMode === 'code' ? 'bg-white text-blue-600 shadow-sm' : 'text-gray-400 hover:text-gray-600'}`"
              @click="editMode = 'code'"
            >
              <Icon icon="code" size="16" />
              编码
            </button>
            <button
              :class="`px-3 h-8 rounded-md text-[13px] font-bold flex items-center gap-1.5 cursor-pointer transition-all whitespace-nowrap ${editMode === 'preview' ? 'bg-white text-blue-600 shadow-sm' : 'text-gray-400 hover:text-gray-600'}`"
              @click="handlePreview"
            >
              <Icon icon="eye" size="16" />
              预览
            </button>
          </div>
        </div>

        <!-- 中间：组件名 -->
        <div class="flex-1 flex justify-center min-w-0 overflow-hidden">
          <div v-if="currentTab" class="flex items-center flex-nowrap min-w-0">
            <div class="flex items-center gap-1.5 group min-w-0">
              <div class="text-gray-500 text-sm whitespace-nowrap flex-shrink-0">组件名:</div>
              <input
                v-model="currentTab.remark"
                class="font-bold color-#464c64 text-16px bg-transparent border-b border-transparent hover:border-gray-200 focus:border-blue-500 focus:bg-gray-50 px-2 py-1 outline-none transition-all text-center min-w-0 w-[80px] lg:w-[120px]"
                placeholder="组件名称"
                maxlength="255"
                @change="updateComponentInfo(currentTab, 'remark')"
              />
              <Icon
                icon="edit"
                size="14"
                class="text-gray-300 opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0"
              />
            </div>
          </div>
          <div v-else class="text-gray-400 text-sm whitespace-nowrap">请选择一个组件进行编辑</div>
        </div>

        <!-- 右侧：操作按钮 -->
        <div class="flex items-center justify-end flex-shrink-0">
          <el-button
            title="刷新低代码组件缓存"
            plain
            :disabled="componentCacheRefreshing"
            @click="refreshComponentCache"
          >
            <Icon
              icon="el-icon-Refresh"
              size="18"
              class="refresh-cache-icon"
              :class="{ 'refresh-cache-icon--spinning': componentCacheRefreshing }"
            />
          </el-button>
          <el-button title="编译并保存" type="primary" @click="saveCode()">
            <Icon icon="save" size="18" />
          </el-button>
          <el-button title="复制" plain @click="copy()">
            <Icon icon="copy" size="18" />
          </el-button>
          <el-button title="历史记录" plain @click="openHistory(currentTab)">
            <Icon icon="el-icon-Timer" size="18" />
          </el-button>
          <el-button title="批量导出" plain @click="openExportDialog">
            <Icon icon="el-icon-Download" size="18" />
          </el-button>
          <el-button title="批量导入" plain @click="openImportDialog">
            <Icon icon="el-icon-Upload" size="18" />
          </el-button>
        </div>
      </div>
    </div>
    <el-splitter style="display: flex; flex: 1 1 0; overflow: hidden">
      <el-splitter-panel v-if="!showCode" size="440px" class="flex overflow-hidden flex-col">
        <u-tree
          ref="treeRef"
          highlight-current
          class="flex-1 mr-2 overflow-y-auto"
          url="/sym/component?action=tree"
          :expand="false"
          :default-expand-all="true"
          search
          search-width="100%"
          :checked="false"
          :checkable="false"
          show-line
          :contextmenu="treeContextmenu"
          :expand-on-click-node="false"
          :node-icon="{ fold: 'folder', unfold: 'folder-open', node: 'vue' }"
          :node-type="(node) => (node.data.isGroup === 1 ? 'dir' : 'file')"
          @node-click="nodeClick"
        />
      </el-splitter-panel>
      <el-splitter-panel class="overflow-y-hidden">
        <div v-if="tabs.length === 0" class="logo-content text-center">
          <div class="text-6xl mt-2 mb-2 italic font-black title-bg">页面组件</div>
          <el-button
            type="primary"
            color="#42b883"
            style="--el-button-text-color: #fff; --el-button-hover-text-color: #fff"
            @click="createFile('0', 0)"
          >
            新建分组
          </el-button>
          <div class="flex flex-wrap justify-between mt-4" style="color: #b6b6b6">
            <div class="text-base w-1/2 mb-4">保存Ctrl + S</div>
            <div class="text-base w-1/2">撤销Ctrl + Z</div>
          </div>
        </div>

        <template v-else>
          <!-- 代码模式 -->
          <div v-show="editMode === 'code'" class="w-full h-full overflow-hidden pl-2">
            <el-tabs
              v-model="tabId"
              type="card"
              class="sfc-tab"
              closable
              style="
                display: flex;
                flex-direction: column;
                width: 100%;
                height: 100%;
                overflow: hidden;
              "
              @tab-remove="tabClose"
            >
              <el-tab-pane
                v-for="tab in tabs"
                :key="tab.tid"
                :label="tab.name + (tab.isSave && tab.isSave === 1 ? ' *' : '')"
                :name="tab.tid"
                display-directive="show"
                style="display: flex; flex-direction: column; width: 100%; height: 100%; padding: 0"
              >
                <div style="display: flex; flex: 1; flex-direction: column; min-height: 0">
                  <code-editor
                    v-model="tab.code"
                    lang="javascript"
                    @change="changeCode(tab)"
                  ></code-editor>
                </div>
              </el-tab-pane>
            </el-tabs>
          </div>

          <!-- 预览模式 -->
          <div
            v-show="editMode === 'preview'"
            ref="previewRef"
            class="preview-container"
            style="width: 100%; height: 100%; overflow: auto"
          >
            <component :is="state.name" />
          </div>
        </template>
      </el-splitter-panel>
    </el-splitter>

    <u-modal
      v-model="modelParam.visible"
      :title="`${updateComponent ? '修改' : '添加'}${formData.type === 1 ? '组件' : '分组'}`"
      @confirm="saveComponent"
    >
      <el-form
        ref="dataForm"
        :rules="rules"
        :model="formData"
        label-placement="left"
        label-width="80px"
      >
        <el-form-item label="名称" prop="remark">
          <el-input v-model="formData.remark" maxlength="255" />
        </el-form-item>
        <el-form-item v-if="formData.type === 1" label="英文名" prop="name">
          <el-input v-model="formData.name" maxlength="255" />
        </el-form-item>
      </el-form>
    </u-modal>
    <u-modal v-model="showModel" title="保存异常">
      <el-input
        type="textarea"
        :model-value="errorMsg"
        :autosize="{ minRows: 6, maxRows: 10 }"
      ></el-input>
    </u-modal>

    <el-drawer
      v-model="showDrawer2"
      :title="currentNode?.name + ' - 历史记录'"
      direction="btt"
      size="80%"
      body-class="overflow-hidden!"
      @close="tableRef.setCurIndex(-1)"
    >
      <div class="flex h-full gap-4">
        <div class="w-400px h-full">
          <data-table
            ref="tableRef"
            show-index
            class="h-full"
            v-bind="historyTableOptions"
            :data="tableData"
            row-key="tid"
            @row-click="historyTableSelect"
          />
        </div>
        <div class="h-full w-full overflow-hidden">
          <div class="flex flex-col h-full w-full">
            <div class="flex gap-4 mb-2 items-center w-full">
              <div class="flex-1 flex-x-between">
                <div>修改时间：{{ currentHistoryOldDate }}</div>
                <el-button type="primary" @click="restoreToThisVersion">还原到此版本</el-button>
              </div>
              <div class="flex-1">当前版本</div>
            </div>
            <CodeDiff
              class="overflow-y-auto flex-1"
              :old-string="historyOldCode"
              :new-string="historyCode"
              :file-name="currentNodeId"
              :context="9999"
            />
          </div>
        </div>
      </div>
    </el-drawer>

    <!-- 批量导出弹窗 -->
    <el-dialog
      v-model="exportDialogVisible"
      title="批量导出组件"
      width="600px"
      append-to-body
      :close-on-click-modal="false"
      @close="resetExportTree"
    >
      <u-tree
        ref="exportTreeRef"
        url="/sym/component?action=tree"
        :checked="false"
        show-checkbox
        :default-expand-all="true"
        :node-icon="{ fold: 'folder', unfold: 'folder-open', node: 'vue' }"
        :node-type="(node) => (node.data.isGroup === 1 ? 'dir' : 'file')"
        style="max-height: 400px; overflow-y: auto"
      />
      <template #footer>
        <el-button @click="exportDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleExport">导出</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入弹窗 -->
    <el-dialog
      v-model="importDialogVisible"
      title="批量导入组件"
      width="500px"
      append-to-body
      :close-on-click-modal="false"
    >
      <el-upload
        drag
        accept=".json"
        :auto-upload="false"
        :show-file-list="false"
        :on-change="handleFileChange"
      >
        <div style="padding: 40px 0">
          <Icon icon="el-icon-UploadFilled" size="48" class="text-gray-400 mb-3" />
          <div class="text-gray-600">
            拖拽 JSON 文件到此处或
            <em class="text-blue-500">点击上传</em>
          </div>
        </div>
        <template #tip>
          <div class="el-upload__tip text-center">仅支持 .json 格式的组件导出文件</div>
        </template>
      </el-upload>
      <div v-if="saving" class="text-center text-gray-500 mt-4">
        <el-icon class="is-loading mr-1"><Icon icon="el-icon-Loading" /></el-icon>
        {{ savingText }}
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import runtimeSfc from "@/plugins/compiler/runtime-sfc";
import request from "@/utils/request";
import FileAPI from "@/api/file-api";
import { useRoute } from "vue-router";
import ComponentAPI from "@/api/system/component-api";
import dayjs from "dayjs";
import {
  ref,
  reactive,
  computed,
  watch,
  nextTick,
  onMounted,
  onBeforeUnmount,
  getCurrentInstance,
} from "vue";

// 引入 html2canvas 库用于截图
import html2canvas from "html2canvas";

const route = useRoute();
const formData = reactive({
  remark: "",
  name: "",
  type: 0,
});
const currentNodeId = ref();
const currentNodePid = ref();
const currentNode = ref();
const treeRef = ref();
const updateComponent = ref(false);
const modelParam = reactive({
  visible: false,
});
const showDrawer = ref(false);
const showDrawer2 = ref(false);
const errorMsg = ref();
const showModel = ref(false);
const historyCode = ref("");
const historyOldCode = ref("");
const historyTableWhere = reactive({});
const currentHistoryOldDate = ref();
const tableData = ref([]);
const historyTableOptions = reactive({
  showPage: false,
  columns: [
    {
      label: "时间",
      prop: "createdTime",
    },
  ],
});
const tableRef = ref();
const tabIdByRoute = ref();

const dataForm = ref();
const rules = reactive({
  name: [
    { required: true, message: "请输入英文名", trigger: "blur" },
    {
      trigger: "blur",
      message: "组件英文名必须以英文字母开头，只能包含英文、数字和-_",
      validator: (_rule, value) => {
        if (formData.type === 0) return true;
        if (value) {
          return /^[a-zA-Z][a-zA-Z0-9\-_]*$/.test(value);
        }
      },
    },
  ],
  remark: [{ required: true, message: "请输入名称", trigger: "blur" }],
});
const tabs = ref([]);
const tabId = ref();
const editMode = ref("code"); // 编辑模式：code（编码）或 preview（预览）
// 保存状态
const saving = ref(false);
const savingText = ref("正在保存...");

// 当前选中的 tab
const currentTab = computed(() => {
  if (!tabId.value) return null;
  const tab = tabs.value.find((tab) => tab.tid === tabId.value);
  return tab;
});

const magicApiPaths = computed(() => {
  const sourceCode = currentTab.value?.code || currentTab.value?.sourceCode || "";
  const paths = new Set();
  const matcher =
    /(?:\$common|request)\s*\.\s*(?:get|post|put|delete)\s*\(\s*[`'"]([^`'"?\s]+)|url\s*:\s*[`'"]([^`'"?\s]+)/g;
  let match;
  while ((match = matcher.exec(sourceCode))) {
    const path = match[1] || match[2];
    if (path?.startsWith("/")) paths.add(path);
  }
  return [...paths];
});

const magicApiEditorTitle = computed(() => {
  if (!currentTab.value) return "查看接口";
  return magicApiPaths.value.length ? `查看接口：${magicApiPaths.value.join("、")}` : "查看接口";
});

const openMagicApiEditor = () => {
  const [apiPath] = magicApiPaths.value;
  const editorHome = `${window.location.protocol}//${window.location.hostname}:8088/api/web/index.html#/home`;
  // 接口入口始终可用；识别到当前组件的请求地址时，再辅助定位到该接口。
  const editorUrl = apiPath ? `${editorHome}?path=${encodeURIComponent(apiPath)}` : editorHome;
  window.open(editorUrl, "_blank", "noopener");
};

// 是否仅显示右侧编码
const showCode = computed(() => !!route.query.tid || route.query.create);

// 自动创建组件
const createComponentByAuto = (pid) => {
  if (!pid) return;
  // 生成随机英文名
  const name = "sfc-" + Math.random().toString(36).substring(2, 10);
  request({
    url: "/sym/component?action=save",
    data: {
      pid,
      name,
      remark: "未定义",
      type: 1,
    },
    method: "POST",
  }).then((data) => {
    tabId.value = data;
    const sourceCode = "<template>\n\n</template>\n\n<script setup>\n\n<" + "/script>";
    const compileJs = `const __sfc__ = {}
function render(_ctx, _cache) {
  return null
}
__sfc__.render = render
__sfc__.__file = "mb-sfc-compiler.vue"
return __sfc__`;
    const compileCss = `/* No <style> tags present */`;
    request({
      url: "/sym/component?action=saveCode",
      data: {
        tid: data,
        sourceCode,
        compileJs,
        compileCss,
      },
      method: "POST",
    });
    tabs.value.push({
      tid: data,
      pid,
      name,
      realName: name,
      remark: "未定义",
      code: sourceCode,
      sourceCode,
      isSave: 0,
    });
    // 新增时需绑定页签
    bindTab(tabIdByRoute.value, data);
    // // 如果路由有值，上传缩略图
    // if (tabIdByRoute.value) {
    //   uploadThumbnail(tabIdByRoute.value);
    // }
  });
};

// 更新组件名称
const updateComponentInfo = async (tab, type) => {
  if (!tab || !tab.tid) return;

  // 根据类型确定要更新的字段
  const isUpdatingRemark = type === "remark";
  const isUpdatingRealName = type === "realName";

  if (isUpdatingRealName && !tab.realName) {
    ElMessage.warning("英文名不能为空");
    return;
  }

  if (isUpdatingRemark && !tab.remark) {
    ElMessage.warning("组件名不能为空");
    return;
  }

  // 验证英文名格式：必须以英文字母开头
  if (isUpdatingRealName && !/^[a-zA-Z][a-zA-Z0-9\-_]*$/.test(tab.realName)) {
    ElMessage.warning("组件英文名必须以英文字母开头，只能包含英文、数字和-_");
    return;
  }

  try {
    await request({
      url: "/sym/component?action=save",
      method: "POST",
      data: {
        type: 1,
        tid: tab.tid,
        pid: tab.pid,
        remark: tab.remark,
        name: tab.realName,
      },
    });

    ElMessage.success(`${isUpdatingRemark ? "组件名" : "英文名"}更新成功`);
    // 刷新树组件
    if (treeRef.value) {
      treeRef.value.reload();
    }
  } catch (error) {
    console.error("更新失败:", error);
  }
};

function historyTableSelect(row) {
  const i = tableRef.value.tableData.findIndex((el) => el.tid === row.tid);
  tableRef.value.setCurIndex(i);
  currentHistoryOldDate.value = row.createdTime;
  request({
    url: "/sym/component?action=historyDetail",
    params: { tid: row.tid },
    method: "post",
  }).then((data) => {
    historyOldCode.value = data;
  });
}
const treeContextmenu = ref([
  {
    key: "addGroup",
    label: "添加分组",
    click: (node) => {
      createFile(node.tid, 0);
    },
  },
  {
    key: "addComponent",
    label: "添加组件",
    click: (node) => {
      createFile(node.tid, 1);
    },
  },
  {
    key: "updateComponent",
    label: "修改",
    click: (node) => {
      formData.remark = "";
      formData.type = node.isGroup === 0 ? 1 : 0;
      if (formData.type === 0) {
        // 分组 remark 和 name 一致
        formData.remark = node.remark || node.name;
        formData.name = formData.remark;
      }
      if (formData.type === 1) {
        // 组件
        formData.name = node.realName;
        formData.remark = node.remark;
      }

      updateComponent.value = true;
      currentNodeId.value = node.tid;
      currentNodePid.value = node.pid;
      modelParam.visible = true;
    },
  },
  {
    key: "delete",
    label: "删除",
    click: (node) => {
      $common.handle({
        url: "/sym/component?action=del",
        params: { tid: node.tid },
        method: "post",
        done: () => {
          treeRef.value.reload();
        },
      });
    },
  },
  // {
  //   key: "history",
  //   label: "历史记录",
  //   if(node) {
  //     return !node.isGroup;
  //   },
  //   click: (node) => {
  //     openHistory(node);
  //   },
  // },
]);
function openHistory(node) {
  if (!node) {
    ElMessage.warning("请先选择一个组件");
    return;
  }
  currentNodeId.value = node.tid;
  currentNode.value = node;
  historyTableWhere.componentId = node.tid;

  request({
    url: "/sym/component?action=history",
    method: "POST",
    params: { componentId: node.tid },
  }).then((data) => {
    tableData.value = data.list;
  });
  request({
    url: "/sym/component?action=getLastCode",
    params: { componentId: node.tid },
    method: "POST",
  }).then((data) => {
    if (data) {
      const { sourceCode, createdTime } = data;
      currentHistoryOldDate.value = createdTime;
      historyOldCode.value = sourceCode;
      const tab = tabs.value.find((it) => it.tid === currentNodeId.value);
      if (tab) {
        historyCode.value = tab.code;
      } else {
        historyCode.value = sourceCode;
      }
    }
    showDrawer2.value = true;
  });
}
function createFile(tid, type) {
  // type 0 分组 1 组件
  formData.type = type;
  formData.name = "";
  formData.remark = "";
  updateComponent.value = false;
  currentNodeId.value = tid;
  currentNodePid.value = tid;
  modelParam.visible = true;
}
async function saveComponent() {
  try {
    const valid = await dataForm.value?.validate();
    if (!valid) return;

    if (formData.type === 0) {
      formData.name = formData.remark;
    }
    saving.value = true;
    savingText.value = "正在保存...";

    const data = await request({
      url: "/sym/component?action=save",
      data: {
        [updateComponent.value ? "tid" : "pid"]: currentNodeId.value,
        ...(updateComponent.value ? { pid: currentNodePid.value } : {}),
        ...formData,
      },
      method: "POST",
    });

    // 如果是新增组件，添加默认代码，并等待代码保存完成。
    if (formData.type === 1 && !updateComponent.value) {
      savingText.value = "正在创建组件...";
      const sourceCode = "<template>\n\n</template>\n\n<script setup>\n\n<" + "/script>";
      const compileJs = `const __sfc__ = {}
function render(_ctx, _cache) {
  return null
}
__sfc__.render = render
__sfc__.__file = "mb-sfc-compiler.vue"
return __sfc__`;
      const compileCss = `/* No <style> tags present */`;
      await request({
        url: "/sym/component?action=saveCode",
        data: { tid: data, sourceCode, compileJs, compileCss },
        method: "POST",
      });

      if (tabIdByRoute.value) {
        await uploadThumbnail(tabIdByRoute.value);
      }
    }

    modelParam.visible = false;
    if (updateComponent.value) {
      tabs.value.forEach((it) => {
        if (it.tid === currentNodeId.value) {
          it.name = formData.name;
          it.realName = formData.name;
          it.remark = formData.remark;
        }
      });
    }
    treeRef.value?.reload();
  } catch (error) {
    console.error("保存页面组件失败:", error);
    // request 拦截器已展示后端业务异常（含错误码）；这里只处理本地异常，避免双重提示。
    if (!error?.handled) {
      ElMessage.warning(`保存失败：${error?.message || error || "请稍后重试"}`);
    }
  } finally {
    // 请求、编译或缩略图上传失败都必须解除全页遮罩；保留表单供用户修改后重试。
    saving.value = false;
    savingText.value = "正在保存...";
  }
}
function nodeClick(option) {
  if (!option.isGroup) {
    editMode.value = "code";

    const index = tabs.value.findIndex((it) => it.tid === option.tid);
    if (index !== -1) {
      tabId.value = option.tid;
      tabs.value[index].realName = option.realName;
      tabs.value[index].remark = option.remark;
      tabs.value[index].pid = option.pid;
    } else {
      request({
        url: "/sym/component?action=getSourceCode&tid=" + option.tid,
        method: "post",
      }).then((data) => {
        tabs.value.push({
          tid: option.tid,
          name: option.name,
          realName: option.realName,
          remark: option.remark,
          pid: option.pid,
          code: data || "",
          sourceCode: data || "",
          isSave: 0,
        });
        tabId.value = option.tid;
      });
    }
  }
}

function tabClose(tid) {
  const index = tabs.value.findIndex((it) => it.tid == tid);
  tabs.value.splice(index, 1);
  if (tabs.value.length > 0) {
    tabId.value = tabs.value[tabs.value.length - 1].tid;
  } else {
    tabId.value = "";
  }
}
const state = reactive({
  name: undefined,
  compileJs: undefined,
  compileCss: undefined,
});
const currentInstance = getCurrentInstance();
const previewRef = ref(null);
const componentCacheRefreshing = ref(false);

/**
 * 清理 Redis 与 JVM 中的低代码组件缓存，并重新加载当前浏览器的运行时组件。
 * 页面重载后，已经挂载的业务页面也会按最新组件定义重新创建。
 */
const refreshComponentCache = async () => {
  if (componentCacheRefreshing.value) return;

  componentCacheRefreshing.value = true;
  try {
    const result = await request({
      url: "/sym/component/cache/refresh",
      method: "post",
      data: {},
      timeout: 120000,
    });
    const app = currentInstance?.appContext?.app;
    const componentCount = app ? await runtimeSfc.loadAll(app) : 0;
    const version = result?.version ? `，缓存版本 ${result.version}` : "";
    const hasUnsavedCode = tabs.value.some((tab) => tab.isSave === 1);

    if (hasUnsavedCode) {
      ElMessage.warning(`低代码组件缓存已刷新${version}，当前有未保存代码，页面未自动重载`);
      return;
    }

    ElMessage.success(`低代码组件缓存已刷新${version}，已重载 ${componentCount} 个组件`);
    window.setTimeout(() => window.location.reload(), 500);
  } catch (error) {
    console.error("刷新低代码组件缓存失败:", error);
  } finally {
    componentCacheRefreshing.value = false;
  }
};

// 生成预览页面的截图，返回 base64 格式
const generatePreviewScreenshot = async () => {
  if (!previewRef.value) {
    console.error("预览组件未找到");
    return null;
  }

  try {
    // 如果当前不是预览模式，先切换并等待 DOM 更新
    if (editMode.value !== "preview") {
      editMode.value = "preview";
      await nextTick();
      // 给组件渲染一点时间
      await new Promise((resolve) => setTimeout(resolve, 1000));
    }

    // 确保预览容器可见
    const previewContainer = previewRef.value;
    if (!previewContainer || previewContainer.offsetHeight === 0) {
      console.error("预览容器不可见或无内容");
      return null;
    }

    // 使用 html2canvas 生成截图
    const canvas = await html2canvas(previewContainer, {
      scale: 2, // 提高截图质量
      useCORS: true, // 允许跨域图片
      logging: false, // 关闭日志
      backgroundColor: "#ffffff", // 设置背景色
    });

    // 将 canvas 转换为 base64 格式
    const base64Image = canvas.toDataURL("image/png");
    return base64Image;
  } catch (error) {
    console.error("生成截图失败:", error);
    return null;
  }
};

const saveThumbnailToTab = async (tabId, thumbnail) => {
  if (!tabId || !thumbnail) return;
  request({
    url: "/das/repository/tab/saveTabThumbnail",
    data: {
      tid: tabId,
      thumbnail,
    },
    method: "post",
  });
};

/** 上传 base64 并保存缩略图 */
const uploadThumbnail = async (tabId) => {
  savingText.value = "正在生成缩略图...";
  const base64Data = await generatePreviewScreenshot();
  if (!base64Data) {
    saving.value = false;
    return "";
  }
  savingText.value = "正在上传缩略图...";
  try {
    const fileInfo = await FileAPI.uploadBase64(
      base64Data,
      `component_thumbnail_${$common.uuid()}.jpg`,
      "image/jpeg"
    );
    const thumbnailUrl = fileInfo.preView ?? "";
    await saveThumbnailToTab(tabId, thumbnailUrl);
    saving.value = false;
  } catch (error) {
    console.error("上传缩略图失败:", error);
    saving.value = false;
  }
};

async function saveCode(showRun) {
  if (saving.value) return;
  const tid = tabId.value;
  if (!tid) return;
  const tab = tabs.value.find((it) => it.tid === tid);
  if (!tab) return;

  // 普通保存仅处理发生变化的源码；预览仍需编译并注册当前组件。
  if (!showRun && tab.code === tab.sourceCode) {
    ElMessage.info("源码未修改，无需保存");
    return;
  }

  let compiling = true;
  try {
    const sourceCode = tab.code;

    // 编译大组件会短暂占用浏览器主线程，先渲染遮罩，避免用户误以为保存无响应。
    saving.value = true;
    savingText.value = "正在编译组件...";
    await nextTick();

    const { compileJs, compileCss } = await runtimeSfc.compile(sourceCode);
    compiling = false;

    // 生成驼峰组件名
    const rawName = tab.realName;
    const componentName = runtimeSfc.componentName(rawName);

    state.compileJs = compileJs;
    state.compileCss = compileCss;
    state.name = componentName; // 使用驼峰格式的组件名

    if (tab.isSave === 1) {
      savingText.value = "正在保存编译产物...";

      await request({
        url: "/sym/component?action=saveCode",
        data: {
          tid,
          sourceCode,
          compileJs,
          compileCss,
        },
        method: "post",
      });
      tab.sourceCode = sourceCode;
      tab.isSave = 0;
      ElMessage.success("编译并保存成功");

      // 注册组件
      const appState = {
        name: rawName,
        compileJs,
        compileCss,
      };
      const app = currentInstance?.appContext.app;
      runtimeSfc.register(app, appState);

      if (showRun) showDrawer.value = true;
      // 如果路由有值，上传缩略图
      if (tabIdByRoute.value) {
        await uploadThumbnail(tabIdByRoute.value);
      }
    } else if (showRun) {
      // 未修改时也需要注册组件
      const appState = {
        name: rawName,
        compileJs,
        compileCss,
      };
      const app = currentInstance?.appContext.app;
      runtimeSfc.register(app, appState);
      showDrawer.value = true;
    }
  } catch (e) {
    console.error(e);
    // 编译错误仍展示代码错误弹窗；接口保存异常只提示，不能遗留弹窗或加载遮罩。
    if (compiling) {
      errorMsg.value = e;
      showModel.value = true;
    } else if (!e?.handled) {
      ElMessage.warning(`保存失败：${e?.message || e || "请稍后重试"}`);
    }
    tab.isSave = 1;
  } finally {
    saving.value = false;
    savingText.value = "正在保存...";
  }
}
function changeCode(tab) {
  if (tab.code !== tab.sourceCode) {
    tab.isSave = 1;
  } else {
    tab.isSave = 0;
  }
}
function restoreToThisVersion() {
  const tab = tabs.value.find((it) => it.tid === tabId.value);
  tab.code = historyOldCode.value;
  showDrawer2.value = false;
}

// ==================== 批量导出 ====================
const exportDialogVisible = ref(false);
const exportTreeRef = ref();

const openExportDialog = () => {
  exportDialogVisible.value = true;
};

const resetExportTree = () => {
  const elTree = exportTreeRef.value?.getTree();
  if (elTree) {
    elTree.setCheckedKeys([]);
  }
};

const handleExport = async () => {
  const elTree = exportTreeRef.value?.getTree();
  if (!elTree) return;

  const checkedNodes = elTree.getCheckedNodes(false, true);

  if (checkedNodes.length === 0) {
    ElMessage.warning("请选择要导出的内容");
    return;
  }

  saving.value = true;
  savingText.value = `正在导出...`;

  try {
    const tids = checkedNodes.map((n) => n.tid);
    const exportData = await ComponentAPI.batchExport(tids);

    const blob = new Blob([JSON.stringify(exportData, null, 2)], {
      type: "application/json",
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `vue-component-${dayjs().format("MMDD")}.json`;
    a.click();
    URL.revokeObjectURL(url);

    ElMessage.success(`导出成功`);
    exportDialogVisible.value = false;
  } catch (e) {
    console.error("导出失败:", e);
    if (!e?.handled) {
      ElMessage.warning("导出失败: " + (e.message || e));
    }
  } finally {
    saving.value = false;
  }
};

// ==================== 批量导入 ====================
const importDialogVisible = ref(false);

const openImportDialog = () => {
  importDialogVisible.value = true;
};

/** 文件选择回调：解析文件 → 调接口 → 关闭弹框 */
const handleFileChange = async (file) => {
  if (!file.raw) return;

  saving.value = true;
  savingText.value = "正在导入...";

  try {
    const text = await file.raw.text();
    const data = JSON.parse(text);

    if (!data.components || !Array.isArray(data.components)) {
      ElMessage.warning("文件格式不正确：缺少 components 字段");
      return;
    }

    await ComponentAPI.batchImport(data);

    // 刷新树
    if (treeRef.value) {
      treeRef.value.reload();
    }

    $message.success("导入成功");

    importDialogVisible.value = false;
  } catch (e) {
    console.error("导入失败:", e);
  } finally {
    saving.value = false;
  }
};

const init = () => {
  window.addEventListener("keydown", (e) => {
    // 判断是否按下 Ctrl+S（Mac 上是 Cmd+S）
    const isCtrlOrCmd = e.ctrlKey || e.metaKey; // metaKey 对应 Mac 的 Command 键
    const isSKey = e.key.toLowerCase() === "s";

    if (isCtrlOrCmd && isSKey) {
      e.preventDefault(); // 阻止默认行为（如浏览器保存页面）
      saveCode();
    }
  });
};

const copy = () => {
  if (!tabId.value) return;
  const tab = tabs.value.find((it) => it.tid === tabId.value);
  $common.copyText(tab.code);
};
const handlePreview = async () => {
  const tid = tabId.value;
  if (!tid) return;
  const tab = tabs.value.find((it) => it.tid === tid);
  if (!tab) return;

  try {
    // 预览始终直读数据库，不使用浏览器运行时组件缓存，也不使用未保存的编辑器内容。
    const sourceCode = await request({
      url: `/sym/component?action=getSourceCode&tid=${tid}`,
      method: "post",
    });
    if (!sourceCode) {
      ElMessage.warning("数据库中未找到该组件代码，暂无法预览");
      return;
    }
    const { compileJs, compileCss } = await runtimeSfc.compile(sourceCode);
    const rawName = tab.realName;
    const componentName = runtimeSfc.componentName(rawName);

    const appState = {
      name: rawName,
      compileJs,
      compileCss,
    };

    if (currentInstance) {
      const app = currentInstance.appContext.app;
      runtimeSfc.register(app, appState);
      state.name = componentName;
      editMode.value = "preview";
    }
  } catch (e) {
    console.error("读取数据库组件并预览失败:", e);
    if (!e?.handled) {
      ElMessage.warning("读取数据库组件失败，无法预览");
    }
  }
};

// 绑定页签
const bindTab = async (tabId, tid) => {
  return await request({
    url: "/das/repository/tab/bind",
    method: "POST",
    data: {
      pid: tabId,
      tid,
    },
  });
};

// 监听路由参数
watch(
  () => [
    route.query.tid,
    route.query.realName,
    route.query.remark,
    route.query.create,
    route.query.pid,
    route.query.tabId,
  ],
  ([tid, realName, remark, create, pid, tabId]) => {
    tabIdByRoute.value = tabId;
    if (tid && realName && remark) {
      // 模拟节点点击，构造 option 对象
      const option = {
        tid,
        pid,
        remark,
        realName,
        name: `${remark}(${realName})`,
        isGroup: 0, // 组件
      };

      // 调用 nodeClick 方法
      nodeClick(option);
    }
    if (create && pid) {
      createComponentByAuto(pid);
    }
  },
  { immediate: true }
);

// 初始化监听
onMounted(() => {
  init();
});
onBeforeUnmount(() => {
  window.removeEventListener("ctrls", init);
});
</script>

<style scoped>
.tools i,
.tools svg {
  cursor: pointer;
}
.logo-content {
  width: 400px;
  padding-top: 30vh;
  margin: 0 auto;
  text-align: center;
}
.title-bg {
  line-height: 1.25;
  background: -webkit-linear-gradient(315deg, #42d392 25%, #647eff);
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.refresh-cache-icon--spinning {
  animation: refresh-cache-spin 0.8s linear infinite;
}

@keyframes refresh-cache-spin {
  to {
    transform: rotate(360deg);
  }
}

:deep(.sfc-tab .el-tabs__header) {
  display: none;
  margin: 0;
}

/* 动画 */
@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}
.animate-fadeIn {
  animation: fadeIn 0.3s ease-in-out;
}

@keyframes bounce {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-10px);
  }
}
.animate-bounce {
  animation: bounce 1s infinite;
}

/* 按钮过渡效果 */
.transition-all {
  transition: all 0.2s ease;
}

/* 激活状态缩放 */
.active\:scale-95:active {
  transform: scale(0.95);
}

/* 阴影效果 */
.shadow-sm {
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
}
</style>
