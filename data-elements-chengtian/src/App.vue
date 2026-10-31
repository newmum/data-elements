<template>
  <el-config-provider v-bind="config">
    <!-- 开启水印 -->
    <el-watermark
      :font="{ color: 'rgba(0, 0, 0, .15)' }"
      :content="showWatermark ? watermarkContent : ''"
      :z-index="9999"
      class="wh-full"
    >
      <router-view />
    </el-watermark>
  </el-config-provider>
</template>

<script setup lang="ts">
import { useSettingStore } from "@/store";
import zhCn from "element-plus/es/locale/lang/zh-cn";
const settingsStore = useSettingStore();

const showWatermark = computed(() => settingsStore.showWatermark);
const watermarkContent = computed(() => settingsStore.watermarkContent);

const config = reactive({
  locale: zhCn,
  dialog: {
    alignCenter: false,
    draggable: true,
    overflow: true,
  },
  link: {
    type: "primary",
    underline: "never",
  },
  message: {
    plain: true,
    grouping: true,
  },
});
</script>
