"""Actual browser-local Mock/API persistence checks; no React or external server."""
import json,os,shutil,threading,http.server
from pathlib import Path
from functools import partial
from playwright.sync_api import sync_playwright
root=Path(__file__).resolve().parents[2]
class Handler(http.server.SimpleHTTPRequestHandler):
 def log_message(self,*args): pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),partial(Handler,directory=str(root/'.browser-review')))
threading.Thread(target=server.serve_forever,daemon=True).start()
url=f'http://127.0.0.1:{server.server_port}/'
checks=[]
fatal=None
def check(name,condition,details=None):
 checks.append({'name':name,'passed':bool(condition),'details':details})
 if not condition: raise AssertionError(name+': '+str(details))
try:
 with sync_playwright() as p:
  executable=os.environ.get('CHROMIUM_PATH') or shutil.which('chromium')
  browser=p.chromium.launch(**({'executable_path':executable} if executable else {}),headless=True)
  context=browser.new_context()
  page=context.new_page();page.goto(url);page.wait_for_function('window.ready===true')
  s=page.evaluate('async()=>await wx.api.loadSession()')
  check('Fresh browser automatically enters local admin workspace',s['authenticated'] and len(s['workspaces'])==1,s['name'])
  counts=page.evaluate('async()=>await wx.api.api("queries/overview")')
  check('Overview uses the persisted local dataset',counts['sources']==8 and counts['entities']==24 and counts['models']==3,counts)
  view=page.evaluate('async()=>{const d=await wx.workspaceService.load();window.data=d;return {models:d.diagrams.length,entities:d.snapshot.entities.length,relations:d.relationships.length,nodes:d.diagrams.find(v=>v.id===d.activeDiagramId).nodes.length}}')
  check('Actual ER DTO service loads model, metadata and relationships',view['models']==3 and view['entities']==24 and view['relations']>0 and view['nodes']>0,view)
  saved=page.evaluate('''async()=>{const d=window.data,v=d.diagrams.find(v=>v.id===d.activeDiagramId);v.nodes[0].position.x+=17;await wx.workspaceService.save(d);const m=await wx.api.api('models/'+v.id);return {id:v.id,x:v.nodes[0].position.x,saved:m.layout.nodes[0].position.x,version:m.version}}''')
  check('ER layout saves through original service facade',saved['x']==saved['saved'],saved)
  page.reload();page.wait_for_function('window.ready===true');page.evaluate('async()=>await wx.api.loadSession()')
  recovered=page.evaluate('async(id)=>await wx.api.api("models/"+id)',saved['id'])
  check('Layout survives a real Chromium page reload',recovered['layout']['nodes'][0]['position']['x']==saved['x'])
  second=context.new_page();second.goto(url);second.wait_for_function('window.ready===true');second.evaluate('async()=>await wx.api.loadSession()')
  earlier=second.evaluate('async(id)=>await wx.api.api("models/"+id)',saved['id'])
  changed=page.evaluate('async(m)=>await wx.api.api("models/"+m.id,{method:"PUT",version:m.version,body:{name:"浏览器验证模型"}})',recovered)
  conflict=second.evaluate('async(m)=>{try{await wx.api.api("models/"+m.id,{method:"PUT",version:m.version,body:{name:"旧页面写入"}});return null;}catch(e){return {status:e.status,code:e.code};}}',earlier)
  check('Two real browser tabs cannot silently overwrite a newer model',conflict and conflict['status']==412,conflict)
  reread=second.evaluate('async(id)=>await wx.api.api("models/"+id)',saved['id'])
  check('Second tab reads persisted current state',reread['name']=='浏览器验证模型' and reread['version']==changed['version'])
  job=page.evaluate('''async()=>{const p=(await wx.api.all('collection-plans'))[0];return await wx.api.api('collection-plans/'+p.id+'/run',{method:'POST',body:{}});}''')
  check('Collection starts as a genuine local pending job',job['status']=='QUEUED',job['status'])
  page.wait_for_timeout(2900)
  final=page.evaluate('async(id)=>await wx.api.api("jobs/"+id)',job['id'])
  check('Actual browser timer advances and persists local collection',final['status']=='SUCCEEDED',{'status':final['status'],'result':final.get('result')})
  job2=page.evaluate('''async()=>{const p=(await wx.api.all('collection-plans'))[1];const j=await wx.api.api('collection-plans/'+p.id+'/run',{method:'POST',body:{}});await wx.api.api('jobs/'+j.id+'/cancel',{method:'POST',version:j.version,body:{}});return j;}''')
  page.wait_for_timeout(2100)
  cancelled=page.evaluate('async(id)=>await wx.api.api("jobs/"+id)',job2['id'])
  check('Cancelled browser job is not later completed by a timer',cancelled['status']=='CANCELLED')
  changedIdentity=page.evaluate('async()=>await wx.api.switchIdentity("reviewer")')
  check('Local review identity requires no password',changedIdentity['authenticated'] and changedIdentity['workspaces'][0]['role']=='REVIEWER')
  denial=page.evaluate('async()=>{try{await wx.api.api("models",{method:"POST",body:{name:"无权限"}});return null}catch(e){return e.status}}')
  check('Local role workflow rejects unrelated writes',denial==403)
  page.evaluate('async()=>await wx.api.switchIdentity("admin")')
  backup=page.evaluate('async()=>await wx.api.api("backup")')
  check('Export returns a recoverable local workspace with records',bool(backup.get('workspace',{}).get('records')) and 'password' not in json.dumps(backup).lower(),list(backup))
  check('Business operations made no fetch calls',page.evaluate('networkCalls.length')==0 and second.evaluate('networkCalls.length')==0)
  check('Application state is present in real browser storage',page.evaluate('localStorage.getItem("wanxiang:local-workspace:3.1")?.length>1000'))
  browser.close()
except Exception as exc:
 fatal=str(exc)
 raise
finally:
 server.shutdown()
 report={'scope':'Actual Mock/API/ER DTO services in system Chromium; real localStorage/timers/two tabs. Not full React UI or an external backend.', 'executionStatus':'BLOCKED_OR_FAILED' if fatal else 'COMPLETED','executionError':fatal,'checks':checks,'passed':sum(x['passed'] for x in checks),'failed':sum(not x['passed'] for x in checks)}
 out=root/'doc/validation';out.mkdir(parents=True,exist_ok=True);(out/'browser-local-runtime.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
 print(json.dumps(report,ensure_ascii=False,indent=2))
