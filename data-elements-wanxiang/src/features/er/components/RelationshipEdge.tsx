import { BaseEdge, EdgeLabelRenderer, getSmoothStepPath, Position } from '@xyflow/react';
import type { Edge, EdgeProps } from '@xyflow/react';
import { memo } from 'react';
import { Tooltip } from 'antd';
import { useStudio } from '../store';
import { Icon } from './Icon';
export type RelationFlowEdge=Edge<{relationshipId:string;kind:'catalog'|'logical'|'suggested';showLabel:boolean;label:string;dimmed:boolean;self:boolean;multiplicity:string;sourceMax:string;targetMax:string;sourceMin:string;targetMin:string},'relationship'>;
function Endpoint({x,y,sign,max,min,color}:{x:number;y:number;sign:number;max:string;min:string;color:string}){
  const fork=max==='many'?`M ${x+sign*15} ${y} L ${x+sign*4} ${y-5} M ${x+sign*15} ${y} L ${x+sign*4} ${y+5}`:max==='1'?`M ${x+sign*7} ${y-5} v10`:null;
  return <g stroke={color} strokeWidth={1.4}>{fork?<path d={fork} fill="none"/>:<text x={x+sign*9} y={y-5} fill={color} stroke="none" fontSize={9} textAnchor="middle">?</text>}{min==='0'?<circle cx={x+sign*22} cy={y} r={3.2} fill="var(--surface)"/>:min==='1'?<path d={`M${x+sign*20},${y-5}v10`}/>:null}</g>;
}
export const RelationshipEdge=memo(function RelationshipEdge(props:EdgeProps<RelationFlowEdge>){
  const {sourceX,sourceY,targetX,targetY,sourcePosition,targetPosition,id,data,selected}=props;
  if(!data)return null;
  let [path,labelX,labelY]=getSmoothStepPath({sourceX,sourceY,targetX,targetY,sourcePosition,targetPosition,borderRadius:14,offset:26});
  if(data.self){const outside=Math.min(sourceX,targetX)-75;path=`M ${sourceX} ${sourceY} C ${outside} ${sourceY}, ${outside} ${targetY}, ${targetX} ${targetY}`;labelX=outside-6;labelY=(sourceY+targetY)/2;}
  const color=`var(--edge-${data.kind})`;
  const srcSign=sourcePosition===Position.Left?-1:1;
  const dstSign=targetPosition===Position.Left?-1:1;
  return <g className={`relation-edge ${selected?'edge-selected':''} ${data.dimmed?'edge-dimmed':''}`}>
    <BaseEdge id={id} path={path} interactionWidth={24} style={{stroke:color,strokeWidth:selected?2.4:1.6,strokeDasharray:data.kind==='suggested'?'6 5':undefined}}/>
    <Endpoint x={sourceX} y={sourceY} sign={srcSign} max={data.sourceMax} min={data.sourceMin} color={color}/>
    <Endpoint x={targetX} y={targetY} sign={dstSign} max={data.targetMax} min={data.targetMin} color={color}/>
    {(data.showLabel||selected)&&<EdgeLabelRenderer><Tooltip title={`${data.label} · 引用方到被引用方 ${data.multiplicity}`}><button className={`edge-label edge-label-${data.kind} nodrag nopan ${selected?'selected':''} ${data.dimmed?'dimmed':''}`} style={{transform:`translate(-50%, -50%) translate(${labelX}px,${labelY}px)`}} onClick={e=>{e.stopPropagation();useStudio.getState().selectRelationship(data.relationshipId);}} aria-label={`查看关系 ${data.label}`}>
      {data.kind==='suggested'&&<Icon name="spark" size={11}/>}<span>{data.label}</span><span className="edge-multiplicity">{data.multiplicity}</span>
    </button></Tooltip></EdgeLabelRenderer>}
  </g>;
});
