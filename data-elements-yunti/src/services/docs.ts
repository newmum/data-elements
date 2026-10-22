import type { Flow } from '@/domain/flow-dsl';
import { generateHtml } from '@/features/doc-export/html';
import { generateMarkdown } from '@/features/doc-export/markdown';
import { generateOpenApiJson } from '@/features/doc-export/openapi';

export type DocFormat = 'openapi' | 'md' | 'html' | 'postman';

/**
 * 文档由设计器当前 DSL 本地生成，避免为了导出静态内容依赖后端业务接口。
 */
export async function fetchDocText(flow: Flow, format: DocFormat): Promise<string> {
  if (format === 'md') return generateMarkdown(flow);
  if (format === 'html') return generateHtml(flow);
  if (format === 'postman') {
    const trigger = flow.nodes.find((node) => node.type === 'trigger');
    const data = trigger?.data as { method?: string; path?: string } | undefined;
    return JSON.stringify(
      {
        info: {
          name: flow.name,
          schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',
        },
        item: [{
          name: flow.name,
          request: {
            method: data?.method ?? 'GET',
            url: {
              raw: data?.path ?? '/',
              path: (data?.path ?? '/').split('/').filter(Boolean),
            },
          },
        }],
      },
      null,
      2,
    );
  }
  return generateOpenApiJson(flow);
}
