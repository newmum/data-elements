package com.linewell.dataelement.model;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author zwenbo
 * @Description: TODO
 * @date 2025/11/28
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RouteMenu {
    private String id;
    private String parentId;
    private String path;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String component;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String componentName;
    private String name;
    private Meta meta;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<RouteMenu> children;
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Meta {
        private String title;
        private String icon;
        private boolean keepAlive;
        private String openMode;
    }
}
