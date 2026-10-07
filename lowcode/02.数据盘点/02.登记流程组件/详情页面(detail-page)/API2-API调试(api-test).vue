<template>
  <div class="api-test card-container flex px-5 pt-5">
    <!-- 请求配置区域 -->
    <div class="api-test-panel">
      <div class="formItem">
        <label>请求方法：</label>
        <el-select v-model="method" style="width: 100px" :options="methods"></el-select>
        <el-button type="primary" :loading="loading" @click="testApi">运行测试</el-button>
      </div>

      <div class="formItem">
        <label>请求URL：</label>
        <el-input v-model="url" type="text" style="flex: 1" placeholder="请输入API地址" />
      </div>

      <div>
        <h4 class="api-test-req">请求参数</h4>
        <v-table
          ref="paramsRef"
          :options="paramsOptions"
          :show-toolbar="true"
          :editable="true"
          class="api-test-table"
        />
      </div>
    </div>

    <!-- 响应展示区域 -->
    <div class="api-test-panel flex flex-col">
      <h3 class="api-test-res">响应结果</h3>
      <code-editor v-model="response" class="flex-1" />
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref, reactive, onMounted } from "vue";
import { useDictStore } from "@/store";
import { ElMessage } from "element-plus";
import { useRoute } from "vue-router";
import request from "@/utils/request";

const loading = ref(false);
const url = ref("");
const method = ref<"get" | "post" | "put" | "delete">("post");
const response = ref<string>("");
const dictStore = useDictStore();
const route = useRoute();
const paramsRef = ref<any>(null);
const methods = [
  {
    value: "get",
    label: "GET",
    disabled: false,
  },
  {
    value: "post",
    label: "POST",
    disabled: false,
  },
  {
    value: "put",
    label: "PUT",
    disabled: false,
  },
  {
    value: "delete",
    label: "DELETE",
    disabled: false,
  },
];
const paramsOptions = reactive({
  importConfig: { mode: "covering" },
  toolbarConfig: {
    buttons: [
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
    {
      type: "input",
      field: "paramName",
      title: "参数名称",
      editRender: { name: "VxeInput", props: { placeholder: "请输入参数名称", maxlength: 256 } },
    },
    {
      type: "select",
      field: "paramType",
      title: "参数类型",
      width: 120,
      editRender: {
        name: "ElSelect",
        options: dictStore.getDictItems("paramType"),
        props: { placeholder: "请选择参数类型" },
      },
    },
    {
      type: "select",
      field: "paramPosition",
      title: "参数位置",
      width: 120,
      editRender: {
        name: "ElSelect",
        options: dictStore.getDictItems("paramPosition"),
        props: { placeholder: "请选择参数位置" },
      },
    },
    {
      type: "select",
      field: "required",
      title: "必填属性",
      width: 120,
      editRender: {
        name: "ElSelect",
        options: dictStore.getDictItems("requiredAttribute"),
        props: { placeholder: "请选择必填属性" },
      },
    },
    {
      type: "input",
      field: "defaultValue",
      title: "默认值",
      width: 120,
      editRender: { name: "VxeInput", props: { placeholder: "请输入默认值", maxlength: 256 } },
    },
  ],
});

const fetchServiceData = async (_serviceId: string) => {
  const response = await $common.post("/dst/catalog/detail", {
    tid: _serviceId,
  });
  const providerConfig = JSON.parse(response?.providerConfig || "{}");
  method.value = providerConfig?.httpMethod || "";
  url.value = response?.publishAddressFull || "";
  const paramsData =
    typeof response?.publishParams === "string"
      ? JSON.parse(response?.publishParams || "[]")
      : response?.publishParams || [];
  paramsRef.value?.setRows(paramsData);
};

/**
 * 根据参数表格数据，按位置拆分为 query/body/path 三组
 */
const buildRequestParams = (paramsData: any[]) => {
  const queryParams: Record<string, any> = {};
  const bodyParams: Record<string, any> = {};
  const pathParamMap: Record<string, string> = {};

  paramsData.forEach((p: any) => {
    const key = p.paramName;
    const value = p.defaultValue ?? "";
    if (!key) return;

    switch (p.paramPosition) {
      case "query":
        queryParams[key] = value;
        break;
      case "body":
        bodyParams[key] = value;
        break;
      case "path":
        pathParamMap[key] = value;
        break;
      // header / form 等暂不处理，可根据需要扩展
    }
  });

  return { queryParams, bodyParams, pathParamMap };
};

/**
 * 替换 URL 中的路径占位符，如 /api/{id} → /api/123
 */
const resolveUrl = (rawUrl: string, pathParams: Record<string, string>) => {
  let result = rawUrl;
  Object.entries(pathParams).forEach(([key, value]) => {
    result = result.replace(`{${key}}`, encodeURIComponent(value));
  });
  return result;
};

const testApi = async () => {
  if (!url.value) {
    ElMessage.warning("请输入请求 URL");
    return;
  }

  loading.value = true;
  response.value = "";

  try {
    const paramsData = paramsRef.value?.getData() || [];
    const { queryParams, bodyParams, pathParamMap } = buildRequestParams(paramsData);
    const finalUrl = resolveUrl(url.value, pathParamMap);

    // 使用 _returnFullResponse 绕过 $common 的数据剥离，拿到完整 axios response
    const axiosConfig: any = {
      url: finalUrl,
      method: method.value,
      _returnFullResponse: true,
    };

    if (method.value === "get" || method.value === "delete") {
      axiosConfig.params = queryParams;
    } else {
      axiosConfig.data = bodyParams;
      // POST/PUT 也支持 query 参数（拼在 URL 上）
      if (Object.keys(queryParams).length > 0) {
        axiosConfig.params = queryParams;
      }
    }

    const res = await request(axiosConfig);

    // 输出后端完整响应体：{ code, msg, success, data }
    response.value = JSON.stringify(res.data ?? {}, null, 2);
  } catch (error: any) {
    // 网络错误 / 超时等
    if (error.response) {
      // 服务端返回了错误响应
      response.value = JSON.stringify(error.response.data ?? {}, null, 2);
    } else {
      // 请求未到达服务端（网络断开、CORS 等）
      response.value = JSON.stringify({ error: error.message || "请求失败" }, null, 2);
    }
  } finally {
    loading.value = false;
  }
};

onMounted(async () => {
  const { id } = route.query;

  if (id) {
    await fetchServiceData(id as string);
  }
});
</script>

<style scoped lang="scss">
.api-test {
  gap: 20px;
  .api-test-panel {
    flex: 1;

    .api-test-res {
      font-size: 16px;
      margin: 0;
      color: rgba(0, 0, 0, 0.88);
      font-weight: 700;
    }
    .api-test-req {
      font-size: 14px;
      margin: 0;
      color: rgba(0, 0, 0, 0.88);
      font-weight: 700;
    }
    .api-test-table {
      min-height: 200px;
      position: relative;
      :deep(.vxe-grid--toolbar-wrapper) {
        position: absolute;
        right: 0;
        top: -31px;
      }
    }
  }
  .formItem {
    margin-bottom: 15px;
    display: flex;
    align-items: center;
    gap: 10px;

    label {
      width: 70px;
    }
  }
}
</style>
