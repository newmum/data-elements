/** randomUUID is secure-context-only; getRandomValues also works on intranet HTTP. */
export function reportRandomId(random:Pick<Crypto,'getRandomValues'>=globalThis.crypto):string{
  if(!random?.getRandomValues)throw new Error('当前浏览器不支持安全随机标识，请使用 Chrome 100 或更新版本');
  const bytes=random.getRandomValues(new Uint8Array(16));
  bytes[6]=(bytes[6]&15)|64;bytes[8]=(bytes[8]&63)|128;
  const hex=Array.from(bytes,byte=>byte.toString(16).padStart(2,'0')).join('');
  return `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`;
}
