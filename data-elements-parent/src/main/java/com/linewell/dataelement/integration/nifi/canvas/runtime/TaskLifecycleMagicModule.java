package com.linewell.dataelement.integration.nifi.canvas.runtime;

import com.linewell.dataelement.integration.nifi.canvas.controller.PipelineRunController;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;

/**
 * 向 Magic 暴露现有流程启停能力，保留原有版本检查、串行变更及运行状态同步。
 * 输入为调用方已经授权的单个流程 ID；输出包含原始 HTTP 状态及处理结果。
 * 本模块不查询业务表、不判断申请或部门权限，也不保存、部署或强制重建流程。
 * Spring 单例无可变请求状态；资源及异常由现有生命周期实现管理，不持有额外连接。
 */
@Component
@MagicModule("taskLifecycle")
public class TaskLifecycleMagicModule {
    private final PipelineRunController controller;

    public TaskLifecycleMagicModule(PipelineRunController controller) {
        this.controller = controller;
    }

    /** 单项启动；调用方须先完成租户、来源表和任务授权，不允许隐式部署或清空数据。 */
    public Map<String, Object> start(String pipelineId) {
        return result(controller.start(pipelineId, false, false));
    }

    /** 单项停止；保留已部署流程及已有数据，重复请求仍由既有实现协调。 */
    public Map<String, Object> stop(String pipelineId) {
        return result(controller.stop(pipelineId));
    }

    private Map<String, Object> result(ResponseEntity<?> response) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", response.getStatusCode().value());
        result.put("accepted", response.getStatusCode().is2xxSuccessful());
        result.put("body", response.getBody());
        return result;
    }
}
