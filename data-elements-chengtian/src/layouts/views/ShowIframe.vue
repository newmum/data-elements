<template>
  <transition name="fade" mode="out-in" appear>
    <iframe :src="url" width="100%" height="100%" frameborder="0"></iframe>
  </transition>
</template>

<script setup>
const route = useRoute();
const url = computed(() => route.meta?.params?.url);
</script>
