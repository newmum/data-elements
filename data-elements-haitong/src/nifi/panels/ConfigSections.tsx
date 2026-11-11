import { useState } from 'react';
import { Collapse, type CollapseProps } from 'antd';

export default function ConfigSections({ items = [], ...props }: Omit<CollapseProps, 'accordion' | 'activeKey' | 'defaultActiveKey' | 'onChange'>) {
  // Configuration can reveal new groups after a component or connection is chosen.
  // Track only explicit collapses so newly available groups are open too.
  const [collapsed, setCollapsed] = useState<string[]>([]);
  const keys = items.map(({ key }, index) => String(key ?? index));
  return <Collapse {...props} items={items}
    activeKey={keys.filter(key => !collapsed.includes(key))}
    onChange={(active) => setCollapsed(keys.filter(key => !active.includes(key)))} />;
}
