package com.linewell.dataelement.integration.nifi.canvas.runtime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.linewell.dataelement.integration.nifi.canvas.controller.PipelineRunController;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class TaskLifecycleMagicModuleTest {
    @Test void preservesControllerStatusAndNeverImplicitlyDeploysOrCleans() {
        var controller=mock(PipelineRunController.class);
        var module=new TaskLifecycleMagicModule(controller);
        doReturn(ResponseEntity.status(409).body(Map.of("message","请先保存部署"))).when(controller).start("p",false,false);
        var failed=module.start("p");
        assertThat(failed).containsEntry("status",409).containsEntry("accepted",false);
        assertThat(failed.get("body")).isEqualTo(Map.of("message","请先保存部署"));
        verify(controller).start("p",false,false);
        doReturn(ResponseEntity.accepted().body(Map.of("status","STOPPING"))).when(controller).stop("p");
        assertThat(module.stop("p")).containsEntry("status",202).containsEntry("accepted",true);
        verify(controller).stop("p");verifyNoMoreInteractions(controller);
    }
    @Test void doesNotMaskLifecycleErrors() {
        var controller=mock(PipelineRunController.class);
        when(controller.start("p",false,false)).thenThrow(new IllegalStateException("unavailable"));
        assertThatThrownBy(() -> new TaskLifecycleMagicModule(controller).start("p")).isInstanceOf(IllegalStateException.class).hasMessage("unavailable");
    }
}
