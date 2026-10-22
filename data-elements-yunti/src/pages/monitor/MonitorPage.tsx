import { Button, Card, Col, Empty, Row, Spin, Statistic, Switch, Table, Tag, Typography, message } from 'antd';
import { Chart } from '@antv/g2';
import { useEffect, useRef } from 'react';
import {
  useAlertEvents,
  useAlertRules,
  useAuditLogs,
  useCallLogs,
  useEvaluateAlertRules,
  useMonitorOverview,
  useSetAlertRuleEnabled,
} from '@/services/monitor';

function downloadCallLogs(rows: Array<object>) {
  const columns = ['createdAt', 'serviceName', 'method', 'path', 'status', 'success', 'durationMs', 'errorMessage'];
  const escape = (value: unknown) => `"${String(value ?? '').replaceAll('"', '""')}"`;
  const csv = [
    columns.join(','),
    ...rows.map((row) => {
      const values = row as Record<string, unknown>;
      return columns.map((key) => escape(values[key])).join(',');
    }),
  ].join('\r\n');
  const url = URL.createObjectURL(new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8' }));
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = 'flowserve-call-logs.csv';
  anchor.click();
  URL.revokeObjectURL(url);
}

function TrendChart() {
  const ref = useRef<HTMLDivElement | null>(null);
  const { data } = useMonitorOverview();

  useEffect(() => {
    if (!ref.current || !data) return;
    const chart = new Chart({
      container: ref.current,
      autoFit: true,
      height: 300,
    });
    chart
      .line()
      .data(data.series.flatMap((point) => [
        { ts: point.ts, type: 'QPS', value: point.qps },
        { ts: point.ts, type: 'P95', value: point.p95 },
      ]))
      .encode('x', 'ts')
      .encode('y', 'value')
      .encode('color', 'type')
      .axis('x', { title: false, labelAutoRotate: false })
      .axis('y', { title: false });
    chart.render();
    return () => chart.destroy();
  }, [data]);

  return <div ref={ref} style={{ height: 300 }} />;
}

export default function MonitorPage() {
  const { data, isLoading } = useMonitorOverview();
  const { data: auditLogs = [] } = useAuditLogs();
  const { data: callLogs = [] } = useCallLogs();
  const { data: alertRules = [] } = useAlertRules();
  const { data: alertEvents = [] } = useAlertEvents();
  const setRuleEnabled = useSetAlertRuleEnabled();
  const evaluateRules = useEvaluateAlertRules();

  const handleEvaluate = async () => {
    const events = await evaluateRules.mutateAsync();
    message.success(events.length ? `触发 ${events.length} 条告警` : '评估完成，暂无告警触发');
  };

  if (isLoading) {
    return (
      <div style={{ padding: 24 }}>
        <Spin />
      </div>
    );
  }
  if (!data) {
    return (
      <div style={{ padding: 24 }}>
        <Empty />
      </div>
    );
  }

  return (
    <div style={{ padding: 24 }}>
      <Row gutter={[16, 16]}>
        <Col span={6}>
          <Card><Statistic title="服务数" value={data.serviceCount} /></Card>
        </Col>
        <Col span={6}>
          <Card><Statistic title="今日调用" value={data.todayCalls} /></Card>
        </Col>
        <Col span={6}>
          <Card><Statistic title="活跃数据源" value={data.activeDataSources} /></Card>
        </Col>
        <Col span={6}>
          <Card><Statistic title="异常服务" value={data.errorTop.length} /></Card>
        </Col>
        <Col span={16}>
          <Card title="调用趋势">
            <TrendChart />
          </Card>
        </Col>
        <Col span={8}>
          <Card title="异常 Top">
            <Table
              size="small"
              rowKey="name"
              pagination={false}
              dataSource={data.errorTop}
              columns={[
                { title: '服务', dataIndex: 'name' },
                { title: '错误数', dataIndex: 'errors' },
              ]}
            />
          </Card>
        </Col>
        <Col span={24}>
          <Card
            title="最近调用"
            extra={<Button onClick={() => downloadCallLogs(callLogs)}>下载日志</Button>}
            style={{ marginBottom: 16 }}
          >
            <Table
              size="small"
              rowKey="id"
              dataSource={callLogs}
              pagination={{ pageSize: 8 }}
              columns={[
                { title: '时间', dataIndex: 'createdAt', width: 180 },
                { title: '服务', dataIndex: 'serviceName', width: 160, render: (v: string | null) => v || '-' },
                { title: '方法', dataIndex: 'method', width: 80 },
                {
                  title: '路径',
                  dataIndex: 'path',
                  render: (value: string) => (
                    <Typography.Text code ellipsis={{ tooltip: value }}>
                      {value}
                    </Typography.Text>
                  ),
                },
                {
                  title: '状态',
                  dataIndex: 'status',
                  width: 100,
                  render: (value: number, record: { success: boolean }) => (
                    <Tag color={record.success ? 'green' : 'red'}>{value}</Tag>
                  ),
                },
                { title: '耗时', dataIndex: 'durationMs', width: 100, render: (v: number) => `${v} ms` },
                {
                  title: '错误',
                  dataIndex: 'errorMessage',
                  render: (value: string | null) => value || '-',
                },
              ]}
            />
          </Card>
          <Card
            title="告警规则"
            extra={
              <Button loading={evaluateRules.isPending} onClick={handleEvaluate}>
                立即评估
              </Button>
            }
            style={{ marginBottom: 16 }}
          >
            <Table
              size="small"
              rowKey="id"
              pagination={false}
              dataSource={alertRules}
              columns={[
                { title: '规则', dataIndex: 'name' },
                { title: '指标', dataIndex: 'metric' },
                {
                  title: '阈值',
                  render: (_, record) => `${record.operator} ${record.thresholdValue}`,
                },
                { title: '持续时间', dataIndex: 'durationMinutes', render: (value: number) => `${value} 分钟` },
                { title: '通知', dataIndex: 'notifyTarget' },
                {
                  title: '状态',
                  dataIndex: 'enabled',
                  render: (value: boolean, record) => (
                    <Switch
                      size="small"
                      checked={value}
                      checkedChildren="启用"
                      unCheckedChildren="停用"
                      loading={setRuleEnabled.isPending}
                      onChange={(enabled) => setRuleEnabled.mutate({ id: record.id, enabled })}
                    />
                  ),
                },
              ]}
            />
          </Card>
          <Card title="最近告警事件" style={{ marginBottom: 16 }}>
            <Table
              size="small"
              rowKey="id"
              dataSource={alertEvents}
              pagination={{ pageSize: 6 }}
              columns={[
                { title: '时间', dataIndex: 'triggeredAt', width: 180 },
                { title: '规则', dataIndex: 'ruleName', width: 160 },
                { title: '指标', dataIndex: 'metric', width: 110 },
                { title: '当前值', dataIndex: 'actualValue', width: 100, render: (value: number) => value.toFixed(2) },
                { title: '阈值', dataIndex: 'thresholdValue', width: 100, render: (value: number) => value.toFixed(2) },
                {
                  title: '消息',
                  dataIndex: 'message',
                  render: (value: string) => (
                    <Typography.Text ellipsis={{ tooltip: value }}>
                      {value}
                    </Typography.Text>
                  ),
                },
                { title: '通知', dataIndex: 'notifyTarget', width: 130, render: (value: string | null) => value || '-' },
                { title: '状态', dataIndex: 'status', width: 90, render: (value: string) => <Tag color="orange">{value}</Tag> },
              ]}
            />
          </Card>
          <Card title="审计日志">
            <Table
              size="small"
              rowKey="id"
              dataSource={auditLogs}
              pagination={{ pageSize: 8 }}
              columns={[
                { title: '时间', dataIndex: 'createdAt', width: 180 },
                {
                  title: '动作',
                  dataIndex: 'action',
                  width: 180,
                  render: (value: string) => <Tag color="blue">{value}</Tag>,
                },
                { title: '对象', dataIndex: 'target', width: 180 },
                {
                  title: '详情',
                  dataIndex: 'detail',
                  render: (value: string) => (
                    <Typography.Text code ellipsis={{ tooltip: value }}>
                      {value || '-'}
                    </Typography.Text>
                  ),
                },
              ]}
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
}
