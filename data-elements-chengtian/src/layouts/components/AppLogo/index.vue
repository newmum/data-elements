<template>
  <div class="flex-center ml-24px mr-24px">
    <div class="logo" :class="[theme === 'dark' ? 'logo-light-text' : 'logo-dark-text']">
      <transition enter-active-class="animate__animated animate__fadeInLeft">
        <span :key="+collapse" class="wh-full flex-center" to="/">
          <img
            v-if="settingsStore.showLogo && settingsStore.logo"
            :src="settingsStore.logo"
            class="wauto h25px mr-12px cursor-pointer"
            :alt="settingsStore.systemName"
            @click="settingsStore.navVisible = !settingsStore.navVisible"
          />
          <span class="title">
            {{ settingsStore.systemName }}
          </span>
        </span>
      </transition>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { useSettingStore } from "@/store";

defineProps({
  collapse: {
    type: Boolean,
    required: true,
  },
  theme: {
    type: String as PropType<"dark" | "light">,
    default: "dark",
  },
});
const settingsStore = useSettingStore();
</script>

<style lang="scss" scoped>
.logo {
  width: 100%;
  height: $navbar-height;

  .title {
    flex-shrink: 0;
    font-family: "Source Han Sans SC", sans-serif;
    font-size: 18px;
    font-weight: 600;
    letter-spacing: 4px;
  }
}
.logo-dark-text .title {
  color: #323643;
}
.logo-light-text .title {
  color: #fff;
}
</style>
