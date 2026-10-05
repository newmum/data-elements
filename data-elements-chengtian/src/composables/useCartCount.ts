import { ref } from "vue";

// 模块级单例，整个应用共享同一个 ref
const cartCount = ref(0);

export function useCartCount() {
  const refresh = async () => {
    const res = await $common.post("/dws/market/shoppingCardList");
    const payload = (res as any)?.data ?? res;
    cartCount.value = Array.isArray(payload)
      ? payload.length
      : Array.isArray(payload?.list)
        ? payload.list.length
        : 0;
  };

  const decrement = (n = 1) => {
    cartCount.value = Math.max(0, cartCount.value - n);
  };

  return { cartCount, refresh, decrement };
}
