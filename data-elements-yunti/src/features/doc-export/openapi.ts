import type { Flow, RequestParam, TriggerData } from '@/domain/flow-dsl';

function triggerOf(flow: Flow): TriggerData {
  return flow.nodes.find((node) => node.type === 'trigger')?.data as TriggerData;
}

function paramLocation(params: RequestParam[], input: 'query' | 'path' | 'header') {
  return params.map((param) => ({
    name: param.name,
    in: input,
    required: input === 'path' ? true : param.required,
    description: param.desc,
    schema: { type: param.type === 'long' ? 'integer' : param.type },
    example: param.example,
  }));
}

export function generateOpenApi(flow: Flow) {
  const trigger = triggerOf(flow);
  const parameters = [
    ...paramLocation(trigger.request.query, 'query'),
    ...paramLocation(trigger.request.path, 'path'),
    ...paramLocation(trigger.request.header, 'header'),
  ];

  return {
    openapi: '3.1.0',
    info: {
      title: flow.name,
      description: flow.description,
      version: String(flow.version),
    },
    tags: [{ name: trigger.group || flow.group }],
    paths: {
      [trigger.path]: {
        [trigger.method.toLowerCase()]: {
          tags: [trigger.group || flow.group],
          summary: flow.name,
          description: flow.description,
          parameters,
          requestBody: trigger.request.body
            ? {
                required: true,
                content: {
                  'application/json': { schema: trigger.request.body },
                },
              }
            : undefined,
          responses: {
            '200': {
              description: 'Success',
              content: {
                'application/json': {
                  schema: {
                    type: 'object',
                    properties: {
                      code: { type: 'integer', example: 0 },
                      message: { type: 'string', example: 'success' },
                      data: { type: 'object' },
                    },
                  },
                },
              },
            },
            '400': { description: 'Bad Request' },
            '500': { description: 'Internal Error' },
          },
        },
      },
    },
  };
}

export function generateOpenApiJson(flow: Flow): string {
  return JSON.stringify(generateOpenApi(flow), null, 2);
}
