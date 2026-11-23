import {describe,expect,it,vi} from 'vitest';
const probe=vi.hoisted(()=>({setOpen:vi.fn()}));
vi.mock('react',async importOriginal=>({...await importOriginal<typeof import('react')>(),useState:(value:unknown)=>[value,probe.setOpen],useEffect:()=>{}}));
import ModelingSqlDiagnostics from '../src/ModelingSqlDiagnostics';
import {renderToStaticMarkup} from 'react-dom/server';
describe('script diagnostics affordance',()=>{
 it('renders nothing when no actual diagnostic exists',()=>{expect(ModelingSqlDiagnostics({id:'d',diagnostics:[],nodeNames:{}})).toBeNull()});
 it.each(['error','warning'] as const)('uses %s severity and exposes complete content to hover, focus and touch',level=>{const diagnostics=[{level,message:'完整诊断第一项',nodeId:'node-1'},{level:'warning' as const,message:'另一项诊断'}],element=ModelingSqlDiagnostics({id:'d',diagnostics,nodeNames:{'node-1':'客户来源'}})!;expect(element.props.trigger).toEqual(['hover','focus','click']);expect(element.props.children.props['data-severity']).toBe(level);expect(element.props.children.props['aria-describedby']).toBe('d');expect(element.props.children.props['aria-label']).toContain(level==='error'?'阻断':'生成提示');const content=renderToStaticMarkup(element.props.content);for(const value of ['完整诊断第一项','另一项诊断','客户来源'])expect(content).toContain(value);expect(element.props.content.props.tabIndex).toBe(0)});
 it('keeps first touch/click open even after focus toggles the native trigger, and closes on Escape',()=>{probe.setOpen.mockClear();const element=ModelingSqlDiagnostics({id:'d',diagnostics:[{level:'warning',message:'提示'}],nodeNames:{}})!;element.props.onOpenChange(false);element.props.children.props.onClick();expect(probe.setOpen).toHaveBeenLastCalledWith(true);const stopPropagation=vi.fn();element.props.children.props.onKeyDown({key:'Escape',stopPropagation});expect(probe.setOpen).toHaveBeenLastCalledWith(false);expect(stopPropagation).toHaveBeenCalledOnce()});
 it('does not leave a portal or icon for an inactive model',()=>{expect(ModelingSqlDiagnostics({active:false,id:'d',diagnostics:[{level:'error',message:'另一模型的错误'}],nodeNames:{}})).toBeNull()});
 it('prioritizes a blocking error over simultaneous warnings',()=>{const element=ModelingSqlDiagnostics({id:'d',diagnostics:[{level:'warning',message:'提示'},{level:'error',message:'阻断'}],nodeNames:{}})!;expect(element.props.children.props['data-severity']).toBe('error')});
});
