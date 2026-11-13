package com.linewell.dataelement.platform.audit;

import java.io.IOException;
import java.lang.reflect.Type;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

/** Observes conversion only; never wraps, caches or reads the request input stream. */
@ControllerAdvice
public class AuditRequestBodyAdvice extends RequestBodyAdviceAdapter {
    @Override
    public boolean supports(MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage input, MethodParameter parameter, Type targetType,
                                           Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        AuditRequestParameters.bodyStarted(AuditIdentityResolver.request(), AuditRequestParameters.partName(parameter));
        return input;
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage input, MethodParameter parameter, Type targetType,
                                Class<? extends HttpMessageConverter<?>> converterType) {
        var request = AuditIdentityResolver.request();
        if (!AuditRequestParameters.eligible(request)) return body;
        try {
            Object captured = body;
            if (input != null && input.getHeaders().getContentDisposition().getFilename() != null) {
                // @RequestPart may convert a file into String/DTO. It is still a file, never a loggable body.
                var metadata = new java.util.LinkedHashMap<String, Object>();
                metadata.put("fileName", input.getHeaders().getContentDisposition().getFilename().replace('\\', '/').replaceFirst("^.*/", ""));
                metadata.put("size", Math.max(0, input.getHeaders().getContentLength()));
                if (input.getHeaders().getContentType() != null) metadata.put("contentType", input.getHeaders().getContentType().toString());
                captured = metadata;
            } else if (body instanceof String text && input != null && input.getHeaders().getContentType() != null
                    && (input.getHeaders().getContentType().getSubtype().equalsIgnoreCase("json")
                    || input.getHeaders().getContentType().getSubtype().toLowerCase(java.util.Locale.ROOT).endsWith("+json"))) {
                captured = AuditRequestParameters.decodedBody(request, text);
            }
            String part = AuditRequestParameters.partName(parameter);
            if (part == null) AuditRequestParameters.convertedBody(request, captured);
            else AuditRequestParameters.convertedPart(request, part, captured);
        } catch (RuntimeException error) {
            AuditRequestParameters.captureFailed(request);
        }
        return body;
    }

    @Override
    public Object handleEmptyBody(Object body, HttpInputMessage input, MethodParameter parameter, Type targetType,
                                  Class<? extends HttpMessageConverter<?>> converterType) {
        String part = AuditRequestParameters.partName(parameter);
        if (part == null) AuditRequestParameters.convertedBody(AuditIdentityResolver.request(), body);
        else AuditRequestParameters.convertedPart(AuditIdentityResolver.request(), part, body);
        return body;
    }
}