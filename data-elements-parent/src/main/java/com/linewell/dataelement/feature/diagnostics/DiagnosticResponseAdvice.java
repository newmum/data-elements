package com.linewell.dataelement.feature.diagnostics;

import com.linewell.dataelement.model.common.CommonResponse;
import com.linewell.dataelement.shared.error.ApiErrorResolver;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** Adds an optional reference; success envelopes and authentication failures remain untouched. */
@ControllerAdvice
public class DiagnosticResponseAdvice implements ResponseBodyAdvice<Object> {
    private final DiagnosticEvents events;
    public DiagnosticResponseAdvice(DiagnosticEvents events) { this.events = events; }
    @Override public boolean supports(MethodParameter method, Class<? extends HttpMessageConverter<?>> converter) { return true; }
    @Override public Object beforeBodyWrite(Object body, MethodParameter method, MediaType type,
            Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof CommonResponse<?> result && Boolean.FALSE.equals(result.getSuccess())
                && result.getCode() != null && result.getCode() != 401 && result.getCode() != 403 && result.getCode() != 100120
                && !request.getURI().getPath().startsWith("/sym/ai/diagnostics")) {
            var error = result.getError();
            if (error == null) error = ApiErrorResolver.business("API-BUSINESS-" + result.getCode(), result.getMsg(), result.getMsg()).toMap("", "");
            error.put("method", request.getMethod().name()); error.put("path", request.getURI().getPath());
            events.attach(error, request.getMethod().name(), request.getURI().getPath());
            result.setError(error);
        }
        return body;
    }
}
