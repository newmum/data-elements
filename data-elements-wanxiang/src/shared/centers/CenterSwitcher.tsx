import { CenterSwitcher as PlatformCenterSwitcher } from '../../app/PlatformShell';
import type { Center } from './navigation';
export { PanshiLogo } from '../../panshi/design/PanshiLogo';
export function CenterSwitcher({ center, collapsed = false, beforeLeave }: { center: Center; collapsed?: boolean; beforeLeave?: () => Promise<boolean> }) {
  return <PlatformCenterSwitcher collapsed={collapsed} center={center === 'panshi' ? 'resource' : 'governance'} beforeLeave={beforeLeave}/>;
}
