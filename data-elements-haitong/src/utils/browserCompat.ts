/** Clone the JSON-like configuration model without relying on Chrome 98 structuredClone. */
export function cloneConfiguration<T>(value: T): T {
  if (typeof globalThis.structuredClone === 'function') return globalThis.structuredClone(value);
  const seen = new WeakMap<object, unknown>();
  function copy(input: unknown): unknown {
    if (input === null || typeof input !== 'object') return input;
    if (seen.has(input)) return seen.get(input);
    if (input instanceof Date) return new Date(input.getTime());
    if (!Array.isArray(input) && Object.getPrototypeOf(input) !== Object.prototype && Object.getPrototypeOf(input) !== null) {
      throw new TypeError('配置包含无法复制的数据类型');
    }
    const result: any = Array.isArray(input) ? new Array(input.length) : {};
    seen.set(input, result);
    for (const key of Object.keys(input)) Object.defineProperty(result, key, { value: copy((input as any)[key]), enumerable: true, writable: true, configurable: true });
    return result;
  }
  return copy(value) as T;
}
