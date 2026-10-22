import { register } from '@antv/x6-react-shape';
import { BaseNode } from './nodes/BaseNode';

let registered = false;

export function registerFlowShapes(): void {
  if (registered) return;
  register({
    shape: 'flowserve-node',
    width: 214,
    height: 72,
    component: BaseNode,
    effect: ['data'],
  });
  registered = true;
}
