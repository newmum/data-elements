import { QuestionCircleOutlined } from '@ant-design/icons';
import { Tooltip } from 'antd';
import { getNifiOverlayContainer } from './overlayContainer';

export function ConfigHelp({ label, help }: { label: string; help: string }) {
  return (
    <Tooltip title={<span className="nifi-config-help__text">{help}</span>}
      trigger={['hover', 'focus']} getPopupContainer={getNifiOverlayContainer}>
      <button type="button" className="nifi-config-help" aria-label={`${label}说明`}
        onKeyDown={(event) => {
          if (event.key === 'Enter' || event.key === ' ') event.stopPropagation();
        }}
        onClick={(event) => event.stopPropagation()}>
        <QuestionCircleOutlined aria-hidden="true" />
      </button>
    </Tooltip>
  );
}

export default function ConfigFieldLabel({ label, help }: { label: string; help?: string }) {
  if (!help) return <>{label}</>;
  return <span className="nifi-config-label">{label}<ConfigHelp label={label} help={help} /></span>;
}
