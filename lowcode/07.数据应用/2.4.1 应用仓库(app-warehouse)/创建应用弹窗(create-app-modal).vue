<template>
  <u-modal
    v-model="visible"
    :title="mode === 'edit' ? '编辑应用基本信息' : '填写应用基本信息'"
    width="600"
    class="create-app-modal"
    :draggable="false"
    @confirm="handleSubmit"
    @close="handleClose"
  >
    <json-form ref="formRef" :rules="formRules" :options="formOptions">
      <template #field-appIcon="scope">
        <div class="icon-list">
          <div
            v-for="item in imgList"
            :key="item.url"
            :class="[
              'app-button',
              { 'is-active': scope.model.value === item.url },
              item.type === 'local' ? `images-warehouse-${item.url}` : '',
            ]"
            :style="{ backgroundImage: item.type !== 'local' ? `url(${item.url})` : '' }"
            @click="handleIconSelect(item.url, scope)"
          >
            <div
              v-if="item.type !== 'local'"
              class="img-delete"
              @click.stop="removeUploadedIcon(item.url)"
            >
              <Icon icon="el-icon-Close" size="14" />
            </div>
          </div>

          <el-upload
            ref="uploadRef"
            class="upload-trigger"
            :show-file-list="false"
            :on-success="handleSuccess"
            :http-request="handleUpload"
            :before-upload="handleBeforeUpload"
            :disabled="isUploading"
            accept="image/*"
          >
            <div class="upload-button app-button flex items-center justify-center">
              <Icon v-if="!isUploading" icon="el-icon-Plus" size="20" />
              <el-icon v-else class="is-loading">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">
                  <path
                    fill="currentColor"
                    d="M512 0a512 512 0 1 1 0 1024A512 512 0 0 1 512 0zm0 896a384 384 0 1 0 0-768 384 384 0 0 0 0 768zm0 64a448 448 0 1 1 0-896 448 448 0 0 1 0 896z"
                  ></path>
                </svg>
              </el-icon>
            </div>
          </el-upload>
        </div>
      </template>
    </json-form>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button v-if="mode === 'edit'" type="primary" @click="handleSubmit">
        <Icon icon="save" class="mr-1"></Icon>
        保存
      </el-button>
      <el-button v-if="mode === 'create'" type="primary" @click="handleSubmit">
        下一步：进入编排工作台
        <Icon icon="arrow-right" class="ml-2"></Icon>
      </el-button>
    </template>
  </u-modal>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, nextTick } from "vue";
import { FormUtils } from "@/utils/form";
import { useDictStore } from "@/store";
import type { UploadRequestOptions } from "element-plus";
import FileAPI from "@/api/file-api";

const appCategoryOptions = useDictStore().getDictItems("appCategory");

interface FileInfo {
  name: string;
  url: string;
  tid?: string;
}

// 图标接口类型定义
interface IconInfo {
  tid: string;
  tenantId: string | null;
  revision: string | null;
  createdBy: string;
  createdTime: string;
  isDel: number;
  updatedBy: string;
  updatedTime: string;
  orgId: string | null;
  appIcon: string;
}

// Props 定义
interface AppData {
  tid?: string;
  appName?: string;
  appType?: number;
  appIcon?: string;
  description?: string;
  publishStatus?: number;
  createdBy?: string;
  createdTime?: string;
}

const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    appData?: AppData | null;
    appId?: string;
    mode?: "create" | "edit";
  }>(),
  {
    appData: null,
    appId: "",
    mode: "create",
  }
);

const emit = defineEmits(["update:modelValue", "close", "submit"]);
const formRef = ref();
const localIcons = [
  { url: "logo1", type: "local" },
  { url: "logo2", type: "local" },
  { url: "logo3", type: "local" },
  { url: "logo4", type: "local" },
  { url: "logo5", type: "local" },
  { url: "logo6", type: "local" },
];

const imgList = ref<any[]>([...localIcons]);
// 加载状态
const isUploading = ref(false);

// 获取用户上传的图标列表
const fetchUserIcons = async () => {
  try {
    const response = (await $common.get(
      "/das/repository/icon/list"
    )) as unknown as IconInfo[];

    const userIcons: any[] = response.map((icon) => ({
      url: `/fileCenter${icon.appIcon}`,
      ...icon,
    }));

    // 整合本地图标和用户上传图标
    imgList.value = [...localIcons, ...userIcons];

    // form-create 的 slot 有自己的缓存机制，外部响应式数据变化不会自动触发 slot 重渲染
    // 必须手动调用 refresh() 清除缓存，强制重新渲染 #field-icon slot
    await nextTick();
    formRef.value?.refresh();
  } catch (error) {
    console.error("获取用户图标失败:", error);
  }
};

const formRules = ref([]);
formRules.value = FormUtils.fixJson([
  {
    type: "input",
    title: "应用名称",
    field: "appName",
    $required: true,
  },
  {
    type: "select",
    title: "所属分类",
    field: "appType",
    props: {
      options: appCategoryOptions,
    },
    $required: true,
  },
  {
    type: "fieldComponent",
    title: "应用图标",
    field: "appIcon",
    value: "logo1",
    $required: true,
  },
  {
    type: "el-input",
    field: "description",
    title: "应用摘要",
    props: {
      type: "textarea",
      placeholder: "简要描述该应用的业务场景与核心功能...",
      maxlength: 500,
      rows: 4,
      showWordLimit: true,
    },
  },
]);

const formOptions = ref({
  form: {
    labelWidth: "100px",
  },
});

const visible = computed({
  get: () => props.modelValue,
  set: (value) => {
    emit("update:modelValue", value);
  },
});

// 处理图标选择
const handleIconSelect = async (value: any, scope: any) => {
  // 如果 value 是对象，使用其 url 属性
  const iconValue = typeof value === "object" && value.url ? value.url : value;
  scope.model.value = iconValue;
  // 修正字段名为 appIcon，与表单配置一致
  formRef.value?.setValue({
    appIcon: iconValue,
  });
  // 强制刷新表单，确保视图更新
  await nextTick();
  formRef.value?.refresh();
};

// 上传前校验
const handleBeforeUpload = async (file: any) => {
  const isImage = file.type.startsWith("image/");
  if (!isImage) {
    ElMessage.warning("只能上传图片文件！");
    return false;
  }
  const isLt10M = file.size / 1024 / 1024 < 10;
  if (!isLt10M) {
    ElMessage.warning("上传图片大小不能超过 10MB！");
    return false;
  }
  return true;
};

// 处理文件上传
const handleUpload = (options: UploadRequestOptions) => {
  return new Promise((resolve, reject) => {
    FileAPI.uploadFile(options.file)
      .then((res) => {
        resolve(res);
      })
      .catch((err) => {
        reject(err);
      });
  });
};

const handleSuccess = async (res: any) => {
  // 添加到图标库
  await $common.post("/das/repository/icon/add", {
    appIcon: res.preView,
  });
  fetchUserIcons();
};

// 移除上传的图标
const removeUploadedIcon = async (url: string) => {
  const index = imgList.value.findIndex((item) => item.url === url);
  if (index === -1) return;
  const file = imgList.value[index];
  const tid = file.tid || file.response?.tid || "";
  if (!tid) return;
  try {
    // 调用图标库删除接口
    await $common.post("/das/repository/icon/delete", {
      tid,
    });
    // 直接从 imgList 移除，v-for 自动更新视图
    imgList.value.splice(index, 1);
    await nextTick();
    formRef.value?.refresh();
  } catch (error) {
    console.error("删除图标失败:", error);
  }
};

// 提交表单
const handleSubmit = async () => {
  try {
    // 验证表单
    await formRef.value.validate();
    const formData = await formRef.value?.getFormData();
    emit("submit", {
      ...formData,
      tid: props.appId,
    });
    visible.value = false;
  } catch (error) {
    console.error("表单验证失败:", error);
  }
};

// 关闭模态框
const handleClose = () => {
  // 重置表单
  formRef.value?.resetFields();
  visible.value = false;
  emit("close");
};

// 监听 appData 变化，设置表单值
watch(
  () => props.appData,
  (newVal) => {
    if (newVal && props.modelValue) {
      nextTick(() => {
        formRef.value?.setValue(newVal);
      });
    }
  }
);

// 监听 modal 显示状态
watch(
  () => props.modelValue,
  (newVal) => {
    if (newVal && props.appData) {
      nextTick(() => {
        formRef.value?.setValue(props.appData);
      });
    }
  }
);

onMounted(() => {
  // 获取用户上传的图标列表
  fetchUserIcons();
  if (props.appData) {
    nextTick(() => {
      formRef.value?.setValue(props.appData);
    });
  }
});
</script>

<style lang="scss">
.create-app-modal {
  padding: 20px 24px !important;
  .el-dialog__footer {
    border-top: 0;
  }
  .icon-list {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
  }

  .loading-container {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 40px;
    color: #165dff;
    .is-loading {
      margin-right: 8px;
      animation: rotate 1s linear infinite;
    }
  }

  .error-message {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 40px;
    color: #ef4444;
    span {
      margin-left: 8px;
    }
  }

  @keyframes rotate {
    from {
      transform: rotate(0deg);
    }
    to {
      transform: rotate(360deg);
    }
  }

  .app-button {
    width: 48px;
    height: 48px;
    border-radius: 12px;
    margin-left: 3px;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all 0.3s;
    border: 1px solid #f3f4f6;
    background-size: cover;
    color: #9ca3af;
    cursor: pointer;

    &:hover {
      transform: scale(1.1);
    }

    &.is-active {
      border: 1px solid #165dff;
      box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
    }

    .img-delete {
      position: absolute;
      top: -3px;
      right: -3px;
      width: 12px;
      height: 12px;
      border-radius: 50%;
      background-color: #ef4444;
      color: white;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 12px;
      cursor: pointer;
      opacity: 0;
      transition: opacity 0.3s;
      box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
      z-index: 10;

      &:hover {
        background-color: #dc2626;
      }
    }

    &:hover .img-delete {
      opacity: 1;
    }
  }

  .upload-trigger {
    .el-upload {
      width: auto;
      height: auto;
      border: none;
      background: none;
    }
  }

  // 上传按钮样式
  .upload-button {
    border: 1px dashed #d1d5db;
    background-color: #f9fafb;
    flex-direction: column;
    gap: 4px;
    overflow: hidden;

    &:hover {
      border-color: #3b82f6;
      background-color: #eff6ff;
    }
  }
}
</style>
