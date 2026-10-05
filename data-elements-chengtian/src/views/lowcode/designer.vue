<template>
  <div v-loading="saving" class="h-full" :element-loading-text="savingText">
    <FcDesigner ref="designer" :config="config" @save="handleSave">
      <template #handle></template>
    </FcDesigner>

    <!-- 隐藏的预览容器，用于生成截图 -->
    <div ref="previewRef" class="preview-hidden-container">
      <form-create
        v-if="previewData.rule.length"
        :rule="previewData.rule"
        :option="previewData.option"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, reactive, nextTick } from "vue";
import { useRoute } from "vue-router";
import FcDesigner, { Config } from "@form-create/designer";
import titleRule from "@/views/lowcode/rules/u-title";
import html2canvas from "html2canvas";
import FileAPI from "@/api/file-api";

const route = useRoute();
const tid = ref<string>("");
const pid = ref<string>("");
const tabId = ref<string>("");
const designer = ref<typeof FcDesigner>();
const previewRef = ref<HTMLElement>();
const previewData = reactive({
  rule: [] as any[],
  option: {} as any,
});

// 保存状态
const saving = ref(false);
const savingText = ref("正在保存...");

const options = ref({
  submitBtn: true,
  labelPosition: "right",
});
const config: Config = {
  fieldReadonly: false,
  showSaveBtn: true,
};
const rule = ref([
  {
    type: "UTitle",
    props: {
      name: "基础信息",
    },
  },
]);

// 生成预览截图
const generatePreviewScreenshot = async () => {
  if (!previewRef.value) {
    console.error("预览容器未找到");
    return null;
  }

  try {
    const container = previewRef.value;

    // 检查容器尺寸
    const width = container.offsetWidth;
    const height = container.offsetHeight;

    if (width === 0 || height === 0) {
      console.error("预览容器尺寸为 0，无法截图");
      return null;
    }

    // 使用 html2canvas 生成截图
    const canvas = await html2canvas(container, {
      scale: 2,
      useCORS: true,
      logging: true, // 开启日志方便调试
      backgroundColor: "#ffffff",
      width,
      height,
    });

    const base64Image = canvas.toDataURL("image/png");
    return base64Image;
  } catch (error) {
    console.error("生成截图失败:", error);
    return null;
  }
};

// 上传截图
const uploadThumbnail = async (data: any) => {
  const parsedRules = JSON.parse(data?.rule || "[]");
  if (parsedRules.length === 0) {
    console.warn("rules 不是数组或为空，跳过缩略图生成");
    return false;
  }

  previewData.rule = parsedRules;
  previewData.option = JSON.parse(data?.options || "{}");

  // 等待 DOM 更新
  await nextTick();
  // 等待表单渲染
  savingText.value = "正在生成缩略图...";
  await new Promise((resolve) => setTimeout(resolve, 1000));

  const base64Data = await generatePreviewScreenshot();
  if (!base64Data) {
    console.error("生成缩略图失败");
    previewData.rule = [];
    return false;
  }

  savingText.value = "正在上传缩略图...";
  try {
    const fileInfo = (await FileAPI.uploadBase64(
      base64Data,
      `form_thumbnail_${$common.uuid()}.jpg`,
      "image/jpeg"
    )) as any;
    const thumbnail = fileInfo?.preView || "";

    // 清空预览数据
    previewData.rule = [];

    if (!thumbnail) {
      console.warn("上传后未获取到文件URL");
      return false;
    }

    // 保存缩略图URL到表单
    await $common.post("/das/repository/tab/saveTabThumbnail", {
      tid: tabId.value || undefined,
      thumbnail,
    });
    return true;
  } catch (error) {
    console.error("上传缩略图失败:", error);
    previewData.rule = [];
    return false;
  }
};

// 绑定页签
const bindTab = async (tabId: string, tid: string) => {
  return await $common.post("/das/repository/tab/bind", {
    pid: tabId,
    tid,
  });
};

const handleSave = async (data: any) => {
  saving.value = true;
  savingText.value = "正在保存...";

  try {
    // 保存设计规则
    const options = JSON.parse(data?.options || "{}");
    savingText.value = "正在保存表单...";
    const res = (await $common.post("/sym/form?action=save", {
      tid: tid.value || undefined,
      env: pid.value,
      formName: options?.formName || "未定义",
      formJson: JSON.stringify(data),
    })) as any;

    // 获取表单ID
    const formTid = res || tid.value;
    if (!formTid) {
      ElMessage.warning("保存失败");
      saving.value = false;
      return;
    }

    // 更新 tid
    if (!tid.value && res) {
      tid.value = res;
      await bindTab(tabId.value, res);
    }

    // 生成并上传缩略图（等待完成）
    await uploadThumbnail(data);

    saving.value = false;
    ElMessage.success("保存成功");
  } catch (error) {
    console.log(error);
    saving.value = false;
    if (!error?.handled) {
      ElMessage.warning("保存失败");
    }
  }
};

const refresh = async () => {
  if (!tid.value || !pid.value) return;
  const res = (await $common.post("/sym/form?action=get", {
    tid: tid.value,
    env: pid.value,
  })) as any;
  if (res && res.formJson) {
    const formJson = JSON.parse(res.formJson);
    options.value = JSON.parse(formJson.options || "{}");
    rule.value = JSON.parse(formJson.rule || "[]");
    designer.value?.setRule(rule.value);
    designer.value?.setOption(options.value);
  }
};

watch(
  () => [route.query.tid, route.query.pid, route.query.tabId],
  ([newTid, newPid, newTabId]) => {
    tid.value = (newTid as string) || "";
    pid.value = (newPid as string) || "";
    tabId.value = (newTabId as string) || "";
    refresh();
  },
  { immediate: true }
);

onMounted(() => {
  designer.value?.addComponent(titleRule);
  designer.value?.setRule(rule.value);
  designer.value?.setOption(options.value);
});
</script>

<style lang="scss">
.el-container ._fc-m-con {
  padding: 12px 12px 0;
}

/* 隐藏的预览容器，用于生成截图 */
.preview-hidden-container {
  position: fixed;
  top: 0;
  left: -9999px;
  z-index: -1;
  width: 800px;
  padding: 20px;
  background: #fff;
}
</style>
