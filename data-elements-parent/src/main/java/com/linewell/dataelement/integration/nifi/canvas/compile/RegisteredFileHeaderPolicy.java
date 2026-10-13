package com.linewell.dataelement.integration.nifi.canvas.compile;

import java.util.Map;

/** File headers are field identities; display labels require an explicit report contract. */
public final class RegisteredFileHeaderPolicy {
    private RegisteredFileHeaderPolicy() {}

    public static boolean usesRegisteredChineseHeaders(Map<String, Object> sourceConfig) {
        if (sourceConfig == null) return false;
        String protocol = String.valueOf(sourceConfig.getOrDefault("protocol", ""));
        if (!"ftp".equalsIgnoreCase(protocol) && !"sftp".equalsIgnoreCase(protocol)) return false;
        return "registered-chinese".equalsIgnoreCase(
                String.valueOf(sourceConfig.getOrDefault("fileHeaderMode", "")));
    }
}
