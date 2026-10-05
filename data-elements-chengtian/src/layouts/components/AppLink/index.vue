<template>
  <template v-if="linkType === 'a'">
    <a v-bind="linkProps(to)" @click.prevent="openUrl(to)">
      <slot />
    </a>
  </template>
  <template v-if="linkType === 'router-link'">
    <router-link :to="{ path: to.path }">
      <slot />
    </router-link>
  </template>
</template>

<script setup lang="ts">
defineOptions({
  name: "AppLink",
  inheritAttrs: false,
});

import { isExternal } from "@/utils";

const props = defineProps({
  to: {
    type: Object,
    required: true,
  },
});

const isExternalLink = computed(() => {
  return isExternal(props.to.path || "");
});

const linkType = computed(() => (isExternalLink.value ? "a" : "router-link"));

const linkProps = (to: any) => {
  if (isExternalLink.value) {
    return {
      href: to.path,
      target: to.meta.target ?? "_blank",
      rel: "noopener noreferrer",
    };
  }
  // 路由跳转
  return { to: { path: to.path } };
};

const openUrl = (to: any) => {
  if (to.meta.ticket) {
    $common.get("/portal/sso/getTicket").then((ticket) => {
      window.open(
        to.path.includes("?") ? `${to.path}&ticket=${ticket}` : `${to.path}?ticket=${ticket}`,
        to.meta.target ?? "_blank"
      );
    });
  } else {
    window.open(to.path, to.meta.target ?? "_blank");
  }
};
</script>
