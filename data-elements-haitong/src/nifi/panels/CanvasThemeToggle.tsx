import { MoonOutlined, SunOutlined } from '@ant-design/icons';
import { Button, Tooltip } from 'antd';
import { useOceanTheme } from '../../design/theme';

/** Shared switch for the canvas toolbar and full-screen node configuration. */
export default function CanvasThemeToggle() {
  const { dark, toggle } = useOceanTheme();
  const label = dark ? '切换为浅色模式' : '切换为深色模式';

  return (
    <Tooltip title={label}>
      <Button
        type="text"
        className="canvas-theme-toggle"
        aria-label={label}
        aria-pressed={dark}
        icon={dark ? <SunOutlined /> : <MoonOutlined />}
        onClick={toggle}
      />
    </Tooltip>
  );
}
