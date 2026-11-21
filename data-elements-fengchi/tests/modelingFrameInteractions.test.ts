import {describe,it,expect,vi,beforeEach,afterEach} from 'vitest';
import type {PointerEvent} from 'react';
import {createModelNode} from '../src/modeling';
const probe=vi.hoisted(()=>({slots:[] as any[],cursor:0,updates:0,cleanups:[] as (()=>void)[]}));
vi.mock('react',()=>({
 useState:(initial:unknown)=>{const i=probe.cursor++;if(!(i in probe.slots))probe.slots[i]=typeof initial==='function'?(initial as ()=>unknown)():initial;return [probe.slots[i],(value:unknown)=>{const next=typeof value==='function'?(value as (old:unknown)=>unknown)(probe.slots[i]):value;if(!Object.is(next,probe.slots[i])){probe.slots[i]=next;probe.updates++}}]},
 useRef:(initial:unknown)=>{const i=probe.cursor++;if(!(i in probe.slots))probe.slots[i]={current:initial};return probe.slots[i]},
 useEffect:(effect:()=>void|(()=>void))=>{const i=probe.cursor++;if(!(i in probe.slots)){probe.slots[i]=true;const cleanup=effect();if(cleanup)probe.cleanups.push(cleanup)}},
}));
import {useModelingInteractions} from '../src/useModelingInteractions';
const source=createModelNode('source','s',0),target=createModelNode('filter','t',1),other=createModelNode('filter','other',2);
let jobs:Map<number,FrameRequestCallback>,listeners:Map<string,(event:any)=>void>,hit:ReturnType<typeof vi.fn>,rect:ReturnType<typeof vi.fn>,candidate:string;
beforeEach(()=>{
 probe.slots=[];probe.cursor=0;probe.updates=0;probe.cleanups=[];jobs=new Map();listeners=new Map();candidate='t';let id=0;
 vi.stubGlobal('requestAnimationFrame',(callback:FrameRequestCallback)=>{jobs.set(++id,callback);return id});
 vi.stubGlobal('cancelAnimationFrame',(id:number)=>jobs.delete(id));
 vi.stubGlobal('window',{addEventListener:(name:string,callback:(event:any)=>void)=>listeners.set(name,callback),removeEventListener:(name:string)=>listeners.delete(name)});
 hit=vi.fn(()=>({closest:()=>candidate?{dataset:{modelInput:candidate}}:null}));
 rect=vi.fn(()=>({left:100,top:100}));
 vi.stubGlobal('document',{elementFromPoint:hit});
});
afterEach(()=>{probe.cleanups.forEach(cleanup=>cleanup());vi.unstubAllGlobals()});
function render(connect=vi.fn(),zoom=1){probe.cursor=0;const hook=useModelingInteractions([source,target,other],zoom,connect);hook.canvasRef.current={getBoundingClientRect:rect} as unknown as HTMLDivElement;return hook}
function pointer(x=200,y=200){return {button:0,pointerId:1,clientX:x,clientY:y,target:{closest:()=>null},currentTarget:{setPointerCapture:vi.fn(),clientWidth:1000,clientHeight:800,getBoundingClientRect:()=>({left:0,top:0})},stopPropagation:vi.fn(),preventDefault:vi.fn()} as unknown as PointerEvent<HTMLDivElement>&PointerEvent<HTMLButtonElement>}
function frame(){const work=[...jobs.values()];jobs.clear();work.forEach(callback=>callback(0))}
describe('production canvas gesture hook',()=>{
 it('coalesces pan updates, preserves zoom coordinates and flushes final pointer position',()=>{
  let hook=render();hook.viewportHandlers.onPointerDown(pointer());const initial=probe.updates;
  for(let x=201;x<=240;x++)hook.viewportHandlers.onPointerMove(pointer(x,230));
  expect(probe.updates).toBe(initial);expect(jobs.size).toBe(1);frame();hook=render();
  expect(hook.pan).toEqual({x:40,y:30});
  hook.viewportHandlers.onPointerMove(pointer(250,240));hook.viewportHandlers.onPointerUp();hook=render();
  expect(hook.pan).toEqual({x:50,y:40});expect(jobs.size).toBe(0);expect(hook.panning).toBe(false);
 });
 it('hit-tests at most once per frame and uses the final drop target rather than a stale queued sample',()=>{
  const connect=vi.fn();let hook=render(connect,2);
  hook.outputHandlers('s').onPointerDown(pointer());rect.mockClear();
  for(let x=201;x<=240;x++)hook.outputHandlers('s').onPointerMove(pointer(x,260));
  expect(hit).not.toHaveBeenCalled();expect(rect).not.toHaveBeenCalled();frame();hook=render(connect,2);
  expect(hit).toHaveBeenCalledTimes(1);expect(rect).toHaveBeenCalledTimes(1);
  expect(hook.link).toMatchObject({x:70,y:80,targetId:'t',error:''});
  hook.outputHandlers('s').onPointerMove(pointer(300,280));candidate='other';
  hook.outputHandlers('s').onPointerUp(pointer(320,280));
  expect(connect).toHaveBeenCalledExactlyOnceWith('s','other');expect(jobs.size).toBe(0);
  frame();hook=render(connect,2);expect(hook.link).toBeNull();
 });
 it.each(['Escape','blur','cancel','capture'])('cancels pending link work on %s',kind=>{
  const connect=vi.fn();let hook=render(connect);
  hook.outputHandlers('s').onPointerDown(pointer());hook.outputHandlers('s').onPointerMove(pointer(300,240));
  if(kind==='Escape')listeners.get('keydown')!({key:'Escape'});
  if(kind==='blur')listeners.get('blur')!({});
  if(kind==='cancel')hook.outputHandlers('s').onPointerCancel();
  if(kind==='capture')hook.outputHandlers('s').onLostPointerCapture();
  expect(jobs.size).toBe(0);frame();hook=render(connect);
  expect(hook.link).toBeNull();expect(connect).not.toHaveBeenCalled();expect(hit).not.toHaveBeenCalled();
 });
 it('cleans up pending frame tasks on unmount',()=>{
  const hook=render();hook.viewportHandlers.onPointerDown(pointer());hook.viewportHandlers.onPointerMove(pointer(300,240));
  expect(jobs.size).toBe(1);probe.cleanups.forEach(cleanup=>cleanup());probe.cleanups=[];
  expect(jobs.size).toBe(0);expect(listeners.size).toBe(0);
 });
});
