import {describe,it,expect} from 'vitest';
import {hashModelText} from '../src/modelingHash';
const oracle=async(value:string)=>[...new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(value)))].map(x=>x.toString(16).padStart(2,'0')).join('');
describe('bounded synchronous SHA-256',()=>{
 it('matches the empty and abc standard vectors',()=>{
  expect(hashModelText('')).toBe('e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855');
  expect(hashModelText('abc')).toBe('ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad');
 });
 it('matches the standard multi-block vector',()=>{
  expect(hashModelText('abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq')).toBe('248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1');
 });
 it('matches platform WebCrypto across padding boundaries',async()=>{
  for(const count of [1,55,56,57,63,64,65,119,120,127,128,129,1000]){
   const value='a'.repeat(count);expect(hashModelText(value)).toBe(await oracle(value));
  }
 });
 it('encodes Unicode, null bytes and lone surrogates as UTF-8',async()=>{
  for(const value of ['风驰数据计算平台','😀é\u0000終','\ud800','\udc00','e\u0301','\ufeffabc'])expect(hashModelText(value)).toBe(await oracle(value));
 });
 it('accepts the bounded maximum and rejects oversize before encoding',async()=>{
  const value='界'.repeat(100000);expect(hashModelText(value)).toBe(await oracle(value));
  expect(()=>hashModelText('x'.repeat(100001))).toThrow('100000');
  expect(()=>hashModelText(null as unknown as string)).toThrow('字符串');
 });
 it('has no mutable state shared across calls',()=>{
  const a=hashModelText('abc');hashModelText('other');expect(hashModelText('abc')).toBe(a);
  expect(hashModelText('ABC')).not.toBe(a);
 });
});
