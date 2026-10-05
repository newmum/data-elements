/** Bounded test-only retry for a shared runtime being published. Not an auth bypass. */
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url),{sm3}=require('sm-crypto');
export async function verificationLogin(base,account,password){
 for(let attempt=0;attempt<4;attempt++){
  let result,message='';
  try{
   const response=await fetch(base+'/portal/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({account,password:sm3(password),appId:'1995678661281710081'}),signal:AbortSignal.timeout(12000)});
   const text=await response.text();result=text?JSON.parse(text):{};
   if(result.code===0&&result.data?.token){if(attempt)console.log('NOTE login needed '+attempt+' runtime-publication retries; not evidence of stable authentication');return result;}
   message=String(result.msg||result.message||'Empty runtime response');
   if(!/No static resource portal\/login|Empty runtime response/.test(message))throw Error('Login rejected: '+message.slice(0,160));
  }catch(error){if(!['TimeoutError','SyntaxError','TypeError'].includes(error.name))throw error;message=error.name;}
  if(attempt===3)throw Error('Shared login remains unavailable: '+message.slice(0,160));
  await new Promise(resolve=>setTimeout(resolve,3000));
 }
}
