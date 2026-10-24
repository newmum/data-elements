package com.haitong.config;

import com.haitong.core.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounded replay of JSON preserves exact idempotency bytes when Magic's JSON converter consumes the body. */
@Component
@Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE+11)
public final class JsonBodyFilter extends OncePerRequestFilter {
    public static final String RAW=JsonBodyFilter.class.getName()+".raw";
    private final RelayProperties properties;private final Json json;
    public JsonBodyFilter(RelayProperties properties,Json json){this.properties=properties;this.json=json;}
    @Override protected boolean shouldNotFilter(HttpServletRequest r){String p=r.getRequestURI();return !(p.startsWith("/api/v1/")||p.startsWith("/internal/")||p.startsWith("/push-operations/"));}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws IOException,ServletException {
        String type=request.getContentType();boolean jsonType=false;
        try{jsonType=type!=null&&MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(type));}catch(IllegalArgumentException ignored){}
        if(!jsonType){chain.doFilter(request,response);return;}
        byte[] bytes;
        try {
            String encoding=request.getHeader("Content-Encoding");
            if(encoding!=null&&!encoding.equalsIgnoreCase("identity"))throw new RelayException(415,"ENCODING_NOT_ALLOWED","本接口不接收压缩请求体");
            if(request.getContentLengthLong()>properties.getLimits().getMaxMessageBytes())throw new RelayException(413,"PAYLOAD_TOO_LARGE","请求体超过限制");
            bytes=PayloadFiles.readLimited(request.getInputStream(),properties.getLimits().getMaxMessageBytes());
        }catch(RelayException e){response.setStatus(e.status());response.setContentType("application/json;charset=UTF-8");response.getWriter().write(json.write(Map.of("code",e.code(),"message",e.getMessage())));return;}
        request.setAttribute(RAW,bytes);
        chain.doFilter(new HttpServletRequestWrapper(request){
            @Override public ServletInputStream getInputStream(){var input=new ByteArrayInputStream(bytes);return new ServletInputStream(){public int read(){return input.read();}public int read(byte[] b,int off,int len){return input.read(b,off,len);}public boolean isFinished(){return input.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener listener){throw new UnsupportedOperationException("Synchronous bounded JSON");}};}
            @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),java.nio.charset.StandardCharsets.UTF_8));}
        },response);
    }
}
