package com.haitong.api;

import com.haitong.core.*;
import com.haitong.core.Models.DeliveryFailure;
import org.slf4j.*;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.*;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.NoHandlerFoundException;
import java.util.*;

@RestControllerAdvice
public class ApiErrors {
    private static final Logger log=LoggerFactory.getLogger(ApiErrors.class);
    @ExceptionHandler(RelayException.class)
    public ResponseEntity<?> relay(RelayException e) {return error(e.status(),e.code(),e.getMessage());}
    @ExceptionHandler({IllegalArgumentException.class,MissingRequestHeaderException.class,MissingServletRequestParameterException.class,HttpMessageNotReadableException.class})
    public ResponseEntity<?> invalid(Exception e) {return error(400,"INVALID_REQUEST","请求参数、JSON 或必要请求头不正确");}
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> tooLarge(Exception e) {return error(413,"FILE_TOO_LARGE","上传超过服务器文件大小限制");}
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<?> media(Exception e) {return error(415,"UNSUPPORTED_MEDIA_TYPE","请检查 Content-Type");}
    @ExceptionHandler({NoResourceFoundException.class,NoHandlerFoundException.class})
    public ResponseEntity<?> missing(Exception e) {return error(404,"RESOURCE_NOT_FOUND","接口不存在");}
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<?> database(Exception e) {return error(503,"PERSISTENCE_UNAVAILABLE","暂时无法确认受理，请使用原幂等键稍后重试");}
    @ExceptionHandler(DeliveryFailure.class)
    public ResponseEntity<?> backend(DeliveryFailure e) {return error(502,e.code,"后端未返回可确认的成功结果");}
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unknown(Exception e) {
        String id=UUID.randomUUID().toString();log.error("Request failed: errorId={}, errorClass={}",id,e.getClass().getSimpleName());
        return ResponseEntity.status(500).body(Map.of("code","INTERNAL_ERROR","message","处理失败，请凭 errorId 核查","errorId",id));
    }
    private ResponseEntity<?> error(int status,String code,String message) {
        return ResponseEntity.status(status).header("Cache-Control","no-store").body(Map.of("code",code,"message",message));
    }
}
