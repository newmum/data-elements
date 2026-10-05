<template>
  <main class="pingao-login-result">
    <h1>警综统一登录</h1>
    <p role="status">{{ message }}</p>
    <button v-if="failed" type="button" @click="retry">重新登录</button>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { PingaoSsoAPI, pingaoLoginUrl, pingaoSsoEnabled } from "@/api/pingao-sso-api";
import { AuthStorage } from "@/utils/auth";
import { useUserStore } from "@/store";

const route = useRoute();
const router = useRouter();
const message = ref("正在确认登录身份…");
const failed = ref(false);
function retry() { window.location.assign(pingaoLoginUrl()); }

onMounted(async () => {
  const ticket = typeof route.query.exchange === "string" ? route.query.exchange : "";
  // Remove the one-use credential before loading any business page or runtime component.
  window.history.replaceState(null, "", `${window.location.pathname}${window.location.search}#/pingao-sso/callback`);
  if (!pingaoSsoEnabled() || !/^[A-Za-z0-9_-]{43}$/.test(ticket)) {
    message.value = "登录入口未启用或凭据已失效，请联系管理员。";
    failed.value = true;
    return;
  }
  try {
    await useUserStore().resetAllState();
    const result = await PingaoSsoAPI.exchange(ticket);
    AuthStorage.setTokens(result.token, false);
    sessionStorage.setItem("pingao-sso-session", "1");
    await router.replace({ path: "/", query: { __loginRecovery: "1" } });
  } catch {
    AuthStorage.clearAuth();
    sessionStorage.removeItem("pingao-sso-session");
    message.value = "登录未完成：请确认账号已同步、身份映射已确认且已分配本平台角色。";
    failed.value = true;
  }
});
</script>

<style scoped>
.pingao-login-result { max-width: 36rem; margin: 15vh auto; padding: 2rem; line-height: 1.8; }
</style>
