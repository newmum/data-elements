<template>
  <el-drawer v-model="diagnosticState.visible" title="AI 排错" size="min(620px, 95vw)" append-to-body @closed="reset">
    <div class="diagnostic-panel">
      <el-alert v-if="diagnosticState.info" :title="diagnosticState.info.message" type="warning" :closable="false" />
      <p class="diagnostic-hint">根据授权证据提供排查建议。修复请在原业务页面完成。</p>
      <div v-if="status" role="status" class="diagnostic-status">{{ status }}</div>
      <el-alert v-if="failure" :title="failure" type="error" :closable="false" />
      <details v-if="evidence.length" class="diagnostic-evidence">
        <summary>本次证据（{{ evidence.length }} 项）</summary>
        <details v-for="item in evidence" :key="item.id">
          <summary>[{{ item.id }}] {{ item.source }} · {{ time(item.observedAt) }}</summary>
          <pre>{{ item.content }}</pre>
        </details>
      </details>
      <div class="diagnostic-messages" aria-live="polite">
        <article v-for="(item, index) in messages" :key="index" :class="item.role">
          <strong>{{ item.role === 'user' ? '我的问题' : '排错建议' }}</strong>
          <pre>{{ item.text || (busy ? '正在分析…' : '本轮未生成完整回答') }}</pre>
        </article>
      </div>
      <div class="diagnostic-compose">
        <el-input v-model="question" type="textarea" :rows="3" :maxlength="2000" placeholder="补充现象，或继续追问；请勿填写密码和业务原文" :disabled="busy" />
        <div class="diagnostic-controls">
          <el-button v-if="busy" @click="stop">停止生成</el-button>
          <el-button v-else type="primary" :disabled="!question.trim()" @click="ask(false)">发送追问</el-button>
          <el-button v-if="!busy && failure" @click="retry">重试本次诊断</el-button>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from "vue";
import { diagnosticState, diagnosticToken, diagnosticHeaders, diagnosticUrl } from "@/utils/diagnostics";
import { readDiagnosticStream, type DiagnosticEvidence } from "@/utils/diagnostic-stream";
const messages = ref<Array<{ role: string; text: string }>>([]), evidence = ref<DiagnosticEvidence[]>([]);
const status = ref(""), failure = ref(""), question = ref(""), busy = ref(false);
let sessionId = "", token = "", generation = "", controller: AbortController | null = null;
let revision = 0;
const time = (value: string) => value ? new Date(value).toLocaleString() : "时间未知";
function current(version: number) { return version === revision && token === diagnosticToken() && diagnosticState.visible; }
async function json(suffix: string, body: unknown, signal: AbortSignal) {
  const response = await fetch(diagnosticUrl(suffix), { method: "POST", headers: diagnosticHeaders(token), body: JSON.stringify(body), signal, cache: "no-store" });
  const result = await response.json();
  if (!response.ok || result.success === false) throw new Error(result.message || result.msg || "诊断请求失败");
  return result;
}
async function cancelRemote(id: string, requestId: string, originalToken: string) {
  if (!id || !requestId || originalToken !== diagnosticToken()) return;
  await fetch(diagnosticUrl('/' + id + '/messages/' + requestId), { method: "DELETE", headers: diagnosticHeaders(originalToken), signal: AbortSignal.timeout(5000) }).catch(() => {});
}
function reset() {
  const oldSession = sessionId, oldGeneration = generation, oldToken = token;
  revision++; controller?.abort(); controller = null;
  void cancelRemote(oldSession, oldGeneration, oldToken);
  sessionId = ""; generation = ""; busy.value = false; messages.value = []; evidence.value = []; failure.value = ""; status.value = ""; question.value = "";
}
async function stop() {
  const oldSession = sessionId, oldGeneration = generation;
  revision++; controller?.abort(); controller = null; status.value = "已停止，已返回内容可能不完整";
  await cancelRemote(oldSession, oldGeneration, token);
  busy.value = false;
}
async function ask(initial = false) {
  if (busy.value) return;
  const version = ++revision, ctrl = new AbortController(); controller = ctrl;
  const text = initial ? "请分析这次故障，并给出有证据的排查建议。" : question.value.trim();
  if (!text) return;
  busy.value = true; failure.value = ""; question.value = ""; status.value = "正在创建诊断会话";
  const requestToken = token;
  const watcher = window.setInterval(() => { if (!current(version)) ctrl.abort(); }, 500);
  try {
    if (!sessionId) {
      const info = diagnosticState.info!;
      const selection = info.diagnosticRef ? { source: "platform", diagnosticRef: info.diagnosticRef }
        : { source: "client", clientError: [info.code, info.message, info.type, info.detail].filter(Boolean).join("\n").slice(0, 8000) };
      const result = await json("", selection, ctrl.signal);
      if (!current(version)) return;
      sessionId = result.sessionId;
    }
    messages.value.push({ role: "user", text });
    const answer = { role: "assistant", text: "" }; messages.value.push(answer);
    const index = messages.value.length - 1;
    generation = Array.from(crypto.getRandomValues(new Uint8Array(16)), value => value.toString(16).padStart(2, "0")).join("");
    const response = await fetch(diagnosticUrl('/' + sessionId + '/messages'), {
      method: "POST", headers: { ...diagnosticHeaders(token), Accept: "text/event-stream" },
      body: JSON.stringify({ message: text, generationId: generation }), signal: ctrl.signal, cache: "no-store",
    });
    await readDiagnosticStream(response, ctrl.signal, ({ event, data }) => {
      if (!current(version)) return;
      if (event === "evidence" && Array.isArray(data)) evidence.value = data;
      if (event === "progress" && !Array.isArray(data)) status.value = String(data.message || "");
      if (event === "delta" && !Array.isArray(data)) messages.value[index].text += String(data.text || "");
      if (event === "done") status.value = "本次分析完成，可继续追问";
    });
  } catch (error) {
    if (current(version) && !ctrl.signal.aborted) { failure.value = error instanceof Error ? error.message : "诊断请求失败"; status.value = ""; }
  } finally {
    clearInterval(watcher);
    if (requestToken !== diagnosticToken()) { reset(); diagnosticState.visible = false; }
    if (current(version)) { busy.value = false; controller = null; }
  }
}
function retry() { if (!busy.value) void ask(true); }
watch(() => diagnosticState.sequence, () => { reset(); token = diagnosticToken(); void ask(true); });
watch(() => diagnosticState.visible, visible => { if (!visible) reset(); });
onBeforeUnmount(reset);
</script>

<style scoped>
.diagnostic-panel{display:flex;flex-direction:column;gap:12px;min-height:100%;color:var(--el-text-color-primary)}
.diagnostic-hint{margin:0;color:var(--el-text-color-secondary);font-size:13px}.diagnostic-status{color:var(--el-color-primary);font-size:13px}
.diagnostic-evidence{border:1px solid var(--el-border-color);padding:12px;border-radius:8px}.diagnostic-evidence details{margin-top:10px}summary{cursor:pointer;font-size:13px}
pre{white-space:pre-wrap;overflow-wrap:anywhere;font-family:inherit;font-size:13px;line-height:1.7;margin:8px 0;max-height:45vh;overflow:auto}
.diagnostic-messages{flex:1}.diagnostic-messages pre{max-height:none;overflow:visible}.diagnostic-messages article{padding:14px;margin-bottom:12px;border-radius:8px;background:var(--el-fill-color-light)}.diagnostic-messages .user{background:var(--el-color-primary-light-9)}
.diagnostic-compose{position:sticky;bottom:0;background:var(--el-bg-color);padding-top:12px}.diagnostic-controls{display:flex;justify-content:flex-end;margin-top:10px}
</style>
