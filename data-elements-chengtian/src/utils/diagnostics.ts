import { defineComponent, h, onMounted, reactive, ref } from "vue";
import { ElButton } from "element-plus";
import { AuthStorage } from "@/utils/auth";
import type { ApiErrorInfo } from "./api-error";

export const diagnosticState = reactive({ visible: false, sequence: 0, info: null as ApiErrorInfo | null });
const available = ref(false);
let checkedToken = "", pending: Promise<void> | undefined;
const base = (import.meta.env.VITE_APP_BASE_API || "").replace(/\/$/, "");
export const diagnosticUrl = (suffix = "") => base + "/sym/ai/diagnostics" + suffix;
export const diagnosticToken = () => AuthStorage.getAccessToken();
export function diagnosticHeaders(token: string) { return { token, "Content-Type": "application/json" }; }

async function checkAvailable() {
  const token = diagnosticToken();
  if (!token) { available.value = false; return; }
  if (token === checkedToken) return pending;
  checkedToken = token; available.value = false;
  pending = fetch(diagnosticUrl("/capabilities"), { headers: diagnosticHeaders(token), cache: "no-store", signal: AbortSignal.timeout(10000) })
    .then(async response => { const result = await response.json(); if (token === diagnosticToken() && response.ok) available.value = result.enabled === true; })
    .catch(() => { /* Original error viewing remains available. */ });
  return pending;
}

export const DiagnosticErrorAction = defineComponent({
  props: { info: { type: Object, required: true } },
  setup(props) {
    onMounted(() => void checkAvailable());
    return () => available.value && !String(props.info.path || "").includes("/sym/ai/diagnostics")
      ? h(ElButton, { link: true, type: "primary", style: "flex:none", onClick: () => {
        diagnosticState.info = props.info as ApiErrorInfo; diagnosticState.sequence++; diagnosticState.visible = true;
      } }, () => "AI 排错") : null;
  },
});
