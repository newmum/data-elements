{"id":"haitong-function-credentials","groupId":"haitong-business-functions","name":"05.业务密钥签发","path":"/credentials","parameters":[{"name":"input","dataType":"Object"}],"description":"平台服务或管理员按数据源签发/显式轮换业务密钥；只保存SHA-256，明文仅首次签发或轮换时返回，重复获取不更换密钥。"}
================================
import java.nio.charset.StandardCharsets
// 业务：密钥生命周期和ht_push_api_client写入在脚本中；Java只提供安全随机数和摘要。
import '@/haitong/common' as common
import org.springframework.dao.DuplicateKeyException
var c=common();var _fail=c.fail;var _text=c.text;var _now=c.now;var _hash=c.hash;
if(!input.caller.admin()){_fail(403,'FORBIDDEN','仅平台服务或管理员可签发密钥')}
var id=_text(input.body.clientId).trim()
if(!id.matches('push-[A-Za-z0-9_-]{1,123}')){_fail(400,'INVALID_REQUEST','clientId必须为数据源推送调用方编号')}
var rotate=input.body.rotate==true
try{
    return c.tx.execute(status=>{
        var old=c.jdbc.queryForList('SELECT client_id FROM ht_push_api_client WHERE client_id=? AND is_enable=1 AND is_del=0 FOR UPDATE',[id].toArray())
        if(old.size()>0 && !rotate){return {clientId:id,revealed:false}}
        var key=c.http.randomKey();var hash=_hash(key.getBytes(StandardCharsets.UTF_8));var now=_now()
        if(old.size()==0){c.jdbc.update('INSERT INTO ht_push_api_client(client_id,api_key_sha256,is_enable,is_del,created_at,updated_at) VALUES (?,?,1,0,?,?)',[id,hash,now,now].toArray())}
        else{c.jdbc.update('UPDATE ht_push_api_client SET api_key_sha256=?,updated_at=? WHERE client_id=? AND is_enable=1 AND is_del=0',[hash,now,id].toArray())}
        return {clientId:id,revealed:true,apiKey:key}
    })
}catch(e){var error=e;while(error!=null){if(error instanceof DuplicateKeyException){return {clientId:id,revealed:false}}error=error.getCause()}throw e}
