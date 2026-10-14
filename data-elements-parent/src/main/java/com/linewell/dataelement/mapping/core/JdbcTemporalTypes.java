package com.linewell.dataelement.mapping.core;

import java.util.Locale;
import java.util.Map;

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
        return type.equals("DATE")&&oracleDateHasTime(config)?"DATETIME":type;
    }
}
