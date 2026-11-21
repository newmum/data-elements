import {describe,it,expect,vi} from 'vitest';
import {createFrameTask,reuseModelComputation} from '../src/modelingUpdates';
import {createModelNode,previewModel} from '../src/modeling';
import {createModelingDemo} from '../src/modelingDemo';
import {generateModelSql,applyModelSqlDraft} from '../src/modelingSql';

function clock(){
 let id=0;const jobs=new Map<number,FrameRequestCallback>();
 return {jobs,request:(callback:FrameRequestCallback)=>{jobs.set(++id,callback);return id},cancel:(id:number)=>{jobs.delete(id)},frame:()=>{const callbacks=[...jobs.values()];jobs.clear();callbacks.forEach(callback=>callback(0))}};
}
describe('latest pointer sample per animation frame',()=>{
 it('coalesces a burst and consumes only the latest absolute value',()=>{
  const c=clock(),consume=vi.fn(),task=createFrameTask(consume,c.request,c.cancel);
  for(let i=0;i<240;i++)task.schedule({x:i,y:i*2});
  expect(c.jobs.size).toBe(1);expect(consume).not.toHaveBeenCalled();
  c.frame();expect(consume).toHaveBeenCalledExactlyOnceWith({x:239,y:478});
  expect(c.jobs.size).toBe(0);
 });
 it('flushes the last movement on pointer completion without a second delayed write',()=>{
  const c=clock(),consume=vi.fn(),task=createFrameTask(consume,c.request,c.cancel);
  task.schedule({x:7});task.flush();c.frame();
  expect(consume).toHaveBeenCalledExactlyOnceWith({x:7});expect(c.jobs.size).toBe(0);
 });
 it('discards queued work on cancellation and can start another gesture',()=>{
  const c=clock(),consume=vi.fn(),task=createFrameTask(consume,c.request,c.cancel);
  task.schedule(1);task.cancel();task.flush();c.frame();expect(consume).not.toHaveBeenCalled();
  task.schedule(2);c.frame();expect(consume).toHaveBeenCalledExactlyOnceWith(2);
 });
 it('preserves the latest node geometry and unrelated nodes when several samples merge',()=>{
  const c=clock(),nodes=[createModelNode('source','s',0),createModelNode('output','o',1)];
  let value=nodes;
  const task=createFrameTask<(current:typeof nodes)=>typeof nodes>(update=>{value=update(value)},c.request,c.cancel);
  for(let x=1;x<=4;x++)task.schedule(all=>all.map(node=>node.id==='s'?{...node,x:40+x,y:80+x}:node));
  task.flush();expect(value[0]).toMatchObject({x:44,y:84});expect(value[1]).toBe(nodes[1]);
 });
});
describe('SQL computation independent from layout',()=>{
 it('retains computation identity for node position and size changes',()=>{
  const nodes=createModelingDemo().nodes;
  const moved=nodes.map((node,i)=>i?node:{...node,x:555,y:333,width:310,height:120});
  expect(reuseModelComputation(nodes,moved)).toBe(nodes);
  expect(moved[0]).toMatchObject({x:555,y:333,width:310,height:120});
 });
 it('invalidates on every business definition change including order and identity',()=>{
  const nodes=createModelingDemo().nodes;
  for(const change of [{id:'different'},{kind:'jsonInput' as const},{name:'renamed'},{inputs:['another']},{config:{...nodes[0].config,table:'another'}}]){
   const changed=[{...nodes[0],...change},...nodes.slice(1)];
   expect(reuseModelComputation(nodes,changed)).toBe(changed);
  }
  const reordered=[...nodes].reverse(),removed=nodes.slice(1);
  expect(reuseModelComputation(nodes,reordered)).toBe(reordered);
  expect(reuseModelComputation(nodes,removed)).toBe(removed);
  expect(reuseModelComputation(null,nodes)).toBe(nodes);
 });
 it('preserves exact SQL, six-row preview and current geometry when applying a script edit',()=>{
  const demo=createModelingDemo(),settings={engine:'flink',mode:'batch'} as const;
  const moved=demo.nodes.map(node=>({...node,x:node.x+50,y:node.y+30}));
  const original=generateModelSql(demo.nodes,demo.tables,settings);
  expect(generateModelSql(reuseModelComputation(demo.nodes,moved),demo.tables,settings)).toEqual(original);
  expect(previewModel(moved,demo.outputId,demo.tables).rows).toEqual(demo.expectedRows);
  const applied=applyModelSqlDraft(moved,demo.tables,settings,original.sql.replaceAll("'completed'","'cancelled'"));
  expect(applied.error).toBeUndefined();expect(applied.nodes?.[0].x).toBe(moved[0].x);
  expect(applied.nodes?.find(node=>node.id==='demo-completed')?.config.value).toBe('cancelled');
 });
});
