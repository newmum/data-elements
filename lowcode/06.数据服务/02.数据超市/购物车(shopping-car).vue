<template>
  <button
    class="shopping-cart"
    type="button"
    :aria-label="`购物车，${cartCount} 项`"
    aria-haspopup="dialog"
    @click="open"
  >
    <el-badge :value="cartCount" :max="99" :offset="[2, 0]" aria-hidden="true">
      <Icon icon="el-icon-ShoppingCart" size="20" />
    </el-badge>
  </button>
</template>

<script setup lang="ts">
import { watch } from "vue";
import { useRegisterModal, useCartCount } from "@/composables";

const { openRegisterModal } = useRegisterModal();
const { cartCount, refresh, sessionRevision } = useCartCount();

function open() {
  openRegisterModal({
    registerClass: "car-apply",
    registerData: {},
  });
}

watch(sessionRevision, () => void refresh().catch(() => {}), { immediate: true });
</script>

<style scoped>
.shopping-cart {
  display: inline-grid;
  place-items: center;
  flex: none;
  width: 28px;
  height: 36px;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: inherit;
  cursor: pointer;
}

.shopping-cart:focus-visible {
  outline: 2px solid currentColor;
  outline-offset: 2px;
}

.shopping-cart :deep(.el-badge) {
  line-height: 1;
}
</style>
