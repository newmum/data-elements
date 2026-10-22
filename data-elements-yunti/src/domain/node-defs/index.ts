import type { NodeDef } from '../node-def';
import { ioNodeDefs } from './io';
import { datasourceNodeDefs } from './datasource';
import { processingNodeDefs } from './processing';
import { logicNodeDefs } from './logic';
import { toolsNodeDefs } from './tools';

export const allNodeDefs: NodeDef[] = [
  ...ioNodeDefs,
  ...datasourceNodeDefs,
  ...processingNodeDefs,
  ...logicNodeDefs,
  ...toolsNodeDefs,
];
