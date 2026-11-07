import { Input } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import './sidebarLayout.css';

export default function ConnectionSearchHeader({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  return <div className="sidebar-heading connection-search-header">
    <Input
      prefix={<SearchOutlined />}
      aria-label="搜索数据库对象"
      placeholder="搜索数据库对象"
      value={value}
      allowClear
      onChange={event => onChange(event.target.value)}
    />
  </div>;
}
