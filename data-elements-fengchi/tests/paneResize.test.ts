import {describe,it,expect} from 'vitest';
import {clampPaneWidth} from '../src/PaneResizeHandle';
describe('pane width limits',()=>{
 it('allows widths inside the available range',()=>expect(clampPaneWidth(280,160,420)).toBe(280));
 it('caps both edges',()=>{expect(clampPaneWidth(80,160,420)).toBe(160);expect(clampPaneWidth(1000,160,420)).toBe(420);});
 it('keeps the minimum usable width when viewport space is constrained',()=>expect(clampPaneWidth(220,160,140)).toBe(160));
});
