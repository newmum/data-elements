import { Button, Card, Empty, Input, Select, Space, Spin, Tabs } from 'antd';
import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { fetchDocText, type DocFormat } from '@/services/docs';
import { useFlow, useFlows } from '@/services/flows';
import { downloadText } from '@/utils/download';

export default function DocsPage() {
  const { flowId: routeFlowId } = useParams();
  const { data: flows = [] } = useFlows();
  const selectedId = routeFlowId ?? flows[0]?.flowId;
  const { data, isLoading } = useFlow(selectedId);
  const flow = data?.flow;
  const [docs, setDocs] = useState<Record<'openapi' | 'markdown' | 'html' | 'postman', string> | null>(null);

  useEffect(() => {
    let cancelled = false;
    if (!flow) {
      setDocs(null);
      return;
    }
    setDocs(null);
    Promise.all([
      fetchDocText(flow, 'openapi'),
      fetchDocText(flow, 'md'),
      fetchDocText(flow, 'html'),
      fetchDocText(flow, 'postman'),
    ]).then(([openapi, markdown, html, postman]) => {
      if (!cancelled) setDocs({ openapi, markdown, html, postman });
    }).catch(() => {
      if (!cancelled) setDocs({ openapi: '', markdown: '', html: '', postman: '' });
    });
    return () => {
      cancelled = true;
    };
  }, [flow]);

  const downloadDoc = async (format: DocFormat, filename: string, mime: string) => {
    if (!flow) return;
    const text = await fetchDocText(flow, format);
    downloadText(filename, text, mime);
  };

  return (
    <div style={{ padding: 24 }}>
      <Card
        title="API 文档导出"
        extra={
          <Select
            style={{ width: 260 }}
            value={selectedId}
            options={flows.map((item) => ({ value: item.flowId, label: item.name }))}
            onChange={(id) => window.history.pushState(null, '', `/docs/${id}`)}
          />
        }
      >
        {isLoading ? (
          <Spin />
        ) : !flow ? (
          <Empty description="请选择一个编排" />
        ) : (
          <>
            <Space style={{ marginBottom: 12 }}>
              <Button onClick={() => void downloadDoc('openapi', `${flow.name}.openapi.json`, 'application/json')}>
                下载 OpenAPI
              </Button>
              <Button onClick={() => void downloadDoc('md', `${flow.name}.md`, 'text/markdown')}>
                下载 Markdown
              </Button>
              <Button onClick={() => void downloadDoc('html', `${flow.name}.html`, 'text/html')}>
                下载 HTML
              </Button>
              <Button onClick={() => void downloadDoc('postman', `${flow.name}.postman.json`, 'application/json')}>
                下载 Postman
              </Button>
            </Space>
            <Tabs
              items={[
                {
                  key: 'openapi',
                  label: 'OpenAPI 3.1',
                  children: docs ? <Input.TextArea rows={24} value={docs.openapi} readOnly spellCheck={false} /> : <Spin />,
                },
                {
                  key: 'markdown',
                  label: 'Markdown',
                  children: docs ? <Input.TextArea rows={24} value={docs.markdown} readOnly spellCheck={false} /> : <Spin />,
                },
                {
                  key: 'html',
                  label: 'HTML',
                  children: docs ? <Input.TextArea rows={24} value={docs.html} readOnly spellCheck={false} /> : <Spin />,
                },
                {
                  key: 'postman',
                  label: 'Postman',
                  children: docs ? <Input.TextArea rows={24} value={docs.postman} readOnly spellCheck={false} /> : <Spin />,
                },
              ]}
            />
          </>
        )}
      </Card>
    </div>
  );
}
