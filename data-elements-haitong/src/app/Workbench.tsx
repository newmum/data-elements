import { createContext, useContext } from 'react';
import type { Task, Workspace } from '../domain/types';
import type { Action } from '../domain/engine';
export interface WorkbenchContext { state:Workspace; act:(action:Action)=>Promise<Workspace>; editTask:(task?:Task,scenario?:Task['scenario'])=>void; showTask:(id:string)=>void; showRun:(id:string)=>void; openCanvas:(id:string)=>void; }
export const Workbench=createContext<WorkbenchContext|null>(null);
export function useWorkbench(){const ctx=useContext(Workbench);if(!ctx)throw new Error('工作区尚未初始化');return ctx;}
