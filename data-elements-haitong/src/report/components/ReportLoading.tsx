import { Button } from 'antd';
import Icon from './Icon';
import './ReportLoading.css';

const stages = ['核对上报数据源', '读取文件与字段规范', '恢复工作区和上报记录'];

/** The entry screen follows real requests; it never delays entry or invents a percentage. */
export default function ReportLoading({ phase = 0, error, onRetry, onReturn }: {
  phase?: number; error?: string; onRetry?: () => void; onReturn?: () => void;
}) {
  return <div className={`report-entry ${error ? 'is-error' : ''}`} aria-busy={!error}>
    <div className="report-entry-grid" aria-hidden="true"/>
    <section className="report-entry-card" aria-labelledby="report-entry-title">
      <div className="report-entry-visual" aria-hidden="true">
        <div className="report-entry-file"><Icon name="file" size={39}/><b>CSV · Excel</b></div>
        <div className="report-entry-stream"><i/><i/><i/><Icon name="arrow" size={25}/></div>
        <div className="report-entry-table"><Icon name="table" size={39}/><b>规范数据</b><span className="report-entry-check"><Icon name="check" size={14}/></span></div>
      </div>
      <div className="report-entry-copy" role={error ? 'alert' : 'status'} aria-live="polite">
        <span className="report-entry-eyebrow">数据有序汇聚 · 上报全程可查</span>
        <h1 id="report-entry-title">{error ? '工作台暂时未能打开' : '正在进入数据上报工作台'}</h1>
        <p>{error || '正在准备数据源、上传文件与上报记录，完成后自动进入工作台。'}</p>
      </div>
      {!error && <ol className="report-entry-stages">{stages.map((label, index) => <li key={label} className={index < phase ? 'is-done' : index === phase ? 'is-current' : ''} aria-current={index === phase ? 'step' : undefined}>
        <span>{index < phase ? <Icon name="check" size={13}/> : index + 1}</span><strong>{label}</strong>{index === phase && <i/>}
      </li>)}</ol>}
      {error && <div className="report-entry-actions">{onRetry && <Button type="primary" onClick={onRetry}>重新加载</Button>}{onReturn && <Button onClick={onReturn}>返回</Button>}</div>}
    </section>
  </div>;
}
