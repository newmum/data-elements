<template>
  <div class="workspace-loading">
    <div class="workspace-loading__panel" role="status">
      <div class="workspace-loading__brand">
        <img
          class="workspace-loading__mark"
          :src="settingsStore.logo || fallbackLogo"
          alt=""
          @error="showFallbackLogo"
        />
        <div class="workspace-loading__copy">
          <strong class="workspace-loading__label">{{ settingsStore.systemName }}</strong>
          <small>正在初始化应用资源</small>
        </div>
      </div>
      <span class="workspace-loading__track" aria-hidden="true"></span>
    </div>
  </div>
</template>

<script setup lang="ts">
import fallbackLogo from "@/assets/images/workplace/iconDatabase.svg";
import { useSettingStore } from "@/store";

const settingsStore = useSettingStore();

function showFallbackLogo(event: Event) {
  const image = event.currentTarget;
  if (image instanceof HTMLImageElement && image.getAttribute("src") !== fallbackLogo) {
    image.src = fallbackLogo;
  }
}
</script>
