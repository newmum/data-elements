package com.linewell.dataelement.integration.flowserve;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/** Shared paths for the node's MVC mappings and the remote node client. */
@Configuration(proxyBeanMethods = false)
@PropertySource("classpath:flowserve-runtime.properties")
public class FlowServeRuntimePaths {
    private final String health;
    private final String deploy;

    public FlowServeRuntimePaths(@Value("${flowserve.runtime.protocol-base-path}") String base,
                                 @Value("${flowserve.runtime.protocol-health-path}") String health,
                                 @Value("${flowserve.runtime.protocol-deploy-path}") String deploy) {
        this.health = base + health;
        this.deploy = base + deploy;
    }

    public String health() {
        return health;
    }

    public String deploy() {
        return deploy;
    }
}
