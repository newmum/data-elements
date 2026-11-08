import { Button, Pagination, Select } from 'antd';
import { CheckOutlined, UndoOutlined } from '@ant-design/icons';
import './tableChangesFooter.css';

export interface TableFooterPagination {
  current: number;
  pageSize: number;
  total: number;
  onChange: (page: number) => void;
  onPageSizeChange: (pageSize: number) => void;
}

export default function TableChangesFooter({ dirty, onApply, onRevert, pagination, readOnly=false }: {
  readOnly?: boolean;
  dirty: boolean;
  onApply: () => void;
  onRevert: () => void;
  pagination?: TableFooterPagination;
}) {
  return <div className="table-changes-footer" aria-label="表格底部操作栏">
    <div className="table-change-actions" style={readOnly?{pointerEvents:"none"}:undefined}>
      <Button size="small" type="primary" icon={<CheckOutlined />} title="应用全部未应用的记录与结构修改" disabled={!dirty} onClick={onApply}>应用更改</Button>
      <Button size="small" icon={<UndoOutlined />} title="撤销全部未应用的记录与结构修改" disabled={!dirty} onClick={onRevert}>撤销</Button>
      <span className="table-change-status" role="status" aria-live="polite">{readOnly?'只读预览':dirty ? '有未应用的更改' : '所有更改已应用'}</span>
    </div>
    {pagination && <div className="table-footer-pagination">
      <span className="table-record-count">共 {pagination.total.toLocaleString('en-US')} 条</span>
      <Pagination current={pagination.current} pageSize={pagination.pageSize} total={pagination.total} onChange={pagination.onChange} size="small" showSizeChanger={false} showLessItems />
      <label className="table-page-size"><span>每页</span><Select aria-label="每页记录数" size="small" value={pagination.pageSize} onChange={pagination.onPageSizeChange} options={[10, 20, 50, 100].map(value => ({ value, label: value }))} /><span>行</span></label>
    </div>}
  </div>;
}
