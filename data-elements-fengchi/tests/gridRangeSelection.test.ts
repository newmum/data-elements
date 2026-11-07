import {describe,it,expect} from 'vitest';
import {rectangularSelection,selectionToTsv,gridRowKey,edgeScrollDelta} from '../src/gridRangeSelection';

describe('rectangular grid selection',()=>{
  it('uses visible sorted row and column order in both drag directions',()=>{
    const rows=[9,3,7,1],columns=['name','id','email'];
    const a={rowId:3,column:'email'},b={rowId:1,column:'name'};
    const forward=rectangularSelection(rows,columns,a,b);
    expect(forward).toEqual(rectangularSelection(rows,columns,b,a));
    expect(forward.rowIds).toEqual([3,7,1]);expect(forward.columns).toEqual(columns);expect(forward.count).toBe(9);
  });
  it('never includes filtered or off-page rows',()=>{
    const result=rectangularSelection([8,2],['a','b'],{rowId:8,column:'a'},{rowId:2,column:'b'});
    expect(result.rowIds).toEqual([8,2]);expect(result.count).toBe(4);
    expect(rectangularSelection([8,2],['a'],{rowId:1,column:'a'},{rowId:2,column:'a'}).count).toBe(0);
  });
  it('keeps numeric and string row keys distinct',()=>{
    expect(gridRowKey(1)).not.toBe(gridRowKey('1'));
    expect(rectangularSelection([1,'1'],['a'],{rowId:'1',column:'a'},{rowId:'1',column:'a'}).rowIds).toEqual(['1']);
  });
  it('keeps single click a one-cell selection',()=>{
    expect(rectangularSelection([1,2],['a','b'],{rowId:2,column:'a'},{rowId:2,column:'a'}).count).toBe(1);
  });
  it('serializes visual rectangles with tabs, newlines, quotes, NULL and zero',()=>{
    const selection=rectangularSelection([2,1],['a','b','c'],{rowId:1,column:'c'},{rowId:2,column:'a'});
    const values:Record<string,Record<string,unknown>>={'2':{a:'a\tb',b:'line\nnext',c:'say "hi"'},'1':{a:null,b:'',c:0}};
    expect(selectionToTsv(selection,(id,col)=>values[String(id)][col])).toBe('"a\tb"\t"line\nnext"\t"say ""hi"""\n\t\t0');
  });
});

describe('edge autoscroll',()=>{
  it('scrolls immediately for the reported bottom/right pointer position',()=>{
    expect(edgeScrollDelta(955,220,976)).toBeGreaterThan(0);
    expect(edgeScrollDelta(703,192,710)).toBeGreaterThan(0);
  });
  it('stays still in the center and caps outward speed',()=>{
    expect(edgeScrollDelta(500,220,976)).toBe(0);
    expect(edgeScrollDelta(1000,220,976)).toBe(18);
    expect(edgeScrollDelta(100,220,976)).toBe(-18);
    expect(edgeScrollDelta(100,220,220)).toBe(0);
  });
});
