<template>
  <el-drawer
    v-if="visible"
    v-model="visible"
    :size="700"
    :with-header="true"
    title="导入数据元"
    body-class="bg-gray-50"
    @closed="onClosed"
  >
    <div>
      <el-card shadow="never" body-class="flex flex-col gap-6">
        <div class="import-notice">
          <div class="flex-y-center gap-2 text-primary">
            <Icon icon="info" class="text-primary" />
            <span>导入说明</span>
          </div>
          <div class="ml-6 mt-2">
            <el-text type="info">
              <div>1. 模板包含“数据元”和“数据字典”两张工作表，数据字典通过数据元英文名关联。</div>
              <div>2. 支持上传模板导出的 xlsx 文件；单次最多导入 200 个数据元。</div>
              <div>3. 系统会先校验必填项、字段类型、重复代码及关联关系，校验通过后再保存。</div>
            </el-text>
          </div>
        </div>
        <div class="flex-x-between">
          <h5>第一步：获取模板</h5>
          <el-button type="primary" plain icon="download" @click="handleDownloadTemplate">
            下载导入模板
          </el-button>
        </div>
        <div>
          <h5>第二步：上传填写好的文件</h5>
          <el-upload
            drag
            :file-list="uploadFiles"
            :auto-upload="false"
            accept=".xlsx"
            @change="onUploadChange"
          >
            <div class="flex flex-col items-center justify-center h-36">
              <el-icon class="text-4xl mb-2"><UploadFilled /></el-icon>
              <el-text>点击或拖拽文件到此处上传</el-text>
              <el-text type="info">仅支持 .xlsx 格式文件</el-text>
            </div>
          </el-upload>
        </div>
      </el-card>
    </div>
    <template #footer>
      <el-button type="primary" @click="handleParse">解析文件</el-button>
      <el-button icon="close" @click="visible = false">关闭</el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { UploadFilled } from "@element-plus/icons-vue";

const emit = defineEmits<{
  (e: "download-template"): void;
  (e: "parse", files: any[]): void;
}>();

const visible = ref(false);
const uploadFiles = ref<any[]>([]);

/** 外部调用：打开抽屉 */
const open = () => {
  uploadFiles.value = [];
  visible.value = true;
};

const onClosed = () => {
  uploadFiles.value = [];
};

const onUploadChange = (_file: any, files: any[]) => {
  uploadFiles.value = files;
};

const handleDownloadTemplate = () => {
  emit("download-template");
};

const handleParse = () => {
  emit("parse", uploadFiles.value);
};

const close = () => {
  visible.value = false;
};

defineExpose({ open, close });
</script>
