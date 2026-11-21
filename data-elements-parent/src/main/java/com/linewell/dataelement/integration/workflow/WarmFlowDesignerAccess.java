package com.linewell.dataelement.integration.workflow;

import org.dromara.warm.flow.ui.controller.WarmFlowController;
import org.dromara.warm.flow.ui.controller.WarmFlowUiController;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

/** Recognizes the native designer registrations when the deployment opts into their protocol. */
@Component
public class WarmFlowDesignerAccess {
    private final Environment environment;

    public WarmFlowDesignerAccess(Environment environment) {
        this.environment = environment;
    }

    public boolean matches(Object handler) {
        if (!environment.getProperty("warm-flow.designer-protocol-access", Boolean.class, false)) {
            return false;
        }
        if (handler instanceof HandlerMethod method) {
            Class<?> type = method.getBeanType();
            return type == WarmFlowController.class || type == WarmFlowUiController.class;
        }
        if (handler instanceof ResourceHttpRequestHandler resources) {
            var locations = resources.getLocations();
            return !locations.isEmpty() && locations.stream().allMatch(location ->
                    location instanceof ClassPathResource resource
                            && (resource.getPath().equals("META-INF/resources/warm-flow-ui/")
                                || resource.getPath().equals("warm-flow-ui/")));
        }
        return false;
    }
}
