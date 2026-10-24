package com.haitong.magic;

import com.haitong.api.ApiErrors;
import com.haitong.core.RelayException;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.core.interceptor.ResultProvider;
import org.ssssssss.magicapi.core.model.JsonBean;

/** Preserve public JSON/status contracts and unwrap script exceptions without exposing implementation details. */
@Component
public final class HaitongMagicResultProvider implements ResultProvider {
    private final ApiErrors errors;
    public HaitongMagicResultProvider(ApiErrors errors){this.errors=errors;}
    @Override public Object buildResult(RequestEntity request,int code,String message,Object value){return new JsonBean<>(code,message,value);}
    @Override public Object buildException(RequestEntity request,Throwable error){
        Throwable current=error;
        for(int n=0;n<20&&current!=null;n++,current=current.getCause()){
            if(current instanceof RelayException e)return errors.relay(e);
            if(current instanceof IllegalArgumentException e)return errors.invalid(e);
            if(current instanceof org.springframework.dao.DataAccessException e)return errors.database(e);
            if(current instanceof java.sql.SQLException e)return errors.database(e);
            if(current instanceof com.haitong.core.Models.DeliveryFailure e)return errors.backend(e);
        }
        return errors.unknown(error instanceof Exception e?e:new RuntimeException(error));
    }
}
