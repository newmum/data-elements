/** Credential-free, insert-only shared Magic resource delta; stdout is UTF-8 SQL.
 * Existing resources are never overwritten. Inspect/backup the target control paths first.
 */
import fs from 'node:fs';
const root='/magic-api/api/05.数据治理/01.数据标准/03.标准落地/';
const records=[
 {name:'',id:null,kind:null,content:'this is directory'},
 {name:'group.json',id:'dwm_standard_landing_group_01',kind:null},
 {name:'01.字段标准引用清单.ms',id:'dwm_standard_landing_list_01',kind:'api'},
 {name:'02.保存字段标准引用.ms',id:'dwm_standard_landing_save_01',kind:'api'},
];
const value=v=>v===null?'NULL':`CONVERT(0x${Buffer.from(v,'utf8').toString('hex')} USING utf8mb4)`;
const lines=['-- Shared control resources only. No tenant-business writes or schema changes.',
 '-- Precondition: original /dwm/standard group exists and is backed up.',
 '-- This insert-only delta deliberately skips existing paths; compare them before any update.',
 'START TRANSACTION;'];
for(const record of records){const content=record.content??fs.readFileSync('backend/standard-landing/'+record.name,'utf8')+(record.name==='group.json'?'\n================================\n':'');
 lines.push(`INSERT INTO baseline.api_file_t (tid,file_kind,file_content,created_by,created_time,updated_by,updated_time,is_del,file_path)
SELECT ${value(record.id)},${value(record.kind)},${value(content)},'1',CURRENT_TIMESTAMP(6),'1',CURRENT_TIMESTAMP(6),0,${value(root+record.name)}
WHERE EXISTS (SELECT 1 FROM baseline.api_file_t WHERE tid='019b772ee9b44705a27710f91bbd731a' AND is_del=0)
AND NOT EXISTS (SELECT 1 FROM baseline.api_file_t WHERE file_path=${value(root+record.name)} AND is_del=0);`);
}
lines.push('COMMIT;',`SELECT file_path,file_kind FROM baseline.api_file_t WHERE is_del=0 AND file_path LIKE ${value(root+'%')} ORDER BY file_path;`,
 '-- Require exactly one directory, one group and two non-empty scripts.',
 '-- Then refresh/cold-load Magic and verify both metadata IDs have non-null runtime scripts.');
process.stdout.write(lines.join('\n')+'\n');
