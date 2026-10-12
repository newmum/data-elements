package com.linewell.dataelement.integration.nifi.canvas.controller;

import com.linewell.dataelement.platform.web.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class IncrementalFieldErrorAdviceTest {
    @Test
    void chainRejectionsKeepTheirHttpAndBusinessCodeInsteadOfBecoming500() throws Exception {
        var service = mock(com.linewell.dataelement.integration.nifi.canvas.lifecycle.AccessTaskChainService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new AccessTaskChainController(service))
                .setControllerAdvice(new GlobalExceptionHandler(), new IncrementalFieldErrorAdvice()).build();
        for (var code : new HttpStatus[]{HttpStatus.CONFLICT, HttpStatus.FORBIDDEN}) {
            doThrow(new ResponseStatusException(code, "任务关联或访问范围需要核对")).when(service).byTask("task");
            mvc.perform(get("/nifi/api/pipelines/task-chain").param("accessTaskId", "task"))
                    .andExpect(status().is(code.value())).andExpect(jsonPath("$.code").value(code.value()))
                    .andExpect(jsonPath("$.error").value("任务关联或访问范围需要核对"));
        }
    }
    @Test
    void scopedRejectionKeepsHttpStatusAheadOfGlobalCatchAll() throws Exception {
        var controller = mock(PipelineController.class);
        when(controller.update(eq("task"), any(), any())).thenThrow(
                new ResponseStatusException(HttpStatus.CONFLICT, "来源节点缺少主增量字段"));
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(), new IncrementalFieldErrorAdvice()).build();
        mvc.perform(post("/nifi/api/pipelines/task").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\",\"dsl\":{\"version\":1,\"nodes\":[],\"edges\":[]}}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.msg").value("来源节点缺少主增量字段"));
    }
}
