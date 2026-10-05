<template>
  <div class="image-preview-container mx-auto w-full px-5 py-5">
    <!-- 头部区域 -->
    <div class="header text-center mb-7.5">
      <h1 class="text-[#323643] mb-2">背景图样式预览</h1>
      <p class="text-[#828691] mb-4">自动生成的背景图样式列表，点击复制类名/变量名</p>
      <el-button type="primary" @click="refreshStyles">刷新样式列表</el-button>
    </div>

    <!-- 文件夹筛选 -->
    <div class="folder-filter mb-5 p-2.5 bg-[#f5f7fa] rounded-2xl">
      <el-radio-group
        v-model="activeFolder"
        class="flex flex-wrap gap-2.5"
        @change="handleFolderChange"
      >
        <el-radio label="all">全部文件夹</el-radio>
        <el-radio v-for="folder in Object.keys(imageStyles)" :key="folder" :label="folder">
          /{{ folder }}
        </el-radio>
      </el-radio-group>
    </div>

    <!-- 背景图预览列表 -->
    <div
      class="image-list grid gap-5"
      style="grid-template-columns: repeat(auto-fill, minmax(280px, 1fr))"
    >
      <div
        v-for="(item, index) in showImages"
        :key="index"
        class="image-card border border-[#e5e6eb] rounded-3xl overflow-hidden cursor-pointer transition-all duration-300 hover:shadow-[0_4px_12px_rgba(0,0,0,0.08)] hover:-translate-y-0.5"
        @click="copyToClipboard(item.className)"
      >
        <!-- 图片预览区 -->
        <div
          class="preview-box w-full h-[180px] bg-[#f8f9fa] bg-no-repeat bg-contain bg-center flex items-center justify-center"
          :style="{ backgroundImage: `var(${item.varName})` }"
        >
          <Empty
            v-if="!item.imgUrl"
            type="image"
            compact
            :image-size="54"
            title="暂无图片"
            description="图片资源未生成"
          />
        </div>

        <!-- 信息区 -->
        <div class="info-box p-4">
          <div class="display-name text-lg font-semibold text-[#323643] mb-3">
            {{ item.displayName }}
          </div>
          <div class="code-item flex mb-2">
            <span class="label text-[#828691] w-[60px] flex-shrink-0">类名：</span>
            <span class="code text-[#1b67f8] font-['Consolas',monospace] break-all">
              {{ item.className }}
            </span>
          </div>
          <div class="code-item flex mb-2">
            <span class="label text-[#828691] w-[60px] flex-shrink-0">变量：</span>
            <span class="code text-[#1b67f8] font-['Consolas',monospace] break-all">
              var({{ item.varName }})
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import { ElMessage } from "element-plus";

function getImageStyles() {
  const styles: any = {};
  const rootStyle: any = getComputedStyle(document.documentElement);
  const prefix = "--images-"; // 固定前缀

  // 匹配 --images- 开头的变量
  for (const key of rootStyle) {
    if (key.startsWith(prefix)) {
      // 去掉前缀后拆分：格式为 目录名-文件名（文件名可能包含-）
      const keyWithoutPrefix = key.replace(prefix, "");
      // 拆分出目录名和文件名（第一个-分隔目录和文件名，后面的-都属于文件名）
      const firstDashIndex = keyWithoutPrefix.indexOf("-");

      let folder = "";
      let fileName = "";
      if (firstDashIndex > -1) {
        // 有目录名的情况（如 workplace-icon-我的利益-2x）
        folder = keyWithoutPrefix.substring(0, firstDashIndex);
        fileName = keyWithoutPrefix.substring(firstDashIndex + 1);
      } else {
        // 极端情况：只有前缀+名称（无目录），归为 root 目录
        folder = "根目录";
        fileName = keyWithoutPrefix;
      }

      // 解析图片URL
      const value = rootStyle.getPropertyValue(key).trim();
      const imgUrl = value.replace(/^url\(["']?/, "").replace(/["']?\)$/, "");

      // 组装样式信息
      if (!styles[folder]) {
        styles[folder] = [];
      }
      styles[folder].push({
        varName: key, // 完整CSS变量名（如 --images-workplace-icon-我的利益-2x）
        className: folder === "根目录" ? `.images-${fileName}` : `.images-${folder}-${fileName}`, // 完整样式类名（如 .images-workplace-icon-我的利益-2x）
        imgUrl, // 图片地址
        displayName: `${folder}/${fileName}`, // 展示名称（如 workplace/icon-我的利益-2x）
        pureFileName: fileName, // 纯文件名（方便后续扩展）
        pureFolder: folder, // 纯目录名（方便后续扩展）
      });
    }
  }

  return styles;
}

// 响应式数据
const imageStyles = ref<any>({}); // 所有图片样式
const activeFolder = ref<any>("all"); // 当前选中的文件夹
const showImages = ref<any>([]); // 要展示的图片列表

// 初始化
onMounted(() => {
  refreshStyles();
});

// 刷新样式列表
const refreshStyles = () => {
  imageStyles.value = getImageStyles();
  handleFolderChange(activeFolder.value);
  ElMessage.success("样式列表已刷新");
};

// 切换文件夹筛选
const handleFolderChange = (val: any) => {
  if (val === "all") {
    // 展示所有
    showImages.value = Object.values(imageStyles.value).flat();
  } else {
    // 展示指定文件夹
    showImages.value = imageStyles.value[val] || [];
  }
};

// 复制到剪贴板
const copyToClipboard = (text: string) => {
  navigator.clipboard
    .writeText(text)
    .then(() => {
      ElMessage.success(`已复制：${text}`);
    })
    .catch(() => {
      ElMessage.warning("复制失败，请手动复制");
    });
};
</script>

<!-- 移除了原有的 scoped SCSS 样式，所有样式都通过 UnoCSS 原子类实现 -->
