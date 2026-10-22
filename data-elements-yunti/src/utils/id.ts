import { customAlphabet } from 'nanoid';

const alphabet = '0123456789abcdefghijklmnopqrstuvwxyz';
const nano = customAlphabet(alphabet, 10);

/** Generate a prefixed id, e.g. genId('node') -> 'node_8a1c3f9b2d'. */
export function genId(prefix: string): string {
  return `${prefix}_${nano()}`;
}
