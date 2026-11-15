import { Input } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import './sidebarLayout.css';

export default function ConnectionSearchHeader({ value, onChange, label="搜索数据库对象" }: { value: string; onChange: (value: string) => void; label?: string }) {
  return <div className="sidebar-heading connection-search-header">
    <Input
      prefix={<SearchOutlined />}
      aria-label={label}
      placeholder={label}
      value={value}
      allowClear
      onChange={event => onChange(event.target.value)}
    />
  </div>;
}
