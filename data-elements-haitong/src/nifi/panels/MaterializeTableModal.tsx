import { useEffect, useState } from 'react';
import { CopyOutlined } from '@ant-design/icons';
import { Alert, App, Button, Form, Input, Modal, Select, Space, Tooltip, Typography } from 'antd';
import CodeMirror from '@uiw/react-codemirror';
import { sql } from '@codemirror/lang-sql';
import { EditorView } from '@codemirror/view';
import {
  fetchMaterializationTemplate,
  generateTargetTableDdl,
  materializeTargetTable,
  type MaterializationTemplate,
  type TargetTableDdlColumn,
} from '@/api/dataassets';
import { probeColumns } from '@/api/pipelines';
import { getApiErrorMessage } from '@/api/response';
import type { MaterializeSource } from './materializeSources';
import { getNifiOverlayContainer } from './overlayContainer';

export default function MaterializeTableModal({ open, dbId, databaseLabel, databaseType, sources, initialTableName, onClose, onCreated }: {
  open: boolean;
  dbId: string;
  databaseLabel: string;
  databaseType: string;
  sources: MaterializeSource[];
  initialTableName: string;
  onClose: () => void;
  onCreated: (tableName: string) => Promise<void>;
}) {
  const { message } = App.useApp();
  const [ddl, setDdl] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [exists, setExists] = useState(false);
  const [sourceId, setSourceId] = useState('');
  const source = sources.find((item) => item.value === sourceId) || sources[0];
  const [tableName, setTableName] = useState(initialTableName);
  const [registeredTemplate, setRegisteredTemplate] = useState<MaterializationTemplate | null>(null);
  const [templateSourceId, setTemplateSourceId] = useState('');
  const [templateLoading, setTemplateLoading] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [generationNote, setGenerationNote] = useState('');
  const defaultTargetTableName = (sourceTableName?: string) => {
    const sourceName = String(sourceTableName ?? '').trim().split('.').at(-1)?.replace(/^[`"\[]|[`"\]]$/g, '') ?? '';
    if (!sourceName) return initialTableName;
    return /^ODS_/i.test(sourceName) ? sourceName : `ODS_${sourceName}`;
  };
  useEffect(() => {
    if (!open) return;
    let current = true;
    const sourceTableId = source?.sourceTableId ?? '';
    setDdl('');
    setError('');
    setExists(false);
    setGenerationNote('');
    setRegisteredTemplate(null);
    setTemplateSourceId('');
    // A usable default appears immediately; the registered template below may
    // refine it with the task-management naming rule.
    setTableName(defaultTargetTableName(source?.table));
    if (!sourceTableId) {
      setTemplateLoading(false);
      return () => { current = false; };
    }
    setTemplateLoading(true);
    void fetchMaterializationTemplate(sourceTableId)
      .then((template) => {
        if (!current) return;
        setRegisteredTemplate(template);
        setTemplateSourceId(sourceTableId);
        const registeredName = String(template.propList?.tableName ?? '').trim();
        if (registeredName) setTableName(registeredName);
      })
      .catch(() => {
        // A manually configured canvas can lack a registered source snapshot.
        // It remains usable through the physical-column fallback on generation.
      })
      .finally(() => { if (current) setTemplateLoading(false); });
    return () => { current = false; };
  }, [open, source?.value, source?.sourceTableId, source?.table, initialTableName]);
  const busy = submitting || generating;
  const ddlEditorHeight = Math.max(220, ddl.split('\n').length * 23 + 30);
  const copySourceTableName = async () => {
    const sourceTableName = source?.table.trim();
    if (!sourceTableName) return;
    try {
      await navigator.clipboard.writeText(sourceTableName);
      message.success(`已复制来源表名：${sourceTableName}`);
    } catch {
      const copyInput = document.createElement('textarea');
      copyInput.value = sourceTableName;
      copyInput.setAttribute('readonly', '');
      copyInput.style.position = 'fixed';
      copyInput.style.opacity = '0';
      document.body.appendChild(copyInput);
      copyInput.select();
      const copied = document.execCommand('copy');
      document.body.removeChild(copyInput);
      if (copied) message.success(`已复制来源表名：${sourceTableName}`);
      else message.warning('当前浏览器未授予剪贴板权限，请直接拖选来源表名后复制。');
    }
  };
  const generate = async () => {
    if (busy || !source) return;
    if (!tableName.trim()) { setError('请填写生成 DDL 使用的目标表名'); return; }
    setGenerating(true);
    setError(''); setExists(false); setGenerationNote('');
    try {
      let columns: TargetTableDdlColumn[];
      let governedTemplate = false;
      if (source.sourceTableId) {
        // Do not recreate governance rules in the canvas. Re-read the exact
        // template used by task management so `_cn`, mandatory-standard and
        // ODS system columns stay identical in both entry points.
        const template = templateSourceId === source.sourceTableId && registeredTemplate
          ? registeredTemplate
          : await fetchMaterializationTemplate(source.sourceTableId);
        setRegisteredTemplate(template);
        setTemplateSourceId(source.sourceTableId);
        columns = template.tableItems;
        governedTemplate = true;
      } else {
        const result = await probeColumns(source.manifestKey, source.config, source.table);
        if (!result.success || !result.columns.length) throw new Error(result.error || '来源表未探查到字段');
        columns = result.columns;
      }
      const sourceType = String(source.config.dbType || source.manifestKey.replace(/^source\./, ''));
      const sourceMode = String(source.config.compatibleMode || '');
      setDdl(await generateTargetTableDdl(dbId, tableName.trim(), columns, `${sourceType} ${sourceMode}`));
      const dictionaryFields = columns.filter((column) => column.dictionaryTranslationField || /_cn$/i.test(column.columnName)).length;
      setGenerationNote(governedTemplate
        ? `已按登记建表模板生成 ${columns.length} 个字段${dictionaryFields ? `，其中包含 ${dictionaryFields} 个字典或强制对标翻译字段` : ''}，并已按目标库类型生成 DDL。请检查后执行建表。`
        : `已探查 ${source.table} 的 ${columns.length} 个字段，并按目标库类型生成 DDL。请检查后执行建表。`);
    } catch (failure) {
      setError(getApiErrorMessage(failure, '探查来源并生成 DDL 失败'));
    } finally { setGenerating(false); }
  };
  const submit = async () => {
    if (busy) return;
    if (!ddl.trim()) { setError('请输入 CREATE TABLE 建表语句'); return; }
    if (!dbId) { setError('请先选择已登记的目标库'); return; }
    setSubmitting(true);
    setError('');
    setExists(false);
    try {
      const result = await materializeTargetTable(dbId, ddl.trim());
      if (result.status === 'exists') {
        setExists(true);
        setError(`目标表 ${result.tableName} 已存在，请直接从目标表下拉框选择，或修改 DDL 中的表名。`);
        return;
      }
      message.success(`物化建表成功：${result.tableName}`);
      onClose();
      setDdl('');
      await onCreated(result.tableName);
    } catch (failure) {
      setError(getApiErrorMessage(failure, '物化建表失败'));
    } finally {
      setSubmitting(false);
    }
  };
  return <Modal title="物化建表" open={open} width={720} style={{ top: 20 }} getContainer={getNifiOverlayContainer}
    styles={{ body: { maxHeight: 'calc(100vh - 160px)', overflowY: 'auto', paddingRight: 4 } }} okText="执行建表" cancelText="取消"
    confirmLoading={submitting} closable={!busy} maskClosable={!busy}
    okButtonProps={{ disabled: generating }} cancelButtonProps={{ disabled: busy }} onOk={submit}
    onCancel={() => { if (!busy) { setError(''); setExists(false); onClose(); } }}>
    <Form className="materialize-table-form" layout="horizontal" labelAlign="left" labelCol={{ flex: '76px' }} wrapperCol={{ flex: 1 }}
      style={{ marginTop: 20 }}>
      <Form.Item label="来源表名：">
        <Space.Compact style={{ width: '100%' }}>
          <Select aria-label="建表来源表" className="materialize-source-table-select" style={{ width: '100%' }}
            options={sources.map(({ value, label }) => ({ value, label }))} value={source?.value} onChange={setSourceId}
            disabled={busy || templateLoading || !sources.length} placeholder="选择上游来源表"
            labelRender={({ label }) => <span className="materialize-source-table-select__value"
              title="可直接拖选来源表名并复制"
              onMouseDown={(event) => event.stopPropagation()}
              onClick={(event) => event.stopPropagation()}>{label}</span>} />
          <Tooltip title="复制来源表名">
            <Button aria-label="复制来源表名" icon={<CopyOutlined />} disabled={!source} onClick={() => void copySourceTableName()} />
          </Tooltip>
          <Button loading={generating || templateLoading} disabled={submitting || templateLoading || !source || !dbId} onClick={generate}>探查并生成</Button>
        </Space.Compact>
      </Form.Item>
      <Form.Item label="目标表名：">
        <Space.Compact style={{ width: '100%' }}>
          <Input aria-label="生成 DDL 的目标表名" value={tableName} onChange={(event) => setTableName(event.target.value)}
            disabled={busy} placeholder="请输入目标表名" />
          <Typography.Text className="materialize-table-target-database">目标库：{databaseLabel}</Typography.Text>
        </Space.Compact>
      </Form.Item>
      {generationNote && <Alert type="success" showIcon message={generationNote} style={{ marginBottom: 12 }} />}
      {databaseType.toLowerCase().includes('hive') && <Alert type="info" showIcon style={{ marginBottom: 12 }}
        message="Hive 会转换来源字段类型并保留注释，不复制主键、自增、非空和默认值约束。无法无损表示的类型将转为 STRING，请确认字段类型和精度。" />}
      <Form.Item label="建表语句" required className="materialize-table-form__ddl">
        <CodeMirror className="materialize-ddl-editor" value={ddl} height={`${ddlEditorHeight}px`} extensions={[sql(), EditorView.lineWrapping]}
          onChange={setDdl} editable={!busy} basicSetup={{ lineNumbers: true, highlightActiveLine: true, foldGutter: true }}
          placeholder={'CREATE TABLE table_name (\n  id BIGINT,\n  name VARCHAR(100)\n);'} />
      </Form.Item>
      {error && <Alert type={exists ? 'warning' : 'error'} showIcon message={error} />}
    </Form>
  </Modal>;
}
