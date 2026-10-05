"""Generate standalone TS types from the reviewed asset API contract; no runtime YAML dependency."""
from pathlib import Path
import os,sys,json
sys.path.insert(0,str(Path(os.environ.get('TEMP','.'))/'haoyue-codegen-deps'))
import yaml
root=Path(__file__).resolve().parents[1]
source=root.parent/'deliverables/皓月数据资产中心整体设计/附录C_接口合同.yaml'
contract=yaml.safe_load(source.read_text(encoding='utf-8'))
schemas=contract['components']['schemas']
(source.parent/'working/haoyue-openapi.json').write_text(json.dumps(contract,ensure_ascii=False,indent=2),encoding='utf-8')
def ts(s):
 if '$ref' in s: return s['$ref'].rsplit('/',1)[-1]
 nullable=' | null' if s.get('nullable') else ''
 if 'enum' in s: return ' | '.join(json.dumps(x,ensure_ascii=False) for x in s['enum'])+nullable
 if 'oneOf' in s or 'anyOf' in s: return '('+' | '.join(ts(x) for x in s.get('oneOf',s.get('anyOf')))+')'+nullable
 if 'allOf' in s: return '('+' & '.join(ts(x) for x in s['allOf'])+')'+nullable
 t=s.get('type','object')
 if t=='array': return 'Array<'+ts(s.get('items',{}))+'>'+nullable
 if t in ('integer','number'): return 'number'+nullable
 if t=='string': return 'string'+nullable
 if t=='boolean': return 'boolean'+nullable
 props=s.get('properties',{})
 if not props: return ('Record<string, '+(ts(s['additionalProperties']) if isinstance(s.get('additionalProperties'),dict) else 'unknown')+'>')+nullable
 required=s.get('required',[])
 lines=['{']
 for k,v in props.items():
  if v.get('description'): lines.append('  /** '+v['description'].replace('*/','* /').replace('\n',' ')+' */')
  lines.append('  '+json.dumps(k,ensure_ascii=False)+('' if k in required else '?')+': '+ts(v)+';')
 lines.append('}')
 return '\n'.join(lines)+nullable
out=['// Generated from the reviewed Haoyue OpenAPI contract. Run scripts/generate-assets-types.py to update.']
for name,schema in schemas.items():
 if schema.get('description'):out.append('/** '+schema['description'].replace('*/','* /').replace('\n',' ')+' */')
 out.append('export type '+name+' = '+ts(schema)+';\n')
(root/'src/services/assets.types.ts').write_text('\n'.join(out),encoding='utf-8')
print('Generated '+str(len(schemas))+' asset contract types')
