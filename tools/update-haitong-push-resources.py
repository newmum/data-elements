#!/usr/bin/env python3
"""Apply the selected, reviewed Magic resources shipped in a Haitong native package.

Uses the editor's login/save/read protocol; never connects directly to the database.
Default is comparison only. --apply requires an exact known predecessor or current script.
Credentials are prompted and never logged. Python 3 standard library only.
"""
from __future__ import annotations
import argparse
import getpass
import hashlib
import json
import os
from pathlib import Path
import urllib.error
import urllib.parse
import urllib.request


def digest(script):
    return hashlib.sha256(script.replace('\r\n', '\n').replace('\r', '\n').strip().encode()).hexdigest()


def verify_predecessor(remote, incoming, entry):
    if remote.get('id') != incoming['id'] or remote.get('path') != incoming['path'] or remote.get('groupId') != incoming['groupId']:
        raise ValueError('Resource identity/path/group differs; manual merge required')
    if digest(remote.get('script') or '') not in {entry['previousScriptSha256'], digest(incoming['script'])}:
        raise ValueError('Online script has custom changes; manual merge required')
    if entry['kind'] == 'api':
        # This update changes exactly one header requirement. Preserve every
        # other editor option, example, audit field and custom description.
        headers=remote.get('headers') or []
        matched=[h for h in headers if h.get('name') == 'X-Api-Key']
        if len(matched) != 1:
            raise ValueError('X-Api-Key header definition differs; manual merge required')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url',default='http://127.0.0.1:18083',help='Haitong service root, not metadata platform')
    parser.add_argument('--username',default='admin')
    parser.add_argument('--apply',action='store_true')
    parser.add_argument('--bundle',type=Path,default=Path(__file__).resolve().parents[1]/'magic-update')
    args=parser.parse_args()
    parsed=urllib.parse.urlsplit(args.url)
    if parsed.scheme not in {'http','https'} or not parsed.hostname or parsed.username or parsed.query or parsed.fragment or parsed.path not in {'','/'}:
        raise ValueError('--url must be a service root URL')
    base=args.url.rstrip('/')+'/api/web'
    entries=json.loads((args.bundle/'manifest.json').read_text(encoding='utf-8'))
    opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
    password=getpass.getpass('Haitong Magic editor password: ')
    login=urllib.parse.urlencode({'username':args.username,'password':password}).encode()
    with opener.open(urllib.request.Request(base+'/login',data=login),timeout=20) as reply:
        token=reply.headers.get('Magic-Token')
        if not json.load(reply).get('data') or not token:raise ValueError('Editor login failed')
    password=None

    def load(entry):
        request=urllib.request.Request(base+'/resource/file/'+urllib.parse.quote(entry['id'],safe=''),headers={'Magic-Token':token})
        with opener.open(request,timeout=20) as reply:
            result=json.load(reply)
        if result.get('code') != 1 or not isinstance(result.get('data'),dict):raise ValueError('Editor read failed')
        return result['data']

    plan=[]
    for entry in entries:
        source=(args.bundle/entry['file']).read_text(encoding='utf-8-sig')
        meta,script=source.split('================================',1)
        incoming=json.loads(meta);incoming['script']=script.replace('\r\n','\n').strip()
        previous=load(entry);verify_predecessor(previous,incoming,entry)
        updated=dict(previous)
        if entry['kind'] == 'function':updated['script']=incoming['script']
        else:
            updated['headers']=[dict(h,required=False,description='push-validation-enabled=true 时填写业务密钥；false 时可省略') if h.get('name')=='X-Api-Key' else h for h in previous['headers']]
        changed=updated!=previous
        print(entry['id']+(': update selected' if changed else ': already current'))
        plan.append((entry,previous,updated,changed))
    if not args.apply:
        print('Comparison only. Re-run with --apply to publish these selected resources.');return
    # Check all resources again before any write. Save only the reviewed resources,
    # keep backups private, and verify each editor runtime result after saving.
    for entry,previous,_,_ in plan:
        if load(entry)!=previous:raise ValueError('Concurrent editor change; no updates applied')
    backup=Path(__file__).resolve().parents[1]/'config/secrets/magic-resource-backups'
    backup.mkdir(parents=True,exist_ok=True)
    if os.name=='nt':
        import subprocess
        account=subprocess.check_output(['whoami'],text=True).strip()
        subprocess.run(['icacls',str(backup),'/inheritance:r','/grant:r',account+':(OI)(CI)F','SYSTEM:(OI)(CI)F'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    else:backup.chmod(0o700)
    import uuid
    for entry,previous,updated,changed in plan:
        if not changed:continue
        if load(entry)!=previous:raise ValueError('Concurrent editor change; stopped before this resource')
        snapshot=backup/(uuid.uuid4().hex+'.json')
        with snapshot.open('x',encoding='utf-8') as out:json.dump(previous,out,ensure_ascii=False)
        snapshot.chmod(0o600)
        script=updated.pop('script')
        content=json.dumps(updated,ensure_ascii=False)+'\n================================\n'+script
        request=urllib.request.Request(base+'/resource/file/'+entry['kind']+'/save',data=content.encode(),headers={'Magic-Token':token,'Content-Type':'text/plain;charset=UTF-8'})
        with opener.open(request,timeout=30) as reply:saved=json.load(reply)
        if saved.get('code')!=1 or saved.get('data')!=entry['id']:raise ValueError('Editor save rejected')
        loaded=load(entry)
        if digest(loaded.get('script') or '')!=digest(script):raise ValueError('Runtime script readback differs')
        if entry['kind']=='api' and next(h for h in loaded['headers'] if h.get('name')=='X-Api-Key').get('required'):
            raise ValueError('Runtime header readback differs')
        print(entry['id']+': published and runtime verified')


if __name__=='__main__':
    try:main()
    except (urllib.error.URLError,ValueError,OSError,KeyError) as error:
        # Do not print HTTP bodies or connection/config objects containing credentials.
        if isinstance(error,urllib.error.HTTPError):message='HTTP '+str(error.code)+' from Haitong editor'
        elif isinstance(error,urllib.error.URLError):message='Cannot connect to Haitong editor'
        else:message=str(error)
        raise SystemExit(message)
