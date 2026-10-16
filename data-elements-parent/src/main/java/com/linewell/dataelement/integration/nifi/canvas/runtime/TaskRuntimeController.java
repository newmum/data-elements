package com.linewell.dataelement.integration.nifi.canvas.runtime;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

@RestController
@RequestMapping("/nifi/api/task-runtime")
public class TaskRuntimeController {
    private final TaskRuntimeStatusService service;
    public TaskRuntimeController(TaskRuntimeStatusService service) { this.service = service; }
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-transform"); response.setHeader("X-Accel-Buffering", "no");
        return service.subscribe();
    }

    @ExceptionHandler({java.io.IOException.class, AsyncRequestNotUsableException.class, AsyncRequestTimeoutException.class})
    public void streamClosed(HttpServletResponse response) {
        // A closed stream cannot carry the ordinary JSON error envelope.
        // The client reconnects through normal authentication.
    }
}
