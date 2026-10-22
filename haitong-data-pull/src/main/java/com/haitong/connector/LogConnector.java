package com.haitong.connector;

import com.haitong.core.Json;
import com.haitong.core.Models.Packet;
import com.haitong.core.Models.TargetPlan;
import com.haitong.core.Rules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.*;

/** A safe operational log target: it records a batch summary, never the business payload itself. */
@Component
public final class LogConnector {
    private static final Logger log = LoggerFactory.getLogger(LogConnector.class);
    private final Json json;

    public LogConnector(Json json) { this.json = json; }

    public Map<String,Object> deliver(TargetPlan target, Packet packet) {
        Map<String,Object> body = json.object(packet.bytes());
        String tableName = Objects.toString(body.get("tableName"), "unknown");
        String batchId = Objects.toString(body.get("batchId"), "");
        long recordCount = body.get("records") instanceof Collection<?> rows ? rows.size() : 0L;
        String digest = Rules.sha256(packet.bytes());
        log.info("TABLE_DATA_RECEIVED route={}, tableName={}, batchId={}, recordCount={}, messageId={}, sha256={}",
            packet.routeCode(), tableName, batchId, recordCount, packet.messageId(), digest);
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("completion", "LOGGED");
        result.put("tableName", tableName);
        result.put("batchId", batchId);
        result.put("recordCount", recordCount);
        result.put("sha256", digest);
        return result;
    }
}
