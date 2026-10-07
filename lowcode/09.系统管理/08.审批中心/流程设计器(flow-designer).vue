<template>
  <main class="flow-workbench card-container flex h-full flex-col">
    <header class="workbench-header">
      <div class="title-wrap">
        <el-button circle plain icon="arrow-left" title="返回流程管理" @click="router.back()" />
        <div>
          <h2>{{ title }}</h2>
          <span>业务流程设计</span>
        </div>
      </div>
      <div class="header-actions">
        <el-button plain icon="refresh" :loading="state.loading" @click="reload">重新加载</el-button>
      </div>
    </header>

    <section v-if="flowId" class="workbench-body">
      <aside class="flow-summary">
        <div class="summary-title">流程概览</div>
        <div class="summary-card">
          <span class="summary-label">流程名称</span>
          <strong :title="title">{{ title }}</strong>
        </div>
        <div class="summary-card">
          <span class="summary-label">设计内容</span>
          <div class="step-list">
            <span><i class="step-dot start"></i>开始</span>
            <span><i class="step-dot"></i>办理环节</span>
            <span><i class="step-dot end"></i>结束</span>
          </div>
        </div>
      </aside>
      <section class="designer-stage" v-loading="state.loading" element-loading-text="正在加载流程设计器">
        <iframe :key="state.frameKey" :src="designerUrl" title="业务流程设计" @load="state.loading = false"></iframe>
      </section>
    </section>
    <el-empty v-else description="未找到需要设计的流程" />
  </main>
</template>

<script setup>
import { computed, reactive } from 'vue';
import { useRoute, useRouter } from 'vue-router';

const route = useRoute();
const router = useRouter();
const state = reactive({ loading: true, frameKey: 0 });
const flowId = computed(() => String(route.query.id || ''));
const title = computed(() => String(route.query.title || '未命名流程'));
const designerUrl = computed(() => `/dev-api/warm-flow-ui/index.html?id=${encodeURIComponent(flowId.value)}&disabled=false`);
const reload = () => {
  state.loading = true;
  state.frameKey += 1;
};
</script>

<style scoped lang="scss">
.flow-workbench { min-width: 0; overflow: hidden; padding: 0; }
.workbench-header { display: flex; align-items: center; justify-content: space-between; min-height: 68px; padding: 10px 20px; border-bottom: 1px solid #e4e7ed; background: #fff; }
.title-wrap, .header-actions { display: flex; align-items: center; gap: 12px; }
.title-wrap h2 { margin: 0; color: #1d2939; font-size: 18px; line-height: 26px; }
.title-wrap span, .summary-label { color: #667085; font-size: 12px; }
.workbench-body { display: grid; grid-template-columns: 204px minmax(0, 1fr); flex: 1; min-height: 0; background: #f5f7fa; }
.flow-summary { padding: 16px; border-right: 1px solid #e4e7ed; background: #fff; }
.summary-title { margin: 2px 0 14px; color: #1d2939; font-size: 14px; font-weight: 600; }
.summary-card { display: flex; flex-direction: column; gap: 8px; margin-bottom: 12px; padding: 12px; border: 1px solid #e4e7ed; border-radius: 6px; }
.summary-card strong { overflow: hidden; color: #344054; font-size: 13px; line-height: 20px; text-overflow: ellipsis; white-space: nowrap; }
.step-list { display: flex; flex-direction: column; gap: 10px; color: #475467; font-size: 13px; }
.step-list span { display: flex; align-items: center; gap: 8px; }
.step-dot { width: 8px; height: 8px; border-radius: 50%; background: #2f6bff; }
.step-dot.start { background: #12b76a; }.step-dot.end { background: #98a2b3; }
.designer-stage { position: relative; min-width: 0; min-height: 0; padding: 12px; }
.designer-stage iframe { display: block; width: 100%; height: 100%; min-height: 560px; border: 1px solid #e4e7ed; border-radius: 6px; background: #fff; }
@media (max-width: 900px) { .workbench-body { grid-template-columns: 1fr; } .flow-summary { display: none; } .designer-stage { padding: 8px; } }
</style>
