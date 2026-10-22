import type { NodeGenerator } from './types';
import { nodeLabel } from './types';
import { triggerGenerator } from './trigger';
import { responseGenerator } from './response';
import { sqlQueryGenerator, sqlExecGenerator } from './sql';
import {
  kafkaConsumeGenerator,
  kafkaProduceGenerator,
  esQueryGenerator,
  httpCallGenerator,
  redisGenerator,
} from './datasource';
import {
  scriptGenerator,
  transformGenerator,
  filterGenerator,
  aggregateGenerator,
  sortGenerator,
  paginateGenerator,
  joinGenerator,
} from './processing';
import {
  conditionGenerator,
  switchGenerator,
  foreachGenerator,
  parallelGenerator,
  tryCatchGenerator,
  subflowGenerator,
  delayGenerator,
  setVarGenerator,
  constGenerator,
  logGenerator,
  validateGenerator,
} from './logic';

/** Fallback for any node type without a dedicated generator yet. */
const fallbackGenerator: NodeGenerator = (node, ctx) => {
  ctx.buf.comment(`TODO: 未实现的节点生成器 ${nodeLabel(node)}`);
};

const registry: Record<string, NodeGenerator> = {
  trigger: triggerGenerator,
  response: responseGenerator,
  'sql-query': sqlQueryGenerator,
  'sql-exec': sqlExecGenerator,
  'kafka-consume': kafkaConsumeGenerator,
  'kafka-produce': kafkaProduceGenerator,
  'es-query': esQueryGenerator,
  'http-call': httpCallGenerator,
  redis: redisGenerator,
  script: scriptGenerator,
  transform: transformGenerator,
  filter: filterGenerator,
  aggregate: aggregateGenerator,
  sort: sortGenerator,
  paginate: paginateGenerator,
  join: joinGenerator,
  condition: conditionGenerator,
  switch: switchGenerator,
  foreach: foreachGenerator,
  parallel: parallelGenerator,
  'try-catch': tryCatchGenerator,
  subflow: subflowGenerator,
  delay: delayGenerator,
  'set-var': setVarGenerator,
  const: constGenerator,
  log: logGenerator,
  validate: validateGenerator,
};

/** Register a generator (used as more node types are implemented). */
export function registerGenerator(type: string, gen: NodeGenerator): void {
  registry[type] = gen;
}

export function getGenerator(type: string): NodeGenerator {
  return registry[type] ?? fallbackGenerator;
}

export const triggerCodeGenerator = triggerGenerator;
