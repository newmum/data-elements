import { Button, Modal, theme } from 'antd';
import type { ColumnsProbeResult } from '@/api/pipelines';
import { getNifiOverlayContainer } from './overlayContainer';

export interface ProbeColumnsSummary {
  title: string;
  results: ColumnsProbeResult[];
  fallbackTables: string[];
}

/** A contextual modal: static Modal.info loses the canvas theme and motion setting. */
export default function ProbeColumnsModal({ summary, onClose }: {
  summary: ProbeColumnsSummary;
  onClose: () => void;
}) {
  const { token } = theme.useToken();
  const rows = summary.results.flatMap((result, index) => (result.columns ?? []).map((column) => ({
    table: result.table ?? summary.fallbackTables[index] ?? '', column,
  })));
  const cellStyle = { borderColor: token.colorBorderSecondary, color: token.colorText };
  const headerStyle = { ...cellStyle, background: token.colorBgContainer };

  return <Modal
    open title={summary.title} width={960} style={{ top: 32, maxWidth: 'calc(100vw - 32px)' }}
    className="probe-columns-modal" getContainer={getNifiOverlayContainer}
    onCancel={onClose} closable keyboard maskClosable
    // No animation lifecycle can strand this read-only result behind a transparent mask.
    // The parent unmounts the modal on close, so it cannot leave a blocking portal behind.
    transitionName="" maskTransitionName=""
    footer={<Button type="primary" onClick={onClose}>关闭</Button>}
  >
    <div className="probe-columns-summary" style={{ color: token.colorText }}>
      <p className="probe-columns-count" style={{ color: token.colorTextSecondary }}>
        共探查 {summary.results.length} 张表，{rows.length} 个字段
      </p>
      <div className="probe-columns-scroll" tabIndex={0} role="region" aria-label="字段探查结果">
        <table className="probe-columns-table">
          <thead><tr>{['表名', '字段名', '注释', '类型', '长度', '小数位', '主键', '可空'].map((name) =>
            <th key={name} scope="col" style={headerStyle}>{name}</th>)}</tr></thead>
          <tbody>{rows.map(({ table, column }, index) =>
            <tr key={`${table}-${column.columnName}-${index}`}>
              {[table, column.columnName, column.columnComment || '-', column.dataType || column.typeName || '-',
                column.columnSize ?? '-', column.decimalDigits ?? '-',
                column.primaryKey == null ? '-' : column.primaryKey ? '是' : '否',
                column.nullable == null ? '-' : column.nullable ? '是' : '否'].map((value, cell) =>
                <td key={cell} style={cellStyle}>{value}</td>)}
            </tr>)}</tbody>
        </table>
      </div>
    </div>
  </Modal>;
}
