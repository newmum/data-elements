import {describe,it,expect} from 'vitest';
import {hoverTopMenu,closeTopMenu} from '../src/TopMenuBar';
describe('desktop menu activation',()=>{
 it('does not open on hover while inactive',()=>expect(hoverTopMenu(null,'文件')).toBeNull());
 it('switches active menus on hover',()=>expect(hoverTopMenu('文件','编辑')).toBe('编辑'));
 it('ignores a previous popup late close event after switching',()=>expect(closeTopMenu('编辑','文件')).toBe('编辑'));
 it('closes the active menu',()=>expect(closeTopMenu('文件','文件')).toBeNull());
});
