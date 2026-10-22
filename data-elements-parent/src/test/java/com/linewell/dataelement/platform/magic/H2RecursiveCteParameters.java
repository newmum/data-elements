package com.linewell.dataelement.platform.magic;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import org.ssssssss.magicapi.modules.db.BoundSql;

/**
 * H2测试适配：其递归CTE仅支持主SELECT中的绑定参数，CTE内参数会返回错误空集。
 * 仅在测试拦截器把CTE参数转为严格转义的字面量，主查询仍绑定；生产Magic始终使用参数绑定。
 * 限制来源：https://h2database.github.io/html/advanced.html#recursive_queries
 */
final class H2RecursiveCteParameters {
    private H2RecursiveCteParameters() { }

    static void adapt(BoundSql statement) {
        String sql=statement.getSql();
        int main=sql.indexOf("SELECT ordered_table.*");
        if(main<0 || !sql.substring(0,main).contains("task_ancestry(")) return;
        Object[] parameters=statement.getParameters();
        StringBuilder translated=new StringBuilder();
        boolean quoted=false;
        int parameter=0;
        for(int index=0;index<main;index++) {
            char value=sql.charAt(index);
            if(value=='\'') {
                translated.append(value);
                if(quoted && index+1<main && sql.charAt(index+1)=='\'') {
                    translated.append(sql.charAt(++index));continue;
                }
                quoted=!quoted;
            } else if(value=='?' && !quoted) {
                translated.append(literal(parameters[parameter++]));
            } else translated.append(value);
        }
        statement.setSql(translated.append(sql.substring(main)).toString());
        statement.setParameters(new ArrayList<>(Arrays.asList(parameters).subList(parameter,parameters.length)));
    }

    private static String literal(Object value) {
        if(value==null) return "NULL";
        if(value instanceof Number) return value.toString();
        if(value instanceof Date date) {
            String formatted=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(date.getTime()));
            return "TIMESTAMP '"+formatted+"'";
        }
        if(value instanceof String text) return "'"+text.replace("'","''")+"'";
        throw new IllegalArgumentException("不支持的H2测试CTE参数类型："+value.getClass().getSimpleName());
    }
}
