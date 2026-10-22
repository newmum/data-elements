import type { Flow } from '@/domain/flow-dsl';
import { generateMarkdown } from './markdown';

function escapeHtml(input: string) {
  return input
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

export function generateHtml(flow: Flow): string {
  const markdown = escapeHtml(generateMarkdown(flow));
  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8" />
  <title>${escapeHtml(flow.name)} API 文档</title>
  <style>
    body{font-family:Inter,system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;line-height:1.65;margin:40px;color:#111827;background:#f8fafc}
    main{max-width:920px;margin:auto;background:#fff;border:1px solid #e5e7eb;border-radius:8px;padding:28px;box-shadow:0 12px 30px rgba(15,23,42,.08)}
    pre{background:#0f172a;color:#e5e7eb;padding:14px;border-radius:8px;overflow:auto}
  </style>
</head>
<body><main><pre>${markdown}</pre></main></body>
</html>`;
}
