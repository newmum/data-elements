import { computed, ref } from "vue";
import { AuthStorage } from "@/utils/auth";
import { requestPolicy } from "@/utils/request-policy";

const sessionRevision = computed(() => AuthStorage.getSessionRevision());
const count = ref({ total: 0, revision: -1 });
const cartCount = computed(() =>
  count.value.revision === sessionRevision.value ? count.value.total : 0
);
let readSequence = 0;
let committedRead = 0;

function beginRead() {
  const sequence = ++readSequence;
  const revision = sessionRevision.value;
  const token = AuthStorage.getAccessToken();
  return (total: unknown) => {
    if (
      sequence > committedRead &&
      revision === sessionRevision.value &&
      token === AuthStorage.getAccessToken() &&
      typeof total === "number" &&
      Number.isSafeInteger(total) &&
      total >= 0
    ) {
      committedRead = sequence;
      count.value = { total, revision };
    }
  };
}

async function refresh() {
  if (!AuthStorage.getAccessToken()) return;
  const commit = beginRead();
  const result = await requestPolicy.quiet<{ total: number }>({
    url: "/dws/market/shoppingCardList",
    method: "post",
    data: { mode: "count" },
  });
  commit(result?.total);
}

export function useCartCount() {
  return { cartCount, refresh, beginRead, sessionRevision };
}
