package com.linewell.dataelement.mapping.core;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Physical DATE means a timestamp only in dialects whose DATE stores time. */
public final class JdbcTemporalTypes {
    private JdbcTemporalTypes() {}
    private static String text(Map<String,?> config,String... names) {
        if(config==null)return "";
        for(String name:names){Object value=config.get(name);if(value!=null&&!value.toString().isBlank())return value.toString().trim();}
        return "";
    }
    public static boolean oracleDateHasTime(Map<String,?> config) {
        String type=text(config,"dbType","databaseType").toUpperCase(Locale.ROOT).replace(' ','_');
        String url=text(config,"jdbcURL","jdbcUrl","url").toLowerCase(Locale.ROOT);
        String mode=text(config,"compatibleMode","compatibilityMode","compatibility","mode").toUpperCase(Locale.ROOT);
        return type.equals("ORACLE")||type.startsWith("ORACLE_12")||type.equals("OCEANBASE_ORACLE")||type.equals("OCEANBASEORACLE")
                ||url.startsWith("jdbc:oracle:")
                ||url.startsWith("jdbc:oceanbase:oracle:")
                ||((type.equals("OCEANBASE")||url.startsWith("jdbc:oceanbase:"))&&(mode.equals("ORACLE")||url.matches(".*[?;&](?:compatiblemode|compatibilitymode)=oracle(?:[;&].*)?$")));
    }
    public static String logicalType(String physicalType,Map<String,?> config) {
        String type=ValueTypes.type(physicalType);
        String database=text(config,"dbType","databaseType").toUpperCase(Locale.ROOT).replace(' ','_');
        String url=text(config,"jdbcURL","jdbcUrl","url").toLowerCase(Locale.ROOT);
        String raw=physicalType==null?"":physicalType.toUpperCase(Locale.ROOT).replaceAll("\\([^)]*\\)","").trim();
        if((raw.equals("TIMESTAMP")||raw.equals("ROWVERSION"))
                &&(database.equals("SQLSERVER")||database.equals("SQL_SERVER")||database.equals("MSSQL")||database.startsWith("MS_SQL")||url.startsWith("jdbc:sqlserver:")))return "ANY";
        return type.equals("DATE")&&oracleDateHasTime(config)?"DATETIME":type;
    }

    /** Carry the JDBC/JSON millisecond transport contract only into temporal rules. */
    public static void annotateMillisInput(JsonNode rule) {
        if (!(rule instanceof ObjectNode object)) return;
        String target = ValueTypes.type(rule.path("target").path("logicalType").asText(rule.path("targetType").asText("ANY")));
        boolean temporal = Set.of("DATE", "DATETIME", "TIME", "INSTANT").contains(target);
        for (JsonNode step : rule.path("steps")) {
            String operator = step.path("operator").asText();
            String function = step.path("args").path("function").asText();
            temporal |= Set.of("DATE_PARSE", "DATE_FORMAT").contains(operator) || Set.of("parseDate", "formatDate").contains(function);
        }
        temporal |= Set.of("parseDate", "formatDate").contains(rule.path("transform").path("fn").asText());
        if (!temporal) return;
        if (rule.path("input").path("kind").asText().equals("FIELD") && rule.path("input") instanceof ObjectNode input) {
            if (input.path("numericTemporalUnit").asText().isBlank()) input.put("numericTemporalUnit", "EPOCH_MILLIS");
        } else if (rule.hasNonNull("from") && rule.path("sourceTemporalUnit").asText().isBlank()) {
            object.put("sourceTemporalUnit", "EPOCH_MILLIS");
        }
    }
}
