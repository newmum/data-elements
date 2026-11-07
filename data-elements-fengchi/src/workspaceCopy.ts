import type {TableDef} from './data';

// Migrate only built-in labels and seed values from saved earlier revisions.
// Custom table definitions, changed records and user-entered text are retained.
export function migrateLegacyTableCopy(tables:Record<string,TableDef>):Record<string,TableDef>{
 const next=structuredClone(tables);
 for(const table of Object.values(next)){
  if(table.name==='users'&&table.comment==='用户账户 · 演示数据（全部虚构）')table.comment='用户账户';
  for(const column of table.columns){
   if(table.name==='audit_logs'&&column.name==='ip_address'&&column.comment==='演示 IP（文档保留地址）')column.comment='IP 地址';
  }
  if(table.name==='users')for(const row of table.rows){
   if(typeof row.email==='string'&&/^demo\.user\d{2}@example\.com$/.test(row.email))row.email=row.email.replace(/^demo\./,'');
  }
 }
 return next;
}
