import test from 'node:test';
import assert from 'node:assert/strict';
import { createSeed } from '../src/domain/seed.ts';
import { effectiveAccess, grantIsCurrent, taskCounts } from '../src/domain/engine.ts';
const now=new Date('2026-09-27T10:00:00.000Z');
const db=createSeed(now);
test('V2: identical initialization time produces identical fictional data',()=>assert.deepEqual(createSeed(now),db));
test('V2: identity domains contain 180 business people, 1 platform identity and 60 citizens',()=>{
 assert.equal(db.users.length,241);assert.equal(db.users.filter(u=>u.kind==='person').length,180);assert.equal(db.users.filter(u=>u.kind==='admin').length,1);assert.equal(db.users.filter(u=>u.kind==='citizen').length,60);
});
test('V2: account names and row IDs are unique',()=>{
 for(const rows of [db.users,db.orgs,db.apps,db.roles,db.resources,db.grants,db.groups,db.logs,db.catalog,db.tasks])assert.equal(new Set(rows.map(r=>r.id)).size,rows.length);
 assert.equal(new Set(db.users.map(u=>u.domain+':'+u.account.toLowerCase())).size,db.users.length);
});
test('V2: every workforce appointment references an organization in its domain',()=>{
 for(const u of db.users.filter(u=>u.domain==='workforce')){assert.ok(db.orgs.some(o=>o.id===u.orgId&&o.domain===u.domain));assert.equal(u.appointments.filter(a=>a.primary).length,1);}
});
test('V2: employee types partition the business-user population',()=>{
 const people=db.users.filter(u=>u.kind==='person');assert.equal(people.filter(u=>['employee','partner','temporary'].includes(u.employmentType||'')).length,people.length);
});
test('V2: every role and resource belongs to a known application',()=>{
 for(const r of [...db.roles,...db.resources])assert.ok(db.apps.some(a=>a.id===r.appId&&a.domain===r.domain));
 for(const r of db.roles)for(const id of r.resourceIds)assert.ok(db.resources.some(v=>v.id===id&&v.appId===r.appId));
});
test('V2: all grant references are valid and platform identity is never a grant subject',()=>{
 for(const g of db.grants){assert.ok(db.apps.some(a=>a.id===g.appId&&a.domain===g.domain));
  if(g.source==='user')assert.ok(db.users.some(u=>u.id===g.subjectId&&u.domain===g.domain&&u.kind!=='admin'));
  if(g.source==='org')assert.ok(db.orgs.some(o=>o.id===g.subjectId&&o.domain===g.domain));
  if(g.source==='group')assert.ok(db.groups.some(x=>x.id===g.subjectId&&x.domain===g.domain));
  for(const id of g.roleIds)assert.ok(db.roles.some(r=>r.id===id&&r.appId===g.appId));
 }
 assert.deepEqual(effectiveAccess(db,'u-admin',now),[]);
});
test('V2: expired and upcoming grants are both represented',()=>{
 assert.ok(db.grants.some(g=>g.expiresAt&&new Date(g.expiresAt)<now));
 assert.ok(db.grants.some(g=>grantIsCurrent(g,now)&&g.expiresAt&&new Date(g.expiresAt).getTime()-now.getTime()<30*86400000));
});
test('V2: logging contains 1800 ordered records with linked application identities',()=>{
 assert.equal(db.logs.length,1800);
 db.logs.forEach((l,i)=>{assert.ok(db.apps.some(a=>a.id===l.appId&&a.domain===l.domain));assert.ok(db.users.some(u=>u.account===l.actor&&u.domain===l.domain));assert.ok(new Date(l.createdAt)<=now);if(i)assert.ok(db.logs[i-1].createdAt>=l.createdAt);});
});
test('V2: a failed operation does not pretend that data changed',()=>{
 const failed=db.logs.filter(l=>l.type==='operation'&&l.result==='失败');assert.ok(failed.length>0);for(const l of failed){assert.equal(l.before,l.after);assert.equal(l.httpStatus,403);}
});
test('V2: sample events cover both identity domains and all three log categories',()=>{
 for(const domain of ['workforce','public'])for(const type of ['login','operation','api'])assert.ok(db.logs.some(l=>l.domain===domain&&l.type===type));
});
test('V2: legal entity contacts and subaccounts reference public identities',()=>{
 for(const l of db.legalEntities){assert.ok(db.users.some(u=>u.id===l.contactId&&u.domain==='public'));for(const id of l.subAccountIds)assert.ok(db.users.some(u=>u.id===id&&u.domain==='public'));}
});
test('V2: initial task aggregate counts equal the number of task items',()=>{
 for(const task of db.tasks){const c=taskCounts(task);assert.equal(c.success+c.failed+c.pending,c.total);assert.equal(c.total,task.items.length);}
});
test('V2: mock storage payload stays below a conservative 4 MiB budget',()=>assert.ok(Buffer.byteLength(JSON.stringify(db),'utf8')<4*1024*1024));
