package com.haitong.magic;

import com.haitong.api.AuthenticationFilter;
import com.haitong.connector.FtpConnector;
import com.haitong.core.RelayException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.springframework.stereotype.Component;

/**
 * Privileged FTP diagnostic operations exposed through a small Magic-API
 * script. Only an administrator key may invoke it because it can create a
 * directory and upload a generated probe file under the configured output
 * root. It never accepts an endpoint or credentials from the caller.
 */
@Component("ftpDiagnostics")
@MagicModule("ftpDiagnostics")
public final class FtpDiagnosticsApi {
    private final FtpConnector connector;

    public FtpDiagnosticsApi(FtpConnector connector) { this.connector=connector; }

    public Map<String,Object> probe(Map<String,Object> input,HttpServletRequest request) {
        if(!AuthenticationFilter.identity(request).admin())
            throw new RelayException(403,"ADMIN_REQUIRED","FTP 诊断接口仅允许管理员调用");
        Map<String,Object> body=input==null?Map.of():input;
        return connector.diagnose(text(body.get("directory")),flag(body,"createDirectories",false),flag(body,"uploadTest",false),flag(body,"cleanupTestFile",true));
    }

    private static String text(Object value) {
        String valueText=value==null?"":String.valueOf(value).trim();
        if(valueText.length()>500) throw RelayException.invalid("directory 长度不能超过 500");
        return valueText;
    }
    private static boolean flag(Map<String,Object> body,String name,boolean defaultValue) {
        Object value=body.get(name);
        if(value==null) return defaultValue;
        if(value instanceof Boolean valueBoolean) return valueBoolean;
        if(value instanceof String valueText && (valueText.equalsIgnoreCase("true") || valueText.equalsIgnoreCase("false"))) return Boolean.parseBoolean(valueText);
        throw RelayException.invalid(name+" 必须为 true 或 false");
    }
}
