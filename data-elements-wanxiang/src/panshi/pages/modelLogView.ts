import type { ActionLog, ResourceState } from '../domain/types';

export type ModelLogStage = '设计' | '标准化' | '版本' | '物化';

export interface ModelLogRow extends ActionLog {
  modelId: string;
  modelName: string;
  modelCode: string;
  modelArchived: boolean;
  stage: ModelLogStage;
  event: string;
}

const events: Record<string, { stage: ModelLogStage; event: string }> = {
  创建逻辑模型: { stage: '设计', event: '创建模型' },
  复制逻辑模型: { stage: '设计', event: '复制模型' },
  '维护逻辑模型：保存': { stage: '设计', event: '保存模型设计' },
  '维护逻辑模型：reverse': { stage: '设计', event: '导入来源表' },
  '维护逻辑模型：reference': { stage: '设计', event: '引用模型实体' },
  '维护逻辑模型：bind': { stage: '标准化', event: '绑定数据标准' },
  生成模型数据项集: { stage: '标准化', event: '生成数据项集' },
  提交标准候选: { stage: '标准化', event: '提交标准候选' },
  冻结设计版本: { stage: '版本', event: '冻结设计版本' },
  '维护逻辑模型：archive': { stage: '版本', event: '归档模型' },
  创建物化方案: { stage: '物化', event: '创建物化方案' },
  重新预检物化: { stage: '物化', event: '重新预检物化' },
  开始物化建表: { stage: '物化', event: '开始物化建表' },
  物化执行完成: { stage: '物化', event: '物化执行完成' },
  请求取消物化: { stage: '物化', event: '请求取消物化' },
  装载融合模型数据: { stage: '物化', event: '装载融合数据' },
  融合模型装载失败: { stage: '物化', event: '融合装载失败' },
};

export function modelLogRows(state: ResourceState): ModelLogRow[] {
  const models = new Map(state.models.map(model => [model.id, model]));
  const plans = new Map(state.materializations.map(plan => [plan.id, plan]));
  const itemSets = new Map(state.itemSets.map(set => [set.id, set]));
  return state.logs.flatMap(log => {
    const meaning = events[log.action];
    if (!meaning) return [];
    const modelId = models.has(log.objectId)
      ? log.objectId
      : plans.get(log.objectId)?.modelId ?? itemSets.get(log.objectId)?.modelId ?? '';
    const model = models.get(modelId);
    const modelName = model?.name ?? (meaning.stage === '物化' ? '关联模型已不存在' : log.name);
    return [{
      ...log,
      detail: log.detail || '',
      modelId,
      modelName,
      modelCode: model?.code ?? '',
      modelArchived: model?.state === 'ARCHIVED',
      stage: meaning.stage,
      event: meaning.event,
    }];
  }).sort((a, b) => b.time.localeCompare(a.time));
}
