package com.linewell.dataelement.platform.magic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stand-in for a remote transfer, keeping Magic tests deterministic and credential free. */
public class FtpSourceProbeStub {
    public int calls;
    public int locatorCalls;
    public RuntimeException locatorFailure;
    public List<Map<String, Object>> columns = List.of(Map.of("columnName", "person_id", "dataType", "varchar"));
    public Map<String, Object> resolveFtpFileSource(Map<String, Object> managed, String table,
                                                  Map<String, Object> options) {
        calls++;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileName", "person-1.json");
        result.put("remotePath", "/managed/data");
        result.put("fileFilterRegex", "^\\Qperson-1.json\\E$");
        result.put("format", "json");
        result.put("charset", "UTF-8");
        result.put("delimiter", ",");
        result.put("excelSheetName", "");
        result.put("jsonRecordPath", "$.data.people");
        result.put("columns", columns);
        return result;
    }

    public Map<String, Object> resolveFtpFileLocator(Map<String, Object> managed, String table,
                                                    Object governance, String tableComment) {
        locatorCalls++;
        if (locatorFailure != null) throw locatorFailure;
        var result = new LinkedHashMap<String, Object>();
        result.put("fileName", "person-1.json"); result.put("remotePath", "/managed/data");
        result.put("fileFilterRegex", "^\\Qperson-1.json\\E$"); result.put("format", "json");
        result.put("charset", "UTF-8"); result.put("delimiter", ",");
        result.put("excelSheetName", ""); result.put("jsonRecordPath", "$.data.people");
        return result;
    }

    public Map<String, Object> withFtpFileSnapshot(Object previous, Map<String, Object> resolved) {
        return com.linewell.dataelement.metautil.structured.StructuredSourceProbeService.withFtpFileSnapshot(previous, resolved);
    }
}
