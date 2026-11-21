package com.linewell.dataelement.platform.web.security;


import cn.hutool.core.text.AntPathMatcher;
import java.util.List;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "data-trading-parent")
@RefreshScope
public class WhiteListProp {
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private List<String> whiteList;

    @Value("${flowserve.runtime.protocol-base-path}/**")
    private String flowServeFallbackPattern;

    public boolean isWhiteListed(String requestUri) {
        if (requestUri == null) {
            return false;
        }
        if (requestUri.startsWith("/portal/")) {
            return false;
        }
        return whiteList != null
                && whiteList.stream().anyMatch(pattern -> !pattern.equals(flowServeFallbackPattern)
                        && PATH_MATCHER.match(pattern, requestUri));
    }
}
