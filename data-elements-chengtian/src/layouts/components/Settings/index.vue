<template>
  <el-drawer
    v-model="drawerVisible"
    size="380"
    :title="'项目设置'"
    :before-close="handleCloseDrawer"
    class="settings-drawer"
  >
    <div class="settings-content">
      <!-- 界面设置 -->
      <section class="config-section">
        <el-divider>界面设置</el-divider>

        <div class="config-item flex-x-between">
          <span class="text-xs">主题颜色</span>
          <el-color-picker
            v-model="selectedThemeColor"
            :predefine="colorPresets"
            popper-class="theme-picker-dropdown"
          />
        </div>

        <div class="config-item flex-x-between">
          <span class="text-xs">系统标题</span>
          <el-input
            v-model="settingsStore.systemName"
            placeholder="请输入系统标题"
            size="small"
            style="width: 180px"
          />
        </div>

        <div class="config-item flex-x-between">
          <span class="text-xs">显示标签页</span>
          <el-switch v-model="settingsStore.showTagsView" />
        </div>

        <div class="config-item flex-x-between">
          <span class="text-xs">显示应用Logo</span>
          <el-switch v-model="settingsStore.showLogo" />
        </div>

        <div class="config-item flex-x-between">
          <span class="text-xs">显示水印</span>
          <el-switch v-model="settingsStore.showWatermark" />
        </div>
      </section>
    </div>

    <!-- 操作按钮区域 - 固定到底部 -->
    <div class="action-footer">
      <div class="action-divider"></div>
      <div class="action-card">
        <div class="action-buttons">
          <el-tooltip
            content="复制配置将生成当前设置的代码，覆盖 src/settings.ts 下的 defaultSettings 变量"
            placement="top"
          >
            <el-button
              type="primary"
              size="default"
              :icon="copyIcon"
              :loading="copyLoading"
              class="action-btn"
              @click="handleCopySettings"
            >
              {{ copyLoading ? "复制中..." : "复制配置" }}
            </el-button>
          </el-tooltip>
          <el-tooltip content="重置将恢复所有设置为默认值" placement="top">
            <el-button
              type="warning"
              size="default"
              :icon="resetIcon"
              :loading="resetLoading"
              class="action-btn"
              @click="handleResetSettings"
            >
              {{ resetLoading ? "重置中..." : "重置配置" }}
            </el-button>
          </el-tooltip>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { DocumentCopy, RefreshLeft } from "@element-plus/icons-vue";
import { useSettingStore } from "@/store";
import { themeColorPresets } from "@/settings";

// 按钮图标
const copyIcon = markRaw(DocumentCopy);
const resetIcon = markRaw(RefreshLeft);

// 加载状态
const copyLoading = ref(false);
const resetLoading = ref(false);

// 使用统一的颜色预设配置
const colorPresets = themeColorPresets;

const settingsStore = useSettingStore();

const selectedThemeColor = computed({
  get: () => settingsStore.themeColor,
  set: (value) => settingsStore.updateThemeColor(value),
});

const drawerVisible = computed({
  get: () => settingsStore.settingsVisible,
  set: (value) => (settingsStore.settingsVisible = value),
});

/**
 * 复制当前配置
 */
const handleCopySettings = async () => {
  try {
    copyLoading.value = true;

    // 生成配置代码
    const configCode = generateSettingsCode();

    // 复制到剪贴板
    await navigator.clipboard.writeText(configCode);

    // 显示成功消息
    ElMessage.success({
      message: "复制成功",
      duration: 3000,
    });
  } catch {
    ElMessage.warning("复制配置失败");
  } finally {
    copyLoading.value = false;
  }
};

/**
 * 重置为默认配置
 */
const handleResetSettings = async () => {
  resetLoading.value = true;

  try {
    settingsStore.resetSettings();

    ElMessage.success("重置成功");
  } catch {
    ElMessage.warning("重置配置失败");
  } finally {
    resetLoading.value = false;
  }
};

/**
 * 生成配置代码字符串
 */
const generateSettingsCode = (): string => {
  const settings = {
    systemName: "pkg.name",
    version: "pkg.version",
    showSettings: true,
    showTagsView: settingsStore.showTagsView,
    showLogo: settingsStore.showLogo,
    themeColor: `"${settingsStore.themeColor}"`,
    showWatermark: settingsStore.showWatermark,
    watermarkContent: "pkg.name",
  };

  return `const defaultSettings: AppSettings = {
  systemName: ${settings.systemName},
  version: ${settings.version},
  showSettings: ${settings.showSettings},
  showTagsView: ${settings.showTagsView},
  showLogo: ${settings.showLogo},
  themeColor: ${settings.themeColor},
  showWatermark: ${settings.showWatermark},
  watermarkContent: ${settings.watermarkContent},
};`;
};

/**
 * 关闭抽屉前的回调
 */
const handleCloseDrawer = () => {
  settingsStore.settingsVisible = false;
};
</script>

<style lang="scss" scoped>
/* 设置抽屉样式 */
.settings-drawer {
  :deep(.el-drawer__body) {
    position: relative;
    height: 100%;
    padding: 0;
    overflow: hidden;
  }
}

/* 设置内容区域 */
.settings-content {
  height: calc(100vh - 120px); /* 减去头部和底部按钮的高度 */
  padding: 20px;
  padding-bottom: 20px;
  overflow-y: auto;
}

/* 底部操作区域样式 */
.action-footer {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 10;
  padding: 0;
  background: var(--el-bg-color);
  border-top: 1px solid var(--el-border-color-light);

  .action-divider {
    display: none; /* 移除重复的分割线 */
  }

  .action-card {
    padding: 16px 20px;
    margin: 0;
    background: var(--el-fill-color-extra-light);
    border: none;
    border-radius: 0;

    .action-buttons {
      display: flex;
      gap: 12px;

      .action-btn {
        flex: 1;
        font-size: 14px;
        border-radius: 8px;
        transition: all 0.3s ease;

        &:hover {
          box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
          transform: translateY(-2px);
        }
      }
    }
  }
}

/* 主题切换器优化 */
.theme-switch {
  transform: scale(1.2);
  transition: all 0.3s ease;

  &:hover {
    transform: scale(1.25);
  }
}

.config-section {
  margin-bottom: 24px;

  .config-item {
    padding: 12px 0;
    border-bottom: 1px solid var(--el-border-color-light);
    transition: all 0.3s ease;

    &:last-child {
      border-bottom: none;
    }

    &:hover {
      padding-right: 8px;
      padding-left: 8px;
      margin: 0 -8px;
      background-color: var(--el-fill-color-light);
      border-radius: 6px;
    }
  }
}

/* 布局选择器样式优化 */
.layout-select {
  padding: 16px 8px;

  .layout-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 12px;
    justify-items: center;
  }
}

.layout-item {
  position: relative;
  width: 70px;
  height: 80px;
  overflow: hidden;
  cursor: pointer;
  background: linear-gradient(145deg, #ffffff 0%, #f8fafc 100%);
  border: 2px solid var(--el-border-color-light);
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);

  &:hover {
    background: linear-gradient(145deg, #ffffff 0%, var(--el-color-primary-light-9) 100%);
    border-color: var(--el-color-primary-light-3);
    transform: translateY(-4px) scale(1.05);
  }

  &:active {
    transform: translateY(-2px) scale(1.02);
  }

  .layout-preview {
    position: relative;
    width: 100%;
    height: 50px;
    margin: 8px 0 4px 0;
  }

  .layout-header {
    position: absolute;
    top: 0;
    right: 4px;
    left: 4px;
    height: 8px;
    background: linear-gradient(
      90deg,
      var(--el-color-primary) 0%,
      var(--el-color-primary-light-3) 100%
    );
    border-radius: 2px;
  }

  .layout-sidebar {
    position: absolute;
    left: 4px;
    width: 12px;
    background: linear-gradient(
      180deg,
      var(--el-color-primary-dark-2) 0%,
      var(--el-color-primary) 100%
    );
    border-radius: 2px;
  }

  .layout-main {
    position: absolute;
    background: linear-gradient(135deg, #f1f5f9 0%, #e2e8f0 100%);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 2px;
  }

  .layout-name {
    position: absolute;
    right: 0;
    bottom: 6px;
    left: 0;
    font-size: 10px;
    font-weight: 500;
    color: var(--el-text-color-regular);
    text-align: center;
    transition: color 0.3s ease;
  }

  .layout-check {
    position: absolute;
    top: 4px;
    right: 4px;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 16px;
    height: 16px;
    font-size: 10px;
    color: white;
    background: var(--el-color-success);
    border-radius: 50%;
  }

  // 左侧布局
  &.left {
    .layout-sidebar {
      top: 4px;
      bottom: 4px;
    }
    .layout-main {
      top: 4px;
      right: 4px;
      bottom: 4px;
      left: 20px;
    }
  }

  // 顶部布局
  &.top {
    .layout-header {
      height: 12px;
    }
    .layout-main {
      top: 16px;
      right: 4px;
      bottom: 4px;
      left: 4px;
    }
  }

  // 混合布局
  &.mix {
    .layout-header {
      height: 10px;
    }
    .layout-sidebar {
      top: 14px;
      bottom: 4px;
    }
    .layout-main {
      top: 14px;
      right: 4px;
      bottom: 4px;
      left: 20px;
    }
  }

  &.is-active {
    background: linear-gradient(
      145deg,
      var(--el-color-primary-light-9) 0%,
      var(--el-color-primary-light-8) 100%
    );
    border-color: var(--el-color-primary);
    transform: translateY(-2px) scale(1.08);

    .layout-name {
      font-weight: 600;
      color: var(--el-color-primary);
    }
  }
}

/* 深色模式适配 */
.dark {
  .action-footer {
    background: var(--el-bg-color);
    border-top-color: var(--el-border-color);
  }

  .action-card {
    background: var(--el-fill-color-extra-light);
  }

  .layout-item {
    background: linear-gradient(145deg, var(--el-bg-color) 0%, var(--el-bg-color-page) 100%);
    border-color: var(--el-border-color);

    &:hover {
      background: linear-gradient(
        145deg,
        var(--el-bg-color-page) 0%,
        var(--el-color-primary-light-9) 100%
      );
    }

    &.is-active {
      background: linear-gradient(
        145deg,
        var(--el-color-primary-light-9) 0%,
        var(--el-color-primary-light-8) 100%
      );
    }

    .layout-main {
      background: linear-gradient(135deg, var(--el-fill-color) 0%, var(--el-fill-color-light) 100%);
    }
  }
}

/* 复制配置对话框样式 */
:deep(.copy-config-dialog) {
  .el-message-box__content {
    max-height: 400px;
    overflow-y: auto;
  }
}
</style>
