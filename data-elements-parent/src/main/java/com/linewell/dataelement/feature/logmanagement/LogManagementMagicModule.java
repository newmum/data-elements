package com.linewell.dataelement.feature.logmanagement;

import java.util.*;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;

/** Query primitives for the platform. The canonical Magic guard supplies the paired permission/scope. */
@Component
@MagicModule("logManagement")
public class LogManagementMagicModule {
    private final LogAccessService access;
    private final LogQueryService queries;
    private final LogComponentService components;
    private final LogLifecycleService lifecycle;
    private final LogArchiveService archives;
    public LogManagementMagicModule(LogAccessService access,LogQueryService queries,LogComponentService components,LogLifecycleService lifecycle,LogArchiveService archives) { this.access=access;this.queries=queries;this.components=components;this.lifecycle=lifecycle;this.archives=archives; }
    public Object query(Map<String,Object> context,String kind,String operation,Map<String,Object> input) {
        boolean event=Set.of("auth","operations","requests","security").contains(kind);
        boolean valid=switch(operation){
            case "components"->event||Set.of("exports","archives","policies").contains(kind);
            case "page","statistics","detail","items","summary"->event;
            case "lifecycle"->Set.of("exports","archives","policies").contains(kind);
            case "export"->kind.equals("exports");
            case "archive","archive-action"->kind.equals("archives");
            case "policy"->kind.equals("policies");default->false;};
        if(!valid)throw new SecurityException("日志模块与操作不匹配");
        var scope=access.platform(context,kind);
        return switch(operation) {
            case "components" -> components.components();
            case "page" -> queries.page(scope,kind,input);
            case "statistics" -> queries.statistics(scope,kind,input);
            case "summary" -> queries.summary(scope,input);
            case "detail" -> queries.detail(scope,kind,String.valueOf(input.get("id")));
            case "items" -> queries.batchItems(scope,kind,String.valueOf(input.get("id")),input);
            case "lifecycle" -> lifecycle.page(scope,kind,input);
            case "export" -> lifecycle.createExport(scope,input);
            case "archive" -> lifecycle.createArchive(scope,input);
            case "archive-action" -> archives.action(scope,String.valueOf(input.get("id")),input);
            case "policy" -> lifecycle.savePolicy(scope,input);
            default -> throw new IllegalArgumentException("日志操作无效");
        };
    }
}
