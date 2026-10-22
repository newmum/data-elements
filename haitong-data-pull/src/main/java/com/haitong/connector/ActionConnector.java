package com.haitong.connector;

import com.haitong.config.*;
import com.haitong.core.*;
import com.haitong.core.Models.*;
import org.springframework.stereotype.Component;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

@Component
public class ActionConnector {
    private final RelayProperties p;private final Secrets secrets;private final Json json;
    public ActionConnector(RelayProperties p,Secrets secrets,Json json) {this.p=p;this.secrets=secrets;this.json=json;}
    public Map<String,Object> execute(ActionPlan action,byte[] body,String stableKey,String owner) throws Exception {
        Map<String,Object> input=json.object(body);json.require(input,action.requiredFields());
        if(action.handler().equals("inspect")) return Map.of("handler","inspect","validJsonObject",true,"topLevelFields",new TreeSet<>(input.keySet()),"bodyBytes",body.length,"sha256",Rules.sha256(body));
        if(!action.handler().equals("http")) throw new DeliveryFailure("ACTION_HANDLER_NOT_AVAILABLE",true,false);
        // URL and method come only from the frozen server-side action registration. Redirects are never followed.
        Map<String,String> headers=new LinkedHashMap<>();headers.put("Content-Type","application/json");
        headers.put("Idempotency-Key",stableKey);headers.put("X-Relay-Client",owner);
        String token=secrets.resolve(p.getBackend().getBearerToken());if(!token.isBlank()) headers.put("Authorization","Bearer "+token);
        HttpCalls.Result response;
        try {response=HttpCalls.call(URI.create(action.url()),action.method(),body,headers,action.timeoutSeconds(),65536);}
        catch(Exception e) {throw new DeliveryFailure("BACKEND_IO_UNCERTAIN",false,!action.safeRetry(),e);}
        int code=response.status();
        if(code<200 || code>=300) {
            boolean transientError=code==408 || code==429 || code>=500;
            throw new DeliveryFailure("BACKEND_HTTP_"+code,!transientError,transientError && !action.safeRetry());
        }
        Object data=response.body().length==0?Map.of():safeResponse(response.body());
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("httpStatus",code);result.put("completion",code==202?"BACKEND_ACCEPTED":"BACKEND_RESPONDED");result.put("body",data);return result;
    }

    private Object safeResponse(byte[] bytes) {
        try {return json.read(PayloadFiles.utf8(bytes),Object.class);} catch(RuntimeException e) {return Map.of("contentType","non-json","bodyBytes",bytes.length);}
    }
}
