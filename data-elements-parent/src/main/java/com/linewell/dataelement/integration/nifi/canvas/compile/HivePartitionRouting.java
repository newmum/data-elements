package com.linewell.dataelement.integration.nifi.canvas.compile;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** The partition stage runs after the raw-Hive branch projection, before CSV serialization. */
public final class HivePartitionRouting {
    private HivePartitionRouting() {}
    public static String script(Map<String,Object> plan,List<String> columns) {
        String encoded;
        try { encoded=Base64.getEncoder().encodeToString(new ObjectMapper().writeValueAsBytes(Map.of("plan",plan,"columns",columns))); }
        catch(Exception failure){throw new IllegalArgumentException("Hive分区写入配置无法序列化",failure);}
        return """
            import groovy.json.JsonSlurper
            import groovy.json.JsonOutput
            import org.apache.nifi.processor.io.InputStreamCallback
            import org.apache.nifi.processor.io.OutputStreamCallback
            import java.time.*
            import java.time.format.*
            import java.nio.charset.StandardCharsets
            def cfg = new JsonSlurper().parseText(new String(Base64.decoder.decode('%s'), StandardCharsets.UTF_8))
            def plan = cfg.plan
            def flow = session.get()
            if (flow == null) return
            if (flow.size > 67108864L) throw new IllegalArgumentException('Hive分区批次超过64MiB，请减小来源批次')
            def payload
            session.read(flow, { input -> payload = new JsonSlurper().parse(input, 'UTF-8') } as InputStreamCallback)
            def rows = payload instanceof List ? payload : [payload]
            if (rows.size() > 100000) throw new IllegalArgumentException('Hive分区批次超过100000条，请减小来源批次')
            def zone = ZoneId.of(plan.timezone)
            def temporal = { value ->
                if (value instanceof Number) return LocalDateTime.ofInstant(Instant.ofEpochMilli(value.longValue()), zone)
                def s = value.toString().trim()
                if (s.endsWith('Z') || s ==~ /.*[+-][0-9]{2}:[0-9]{2}$/) return LocalDateTime.ofInstant(OffsetDateTime.parse(s.replace(' ', 'T')).toInstant(), zone)
                if (s.size() == 10) return LocalDate.parse(s).atStartOfDay()
                return LocalDateTime.parse(s.replace(' ', 'T'))
            }
            def canonical = { value -> value == null ? null : plan.kind == 'NUMBER' ? new BigDecimal(value.toString()) : plan.kind == 'TIME' ? temporal(value) : value.toString() }
            def groups = new LinkedHashMap()
            rows.each { row ->
                if (!(row instanceof Map)) throw new IllegalArgumentException('Hive分区输入必须是JSON对象记录')
                def index = row.collectEntries { k,v -> [(k.toString().toLowerCase(Locale.ROOT)):v] }
                def key = (plan.inputField ?: plan.field).toString().toLowerCase(Locale.ROOT)
                if (!index.containsKey(key)) throw new IllegalArgumentException('Hive分区输入缺少字段：'+key)
                def value = canonical(index[key])
                def part = plan.partitions.find { p ->
                    if (plan.strategy == 'LIST') return p.default == true || p.values.any { canonical(it) == value }
                    if (p.upper == 'MAXVALUE') return true
                    if (value == null) return false
                    return value < canonical(p.upper)
                }
                if (part == null) throw new IllegalArgumentException('数据未匹配任何Hive分区，请核对空值、区域值或时间边界')
                def projected = new LinkedHashMap()
                cfg.columns.each { name ->
                    def item = name.toString().equalsIgnoreCase(plan.field.toString()) ? index[key] : index[name.toString().toLowerCase(Locale.ROOT)]
                    if (item != null && name.toString().equalsIgnoreCase(plan.field.toString()) && plan.kind == 'TIME') {
                        def date = temporal(item)
                        item = plan.dateOnly == true ? date.toLocalDate().toString() : date.format(new DateTimeFormatterBuilder().appendPattern('uuuu-MM-dd HH:mm:ss').appendFraction(java.time.temporal.ChronoField.NANO_OF_SECOND, 0, 9, true).toFormatter())
                    }
                    projected[name] = item
                }
                if (!groups.containsKey(part.bucket)) groups[part.bucket] = []
                groups[part.bucket].add(projected)
            }
            // All rows are validated before creating children; an exception rolls the session back.
            groups.each { bucket, records ->
                def child = session.create(flow)
                child = session.write(child, { output -> output.write(JsonOutput.toJson(records).getBytes(StandardCharsets.UTF_8)) } as OutputStreamCallback)
                def directory = plan.locations[bucket]
                if (directory == null || directory.toString().isBlank()) throw new IllegalArgumentException('Hive分区实际目录缺失')
                child = session.putAllAttributes(child, ['hive.partition.bucket':bucket.toString(), 'hive.partition.directory':directory.toString(), 'mime.type':'application/json'])
                session.transfer(child, REL_SUCCESS)
            }
            session.remove(flow)
            """.formatted(encoded);
    }
}
