import { z } from 'zod';
import { genId } from '@/utils/id';

/** HTTP methods supported by a flow trigger. */
export const HttpMethodSchema = z.enum(['GET', 'POST', 'PUT', 'DELETE', 'PATCH']);
export type HttpMethod = z.infer<typeof HttpMethodSchema>;

/** A single request parameter definition (query/path/header). */
export const RequestParamSchema = z.object({
  name: z.string(),
  type: z.string().default('string'),
  required: z.boolean().default(false),
  desc: z.string().default(''),
  default: z.unknown().optional(),
  example: z.unknown().optional(),
});
export type RequestParam = z.infer<typeof RequestParamSchema>;

export const TriggerRequestSchema = z.object({
  query: z.array(RequestParamSchema).default([]),
  path: z.array(RequestParamSchema).default([]),
  header: z.array(RequestParamSchema).default([]),
  /** JSON Schema (loose) for the request body, or null. */
  body: z.unknown().nullable().default(null),
});
export type TriggerRequest = z.infer<typeof TriggerRequestSchema>;

export const TriggerAuthSchema = z.object({
  required: z.boolean().default(false),
  roles: z.array(z.string()).default([]),
});
export type TriggerAuth = z.infer<typeof TriggerAuthSchema>;

/** Trigger config lives in the `data` of the singleton `trigger` node. */
export const TriggerDataSchema = z.object({
  method: HttpMethodSchema.default('GET'),
  path: z.string().default('/'),
  group: z.string().default('默认分组'),
  auth: TriggerAuthSchema.default({ required: false, roles: [] }),
  request: TriggerRequestSchema.default({ query: [], path: [], header: [], body: null }),
});
export type TriggerData = z.infer<typeof TriggerDataSchema>;

/** Per-node error handling strategy (SRS §6.6). */
export const ErrorHandlingSchema = z.object({
  onError: z.enum(['fail', 'ignore', 'branch', 'retry']).default('fail'),
  retry: z
    .object({ times: z.number().int().nonnegative(), intervalMs: z.number().int().nonnegative() })
    .optional(),
  errorBranchPort: z.string().optional(),
  fallbackValue: z.unknown().optional(),
});
export type ErrorHandling = z.infer<typeof ErrorHandlingSchema>;

export const PositionSchema = z.object({ x: z.number(), y: z.number() });
export type Position = z.infer<typeof PositionSchema>;

export const FlowNodeSchema = z.object({
  id: z.string(),
  type: z.string(),
  name: z.string().optional(),
  position: PositionSchema,
  /** Node-type-specific config. Validated per type by the registry/validators. */
  data: z.record(z.unknown()).default({}),
  errorHandling: ErrorHandlingSchema.optional(),
});
export type FlowNode = z.infer<typeof FlowNodeSchema>;

export const FlowEdgeSchema = z.object({
  id: z.string(),
  source: z.string(),
  target: z.string(),
  sourcePort: z.string().default('out'),
  targetPort: z.string().default('in'),
  label: z.string().optional(),
});
export type FlowEdge = z.infer<typeof FlowEdgeSchema>;

export const FlowSettingsSchema = z.object({
  timeoutMs: z.number().int().positive().default(10000),
  transactional: z.boolean().default(false),
  errorStrategy: z.enum(['fail-fast', 'continue', 'custom']).default('fail-fast'),
});
export type FlowSettings = z.infer<typeof FlowSettingsSchema>;

export const FlowSchema = z.object({
  flowId: z.string(),
  name: z.string(),
  description: z.string().default(''),
  version: z.number().int().nonnegative().default(1),
  group: z.string().default('默认分组'),
  nodes: z.array(FlowNodeSchema),
  edges: z.array(FlowEdgeSchema),
  settings: FlowSettingsSchema.default({
    timeoutMs: 10000,
    transactional: false,
    errorStrategy: 'fail-fast',
  }),
});
export type Flow = z.infer<typeof FlowSchema>;

/** Parse + validate an unknown value into a Flow (throws ZodError on failure). */
export function parseFlow(value: unknown): Flow {
  return FlowSchema.parse(value);
}

/** Serialize a Flow to a JSON string. */
export function serializeFlow(flow: Flow): string {
  return JSON.stringify(flow);
}

/** Create a minimal valid flow: a trigger wired to a response. */
export function createEmptyFlow(name: string): Flow {
  const triggerId = genId('node');
  const responseId = genId('node');
  const trigger: FlowNode = {
    id: triggerId,
    type: 'trigger',
    name: 'API 触发器',
    position: { x: 80, y: 200 },
    data: TriggerDataSchema.parse({}),
  };
  const response: FlowNode = {
    id: responseId,
    type: 'response',
    name: '响应',
    position: { x: 520, y: 200 },
    data: { mode: 'wrap', wrap: true, source: '', statusCode: 200 },
  };
  return parseFlow({
    flowId: genId('fl'),
    name,
    description: '',
    version: 1,
    group: '默认分组',
    nodes: [trigger, response],
    edges: [
      {
        id: genId('e'),
        source: triggerId,
        target: responseId,
        sourcePort: 'out',
        targetPort: 'in',
      },
    ],
    settings: { timeoutMs: 10000, transactional: false, errorStrategy: 'fail-fast' },
  });
}
