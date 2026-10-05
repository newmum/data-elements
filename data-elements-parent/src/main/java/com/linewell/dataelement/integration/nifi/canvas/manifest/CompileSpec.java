package com.linewell.dataelement.integration.nifi.canvas.manifest;

import java.util.List;
import java.util.Map;

public record CompileSpec(
        List<ControllerServiceSpec> controllerServices,
        List<ProcessorSpec> processors,
        List<InternalConnection> internalConnections,
        String inlet,
        List<Outlet> outlets
) {
    public record ControllerServiceSpec(
            String localId,
            String type,
            Map<String, String> properties
    ) {}

    public record ProcessorSpec(
            String localId,
            String type,
            Map<String, String> properties,
            String schedulingPeriod,
            String schedulingStrategy
    ) {}

    public record InternalConnection(String from, String to, String relationship) {}

    public record Outlet(String name, String processorRef, String relationship) {}
}
