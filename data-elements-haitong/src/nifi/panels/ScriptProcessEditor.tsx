import { Alert, Button, Collapse, Form, Input } from 'antd';
import { ArrowRightOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import './scriptProcessEditor.css';

export const isScriptProcess = (key?: string) => ['transform.python', 'transform.java', 'transform.shell'].includes(key ?? '');

export function scriptProcessError(key: string, config: Record<string, unknown>): string | undefined {
  const suffix = key === 'transform.java' ? '.jar' : key === 'transform.python' ? '.py' : '.sh';
  const path = String(config.programPath ?? '').trim();
  if (!path) return '请填写 NiFi 服务器上的脚本或 JAR 路径';
  if (path.startsWith('-') || !path.toLowerCase().endsWith(suffix)) return `文件路径需以 ${suffix} 结尾，且不能以 - 开头`;
  if (config.executable !== undefined && !String(config.executable).trim()) return '请填写运行程序';
  if (config.arguments !== undefined && (!Array.isArray(config.arguments) || config.arguments.some(item => typeof item !== 'string' || !item))) return '请填写或移除空白参数行';
  if (Array.isArray(config.arguments) && config.arguments.length > 64) return '程序参数最多 64 项';
  const values = [config.executable, path, config.workingDirectory, ...(Array.isArray(config.arguments) ? config.arguments : [])];
  if (values.some(value => typeof value === 'string' && (value.includes('${') || value.includes('\0') || value.length > 8192))) return '请使用不含 NiFi 表达式的普通文本，单项不超过 8192 字符';
}

interface Props {
  manifestKey: string;
  config: Record<string, unknown>;
  onChange: (patch: Record<string, unknown>) => void;
}

export default function ScriptProcessEditor({ manifestKey, config, onChange }: Props) {
  const java = manifestKey === 'transform.java';
  const python = manifestKey === 'transform.python';
  const language = java ? 'JAVA' : python ? 'Python' : 'Shell';
  const executable = String(config.executable ?? (java ? 'java' : python ? 'python3' : '/bin/sh'));
  const extension = java ? '.jar' : python ? '.py' : '.sh';
  const args: unknown[] = Array.isArray(config.arguments) ? config.arguments : [];
  const path = String(config.programPath ?? '');
  const example = python
    ? 'import json, sys\nrecords = json.load(sys.stdin)\n# 在此处理 records，标准输出仅写处理结果\njson.dump(records, sys.stdout, ensure_ascii=False)'
    : java
      ? 'public class ProcessData {\n    public static void main(String[] args) throws Exception {\n        // 将处理结果写到标准输出；日志写到 System.err\n        byte[] buffer = new byte[4096];\n        int size;\n        while ((size = System.in.read(buffer)) != -1) {\n            System.out.write(buffer, 0, size);\n        }\n    }\n}\n// 打包为包含 Main-Class 的可执行 JAR'
      : '#!/bin/sh\nset -eu\n# 从标准输入读取数据，将处理结果写到标准输出\ncat';
  return <div className="script-process-editor">
    <div className="script-process-editor__intro"><strong>{language} 程序</strong><span>{java ? '运行已部署的可执行 JAR' : '运行已部署的脚本文件'}</span></div>
    <Form layout="horizontal" labelCol={{ flex: '88px' }} wrapperCol={{ flex: 1 }} colon={false} size="small">
      <Form.Item label="运行程序" required extra="填写 NiFi 服务器上的可执行文件路径，或已加入 PATH 的程序名。">
        <Input aria-label="运行程序" value={executable} onChange={e => onChange({ executable: e.target.value })} placeholder={java ? 'java' : python ? 'python3' : '/bin/sh'} />
      </Form.Item>
      <Form.Item label={java ? 'JAR 路径' : '脚本路径'} required validateStatus={path && (!path.toLowerCase().endsWith(extension) || path.startsWith('-')) ? 'error' : undefined} extra={`文件需预先部署到各 NiFi 执行节点，以 ${extension} 结尾。`}>
        <Input aria-label={java ? 'JAR 路径' : '脚本路径'} value={path} onChange={e => onChange({ programPath: e.target.value })} placeholder={`/opt/nifi/scripts/process${extension}`} />
      </Form.Item>
      <Form.Item label="工作目录" extra="可选，留空时使用 NiFi 默认工作目录。">
        <Input aria-label="工作目录" value={String(config.workingDirectory ?? '')} onChange={e => onChange({ workingDirectory: e.target.value })} placeholder="/opt/nifi/scripts" />
      </Form.Item>
    </Form>
    <div className="script-process-editor__section"><strong>程序参数</strong><span>按顺序传入，每行是一个完整参数</span></div>
    <div className="script-process-editor__arguments">
      {args.map((arg: unknown, index: number) => <div className="script-process-editor__argument" key={index}>
        <span className="script-process-editor__index">{index + 1}</span>
        <Input size="small" aria-label={`参数 ${index + 1}`} value={typeof arg === 'string' ? arg : ''} status={typeof arg !== 'string' || !arg ? 'error' : undefined} placeholder="例如 --mode 或 clean；包含空格时无需加引号" onChange={e => onChange({ arguments: args.map((item: unknown, i: number) => i === index ? e.target.value : item) })} />
        <Button size="small" type="text" icon={<DeleteOutlined />} aria-label={`删除参数 ${index + 1}`} onClick={() => onChange({ arguments: args.filter((_: unknown, i: number) => i !== index) })} />
      </div>)}
      {!args.length && <p className="script-process-editor__empty">暂无附加参数，脚本或 JAR 路径会自动作为启动参数。</p>}
      <Button size="small" type="dashed" icon={<PlusOutlined />} disabled={args.length >= 64} onClick={() => onChange({ arguments: [...args, ''] })}>添加参数</Button>
      {config.arguments !== undefined && !Array.isArray(config.arguments) && <Alert type="error" message="现有参数格式无效，请重新逐项填写参数。" />}
    </div>
    <div className="script-process-editor__section"><strong>数据如何流转</strong><span>每个数据批次执行一次程序</span></div>
    <div className="script-process-editor__flow"><span>上游数据<br /><small>标准输入 stdin</small></span><ArrowRightOutlined /><span>{language} 加工<br /><small>退出码为 0</small></span><ArrowRightOutlined /><span>下游组件<br /><small>标准输出 stdout</small></span></div>
    <p className="script-process-editor__note">输出格式须与下游组件兼容；接入字段治理或数据库目标时请输出 JSON。日志写入 stderr，执行失败会保留输出和错误信息，不传给下游。</p>
    <Collapse size="small" items={[{ key: 'example', label: `${language} 输入输出示例与运行要求`, children: <><pre className="script-process-editor__example">{example}</pre><p className="script-process-editor__note">{python ? '需安装 Python 3 及脚本依赖。' : java ? '需安装与 JAR 兼容的 Java 运行环境。' : '需安装 sh / bash 等兼容解释器。'} NiFi 执行账号需具备代码执行权限和文件访问权限。程序应自行控制执行时长；本组件不额外设置进程超时。</p></> }]} />
  </div>;
}
