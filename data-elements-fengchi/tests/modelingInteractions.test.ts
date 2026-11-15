import {describe,it,expect} from 'vitest';
import {createModelNode} from '../src/modeling';
import {modelInputCapacity,modelHasOutput,modelPoint,validateModelConnection,validateModelInputs} from '../src/modelingInteractions';
import hook from '../src/useModelingInteractions.ts?raw';
const a=createModelNode('source','a',0),b=createModelNode('filter','b',1),c=createModelNode('join','c',2),out=createModelNode('output','o',3);
describe('model canvas connection validation',()=>{
 it('allows valid inputs in order and rejects duplicates and capacity overflow',()=>{expect(validateModelConnection([a,b],'a','b')).toBe('');expect(validateModelConnection([a,{...b,inputs:['a']}],'a','b')).toContain('存在');expect(validateModelConnection([a,b,{...c,inputs:['a','b']}],'o','c')).toContain('不存在');expect(validateModelInputs([a,b,c],'c',['a','b'])).toBe('');expect(validateModelInputs([a,b,c,out],'b',['a','c'])).toContain('已满')});
 it('rejects self loops, cycles, source inputs and sink outputs',()=>{expect(validateModelConnection([a,b],'b','b')).toContain('自身');expect(validateModelConnection([a,b],'b','a')).toContain('输入算子');expect(validateModelConnection([a,b,out],'o','b')).toContain('输出节点');expect(validateModelConnection([{...b,inputs:['c']},c],'b','c')).toContain('循环')});
 it('matches catalog arity for planned and running operators',()=>{expect(modelInputCapacity('httpInput')).toBe(0);expect(modelInputCapacity('dimensionLookup')).toBe(2);expect(modelInputCapacity('filter')).toBe(1);expect(modelHasOutput('databaseOutput')).toBe(false)});
 it('converts pointer coordinates using canvas bounds including pan and zoom',()=>{expect(modelPoint(340,220,{left:100,top:100},2)).toEqual({x:120,y:60})});
 it('captures pointers separately and cancels gestures on escape, blur and cancellation',()=>{expect(hook).toContain("event.key==='Escape'");expect(hook).toContain("window.addEventListener('blur',cancel)");expect(hook).toContain('e.stopPropagation()');expect(hook).toContain('onPointerCancel:cancel');expect(hook).toContain('document.elementFromPoint');expect(hook).toContain('setPointerCapture(e.pointerId)')});
});
