package com.haitong.api;
import com.haitong.store.*;
import com.haitong.core.RelayException;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;

/** Administrator-only local transport reconciliation. No claim of downstream business processing. */
@RestController
@RequestMapping("/internal/v1")
public class ReconciliationController {
    private final ExchangeLog ledger;private final JdbcTemplate jdbc;
    public ReconciliationController(ExchangeLog ledger,JdbcTemplate jdbc){this.ledger=ledger;this.jdbc=jdbc;}
    @GetMapping("/exchange-logs")
    public List<Map<String,Object>> logs(@RequestParam long from,@RequestParam long to,
        @RequestParam(defaultValue="")String protocol,@RequestParam(defaultValue="")String messageId,
        @RequestParam(defaultValue="50")int limit,@RequestParam(defaultValue="0")int offset){
        window(from,to);if(!protocol.isBlank()&&!Set.of("HTTP","FTP","KAFKA","LOCAL","LOG").contains(protocol))throw RelayException.invalid("protocol 不合法");
        if(!messageId.isBlank())try{UUID.fromString(messageId);}catch(IllegalArgumentException e){throw RelayException.invalid("messageId 不合法");}
        if(limit<1||limit>200||offset<0||offset>1000000)throw RelayException.invalid("分页超限");
        return ledger.query(from,to,protocol,messageId,limit,offset);
    }
    @GetMapping("/reconciliation/summary")
    public Map<String,Object> summary(@RequestParam long from,@RequestParam long to){
        window(from,to);Map<String,Object> response=new LinkedHashMap<>();response.put("scope","LOCAL_TRANSPORT_LEDGER");
        response.put("businessReceiptCompared",false);response.put("provisional",true);response.put("windowFromInclusive",from);response.put("windowToExclusive",to);
        response.put("windowBasis","ht_message.created_at UTC epoch milliseconds");response.put("generatedAt",ledger.now());
        response.put("inputs",jdbc.query("""
          SELECT route_code,source_type,status,COUNT(*) n FROM ht_message WHERE created_at>=? AND created_at<?
          GROUP BY route_code,source_type,status ORDER BY route_code,source_type,status
          """,(r,i)->Map.of("route",r.getString("route_code"),"sourceType",r.getString("source_type"),"acceptance",r.getString("status"),"messageCount",r.getLong("n")),from,to));
        response.put("targets",jdbc.query("""
          SELECT m.route_code,d.target_code,d.target_type,COUNT(*) expected,
          SUM(CASE WHEN d.status='SUCCEEDED' THEN 1 ELSE 0 END) confirmed,
          SUM(CASE WHEN d.status='FAILED' THEN 1 ELSE 0 END) failed,
          SUM(CASE WHEN d.status='UNKNOWN' THEN 1 ELSE 0 END) uncertain,
          SUM(CASE WHEN d.status IN ('PENDING','RUNNING','RETRY_WAIT') THEN 1 ELSE 0 END) pending
          FROM ht_message m JOIN ht_delivery d ON d.message_id=m.id WHERE m.created_at>=? AND m.created_at<?
          GROUP BY m.route_code,d.target_code,d.target_type ORDER BY m.route_code,d.target_code
          """,(r,i)->Map.of("route",r.getString("route_code"),"target",r.getString("target_code"),"type",r.getString("target_type"),
          "expectedDeliveries",r.getLong("expected"),"transportConfirmed",r.getLong("confirmed"),"failed",r.getLong("failed"),
          "unknown",r.getLong("uncertain"),"pending",r.getLong("pending")),from,to));
        response.put("files",jdbc.query("""
          SELECT route_code,content_format,status,COUNT(*) n FROM ht_file_checkpoint WHERE created_at>=? AND created_at<?
          GROUP BY route_code,content_format,status ORDER BY route_code,content_format,status
          """,(r,i)->Map.of("route",r.getString("route_code"),"format",r.getString("content_format"),"ingestionStatus",r.getString("status"),"fileCount",r.getLong("n")),from,to));
        return response;
    }
    private static void window(long from,long to){if(from<0||to<=from||to-from>31L*86400000)throw RelayException.invalid("时间窗口采用UTC毫秒，左闭右开，最长31天");}
}
