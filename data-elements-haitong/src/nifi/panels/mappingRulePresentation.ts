import type { UnifiedMappingRule } from '../api/fieldMapping';
import { changePrimaryRuleMode, primaryRuleMode, TYPE_LABELS, type RuleStep } from './unifiedRuleModel.ts';

interface RuleChoice { value: string; label: string; description: string }
export const RULE_CHOICE_GROUPS: { label: string; options: RuleChoice[] }[] = [
  { label: '直接取值', options: [
    { value: 'DIRECT', label: '保持原值', description: '将源字段的值写入目标字段，保留已有空值处理和后续步骤。' },
    { value: 'CONSTANT', label: '填入固定值', description: '为每条记录写入相同的文本、数字或其他固定值。' },
    { value: 'UUID32', label: '生成 UUID（32 位）', description: '为每条记录生成无连字符的唯一标识，适用于 ODS 主键。' },
    { value: 'UUID', label: '生成 UUID（36 位）', description: '为每条记录生成带连字符的唯一标识。' },
    { value: 'CURRENT_TIME', label: '写入当前时间', description: '写入本次执行时间，适用于入库时间、更新时间等字段。' },
  ] },
  { label: '格式与内容转换', options: [
    { value: 'CAST', label: '转换数据类型', description: '将字段转换为文本、数字、日期或其他指定类型。' },
    { value: 'FUNCTION', label: '文本与数值处理', description: '选择去除空白、大小写转换等函数，并填写所需参数。' },
    { value: 'DATE_PARSE', label: '文本转日期 / 时间', description: '按来源格式解析日期文本，并指定输出日期或时间类型。' },
    { value: 'DATE_FORMAT', label: '日期 / 时间转文本', description: '按指定格式输出日期或时间文本。' },
    { value: 'ID_CARD_15_TO_18', label: '身份证 15 位转 18 位', description: '将旧版身份证号转换为 18 位，并选择已为 18 位时的处理方式。' },
    { value: 'ID_CARD_VALIDATE', label: '校验身份证号码', description: '检查身份证号码是否有效，输出布尔值。' },
    { value: 'CONCAT', label: '合并多个字段', description: '按指定顺序与分隔符合并多个字段。' },
  ] },
  { label: '字典与枚举翻译', options: [
    { value: 'LOOKUP', label: '单值字典翻译', description: '在授权字典表中匹配一个编码，输出对应的名称或值。' },
    { value: 'LOOKUP_MULTI', label: '多值字典翻译', description: '拆分多个编码，批量匹配字典，再按顺序拼接结果。' },
    { value: 'STATIC_MAP', label: '按枚举替换值', description: '配置编码与显示值的对应关系，无需查询字典表。' },
  ] },
  { label: '计算与校验', options: [
    { value: 'EXPRESSION', label: '按表达式计算', description: '引用源字段或已有目标字段，使用受限表达式计算结果。' },
    { value: 'SYSTEM', label: '生成系统值', description: '选择系统提供的唯一标识或执行时间。' },
    { value: 'CONDITIONAL', label: '按条件选择值', description: '设置判断条件，并分别配置成立与不成立时的取值规则。' },
    { value: 'VALIDATE', label: '校验输出值', description: '检查必填、范围、格式或允许值；失败时按所选策略处理。' },
  ] },
];
const choices = RULE_CHOICE_GROUPS.flatMap(group => group.options);
const FUNCTIONS: Record<string, string> = { trim: '去除首尾空白', upper: '转为大写', lower: '转为小写', blankToNull: '空白转 NULL', coalesce: '填补空值', substring: '截取文本', replace: '替换文本', concat: '拼接文本' };

function generatedValue(input: UnifiedMappingRule['input']): string | undefined {
  if (input.kind === 'SYSTEM') return ({ UUID32: 'UUID32', UUID: 'UUID', NOW: 'CURRENT_TIME' } as Record<string, string>)[input.generator ?? 'UUID'];
  if (input.kind !== 'EXPRESSION') return;
  const expression = input.expression?.trim().toUpperCase();
  // Recognize established generated expressions without rewriting their stored semantics.
  if (expression === 'LOCALTIMESTAMP') return 'CURRENT_TIME';
  if (expression === 'UUID()') return 'UUID32';
}
export function concreteRuleMode(rule: UnifiedMappingRule): string {
  return rule.when != null ? 'CONDITIONAL' : generatedValue(rule.input) ?? primaryRuleMode(rule);
}
export function ruleChoice(mode: string): RuleChoice {
  return choices.find(choice => choice.value === mode) ?? { value: mode, label: mode, description: '保留已有规则，可在附加转换中查看具体参数。' };
}
export function selectConcreteRule(rule: UnifiedMappingRule, mode: string, id: string): UnifiedMappingRule {
  if (mode === concreteRuleMode(rule)) return rule;
  if (['UUID32', 'UUID', 'CURRENT_TIME'].includes(mode)) {
    const next = changePrimaryRuleMode(rule, 'SYSTEM', id);
    return { ...next, input: { kind: 'SYSTEM', generator: mode === 'CURRENT_TIME' ? 'NOW' : mode } };
  }
  return changePrimaryRuleMode(rule, mode, id);
}
export function concreteStepLabel(step: RuleStep): string {
  const args = step.args ?? {};
  if (step.operator === 'FUNCTION') return FUNCTIONS[String(args.function)] ?? `函数：${String(args.function ?? '待选择')}`;
  if (step.operator === 'CAST') return `转为${TYPE_LABELS[String(args.logicalType ?? 'STRING')] ?? String(args.logicalType)}`;
  if (step.operator === 'SYSTEM_VALUE') return ruleChoice(({ UUID32: 'UUID32', UUID: 'UUID', NOW: 'CURRENT_TIME' } as Record<string, string>)[String(args.generator ?? 'UUID')] ?? 'SYSTEM').label;
  return ruleChoice(step.operator).label;
}
export function concreteRuleLabel(rule: UnifiedMappingRule): string {
  if (rule.when != null) return ruleChoice('CONDITIONAL').label;
  const steps = rule.steps.filter(step => !step.uiRole && step.operator !== 'DIRECT');
  const input = rule.input.kind === 'FIELD' || rule.input.kind === 'TARGET' ? [] : [ruleChoice(concreteRuleMode(rule)).label];
  return [...input, ...steps.map(concreteStepLabel)].join(' → ') || ruleChoice('DIRECT').label;
}
export function concreteInputLabel(rule: UnifiedMappingRule): string {
  if (rule.when != null) return '由条件分支取值';
  if (rule.input.kind === 'FIELD') return rule.input.name || '未选源字段';
  if (rule.input.kind === 'TARGET') return `已生成字段：${rule.input.name ?? '待选择'}`;
  if (rule.input.kind === 'LITERAL') return '固定值：' + (rule.input.value == null ? 'NULL' : typeof rule.input.value === 'object' ? JSON.stringify(rule.input.value) : String(rule.input.value));
  return ruleChoice(concreteRuleMode(rule)).label;
}
