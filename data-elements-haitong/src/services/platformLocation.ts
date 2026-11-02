/** Pure URL rules shared by report return, platform home/login and the iframe bridge. */
const credentialKey=/^(?:token|access[_-]?token|refresh[_-]?token|id[_-]?token|authorization|password|secret|credential|ticket|authCode|code)$/i;

export function withoutNavigationCredentials(params:URLSearchParams):URLSearchParams{
  for(const key of [...params.keys()])if(credentialKey.test(key))params.delete(key);
  return params;
}

/** index.html and a directory entry belong to the same deployed hash-router app. */
export function appDocumentPath(pathname:string):string{
  return pathname.replace(/\/index\.html$/i,'/').replace(/\/+$/,'')||'/';
}

export function sourcePlatformBaseUrl(currentHref:string,configured?:string,development=false):URL{
  const fallback=development?'http://localhost:3000/#/':'/#/';
  const portal=new URL(configured?.trim()||fallback,currentHref);
  if(!['http:','https:'].includes(portal.protocol)||portal.username||portal.password)throw new Error('源数据管理平台地址必须是有效的 HTTP 或 HTTPS 应用地址');
  return portal;
}

export function sourcePlatformHomeUrl(portalHref:string):string{
  const portal=new URL(portalHref);portal.search='';portal.hash='/';return portal.href;
}

export function sourcePlatformLoginUrl(currentHref:string,portalHref:string):string{
  const portal=new URL(portalHref),destination=new URL(currentHref);
  destination.search=withoutNavigationCredentials(destination.searchParams).toString();
  const hash=destination.hash.slice(1),queryStart=hash.indexOf('?');
  if(queryStart>=0){
    const query=withoutNavigationCredentials(new URLSearchParams(hash.slice(queryStart+1))).toString();
    destination.hash=hash.slice(0,queryStart)+(query?'?'+query:'');
  }
  portal.search='';portal.hash='/login?'+new URLSearchParams({redirect:destination.href});return portal.href;
}
