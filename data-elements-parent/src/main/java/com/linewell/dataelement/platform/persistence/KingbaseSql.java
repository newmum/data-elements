package com.linewell.dataelement.platform.persistence;

import java.util.*;
import java.util.regex.*;

/** SQL adaptation for explicitly selected Kingbase connections. */
public final class KingbaseSql {
    private KingbaseSql() {}
    private static final Pattern REVISION_INCREMENT = Pattern.compile(
        "(?i)\\bCOALESCE\\s*\\(\\s*CAST\\s*\\(\\s*revision\\s+AS\\s+UNSIGNED\\s*\\)\\s*,\\s*0\\s*\\)\\s*\\+\\s*1\\b");
    private static final Map<String,String> CONFLICT_KEYS=Map.of(
        "surveillance_kafka_connection_t","tenant_id,engine_code,connection_role",
        "surveillance_rule_snapshot_t","tenant_id,engine_code,channel_code,snapshot_version,is_del",
        "surveillance_topic_daily_stat_t","tenant_id,engine_code,resource_code,stat_date",
        "dq_quality_metric_t","tenant_id,run_id,task_rule_id",
        "api_job_t","job_code",
        "data_push_relay_client_t","relay_client_id,tenant_id,datasource_id");

    public static String adapt(String sql) {
        if(sql==null)return null;
        String expanded=jsonTables(sql);
        StringBuilder result=new StringBuilder();StringBuilder code=new StringBuilder();
        for(int i=0;i<expanded.length();) {
            char ch=expanded.charAt(i);
            if(ch=='\'' || ch=='"' || (ch=='-' && i+1<expanded.length() && expanded.charAt(i+1)=='-')
                || (ch=='/' && i+1<expanded.length() && expanded.charAt(i+1)=='*')) {
                result.append(code(code.toString()));code.setLength(0);
                int end=i+1;
                if(ch=='-' ) {while(end<expanded.length() && expanded.charAt(end)!='\n')end++;}
                else if(ch=='/') {int close=expanded.indexOf("*/",i+2);end=close<0?expanded.length():close+2;}
                else {while(end<expanded.length()) {if(expanded.charAt(end++)==ch) {if(end<expanded.length() && expanded.charAt(end)==ch){end++;continue;}break;}}}
                result.append(expanded,i,end);i=end;
            } else if(ch=='`') {
                int end=expanded.indexOf('`',i+1);
                if(end<0)throw new IllegalArgumentException("SQL identifier is not closed");
                code.append('"').append(expanded.substring(i+1,end).toLowerCase(Locale.ROOT)).append('"');i=end+1;
            } else {code.append(ch);i++;}
        }
        result.append(code(code.toString()));
        return upsert(result.toString());
    }

    private static String code(String sql) {
        // Asset center revisions are stored as text. Legacy empty and nonnumeric
        // revisions advance from zero, as they do in the MySQL update.
        sql = REVISION_INCREMENT.matcher(sql).replaceAll(Matcher.quoteReplacement(
            "CAST((CASE WHEN TRIM(revision) ~ '^[0-9]+$' THEN CAST(TRIM(revision) AS NUMERIC(32,0)) ELSE 0 END)+1 AS TEXT)"));
        sql=sql.replaceAll("(?i)\\bDATABASE\\s*\\(\\s*\\)","current_schema()");
        sql=sql.replaceAll("(?i)\\bCAST\\s*\\(([^()]+)\\s+AS\\s+SIGNED\\s*\\)","CAST($1 AS BIGINT)");
        // MySQL's CHAR without a length preserves the full value; Kingbase CHAR
        // means CHAR(1), which would silently collapse identifier joins.
        sql=sql.replaceAll("(?i)\\bCAST\\s*\\(([^()]+)\\s+AS\\s+CHAR\\s*\\)","CAST($1 AS TEXT)");
        Matcher aliases=Pattern.compile("(?i)\\bAS\\s+([a-z][a-z0-9_]*[A-Z][a-zA-Z0-9_]*)\\b").matcher(sql);
        StringBuffer text=new StringBuffer();
        while(aliases.find()) {
            String alias=aliases.group(1);
            if(alias.chars().anyMatch(Character::isLowerCase) && alias.chars().anyMatch(Character::isUpperCase))
                aliases.appendReplacement(text,Matcher.quoteReplacement("AS \""+alias+"\""));
        }
        aliases.appendTail(text);return text.toString();
    }

    static String upsert(String sql) {
        Matcher insert=Pattern.compile("(?is)^\\s*INSERT\\s+(IGNORE\\s+)?INTO\\s+([a-zA-Z0-9_\"]+)\\s*\\((.*?)\\)").matcher(sql);
        if(!insert.find())return sql;
        String table=insert.group(2).replace("\"","").toLowerCase(Locale.ROOT);
        if(insert.group(1)!=null) {
            if(!table.equals("surveillance_metric_batch_t"))throw new IllegalArgumentException("Unreviewed Kingbase INSERT IGNORE target");
            return sql.replaceFirst("(?i)INSERT\\s+IGNORE\\s+INTO","INSERT INTO")+" ON CONFLICT DO NOTHING";
        }
        Matcher marker=Pattern.compile("(?i)\\bON\\s+DUPLICATE\\s+KEY\\s+UPDATE\\b").matcher(sql);
        boolean found=false;
        while(marker.find())if(!protectedAt(sql,marker.start())){found=true;break;}
        if(!found)return sql;
        String keys=CONFLICT_KEYS.get(table);
        if(keys==null)throw new IllegalArgumentException("Unreviewed Kingbase UPSERT target");
        List<String> columns=split(insert.group(3));
        String tail=sql.substring(marker.end());
        Matcher incoming=Pattern.compile("(?i)VALUES\\s*\\(\\s*([a-zA-Z0-9_]+)\\s*\\)").matcher(tail);
        StringBuffer incomingSql=new StringBuffer();
        while(incoming.find())if(!protectedAt(tail,incoming.start()))
            incoming.appendReplacement(incomingSql,Matcher.quoteReplacement("EXCLUDED."+incoming.group(1)));
        incoming.appendTail(incomingSql);
        String assignments=incomingSql.toString();
        List<String> converted=new ArrayList<>();
        for(String assignment:split(assignments)) {
            int equals=assignment.indexOf('=');
            if(equals<0)throw new IllegalArgumentException("Invalid UPSERT assignment");
            String rhs=assignment.substring(equals+1);
            for(String column:columns) {
                String name=column.trim().replace("\"","");
                Matcher reference=Pattern.compile("(?i)(?<![.\\w])"+Pattern.quote(name)+"(?![\\w])").matcher(rhs);
                StringBuffer replacement=new StringBuffer();
                while(reference.find())if(!protectedAt(rhs,reference.start()))reference.appendReplacement(replacement,Matcher.quoteReplacement(table+"."+name));
                reference.appendTail(replacement);rhs=replacement.toString();
            }
            converted.add(assignment.substring(0,equals+1)+rhs);
        }
        return sql.substring(0,marker.start())+" ON CONFLICT ("+keys+") DO UPDATE SET "+String.join(",",converted);
    }

    static String jsonTables(String sql) {
        StringBuilder out=new StringBuilder();int position=0;
        Matcher matcher=Pattern.compile("(?i)\\bJSON_TABLE\\s*\\(").matcher(sql);
        while(matcher.find(position)) {
            if(protectedAt(sql,matcher.start())) {out.append(sql,position,matcher.end());position=matcher.end();continue;}
            int start=matcher.start(),open=sql.indexOf('(',start),end=closing(sql,open);
            String body=sql.substring(open+1,end);int comma=topComma(body);
            if(comma<0)throw new IllegalArgumentException("Invalid JSON_TABLE arguments");
            String expression=body.substring(0,comma),spec=body.substring(comma+1).trim();
            Matcher path=Pattern.compile("(?is)^'([^']+)'\\s+COLUMNS\\s*\\(").matcher(spec);
            if(!path.find())throw new IllegalArgumentException("Unreviewed JSON_TABLE specification");
            int columnsOpen=spec.indexOf('(',path.start());int columnsEnd=closing(spec,columnsOpen);
            List<String> projections=new ArrayList<>();
            for(String column:split(spec.substring(columnsOpen+1,columnsEnd))) {
                Matcher ordinal=Pattern.compile("(?i)^\\s*(\\w+)\\s+FOR\\s+ORDINALITY\\s*$").matcher(column);
                if(ordinal.matches()){projections.add("entry.ordinality AS \""+ordinal.group(1)+"\"");continue;}
                Matcher field=Pattern.compile("(?is)^\\s*(\\w+)\\s+((?:VAR)?CHAR\\s*\\(\\d+\\)|BIGINT|INTEGER|INT|DECIMAL\\s*\\(\\d+\\s*,\\s*\\d+\\))\\s+PATH\\s+'([^']+)'(?:\\s+NULL\\s+ON\\s+ERROR)?\\s*$").matcher(column);
                if(!field.matches())throw new IllegalArgumentException("Unreviewed JSON_TABLE column");
                String value="public.json_unquote(public.json_extract(entry.value::text,'"+field.group(3)+"'))";
                if(field.group(2).toUpperCase(Locale.ROOT).matches("BIGINT|INTEGER|INT|DECIMAL.*"))value="public.json_number("+value+")";
                projections.add("CAST("+value+" AS "+field.group(2)+") AS \""+field.group(1)+"\"");
            }
            out.append(sql,position,start).append("LATERAL (SELECT ").append(String.join(",",projections))
                .append(" FROM public.json_table_elements(CAST((").append(expression).append(") AS text),'").append(path.group(1))
                .append("') WITH ORDINALITY entry(value,ordinality))");
            position=end+1;
        }
        return out.append(sql.substring(position)).toString();
    }

    private static int topComma(String text) {
        int depth=0;char quote=0;
        for(int i=0;i<text.length();i++) {char ch=text.charAt(i);
            if(quote!=0){if(ch==quote){if(i+1<text.length() && text.charAt(i+1)==quote)i++;else quote=0;}continue;}
            if(ch=='\''||ch=='"')quote=ch;
            else if(ch=='(')depth++;else if(ch==')')depth--;else if(ch==','&&depth==0)return i;
        }return -1;
    }
    private static boolean protectedAt(String text,int position) {
        char quote=0;boolean line=false,block=false;
        for(int i=0;i<position;i++) {char ch=text.charAt(i),next=i+1<text.length()?text.charAt(i+1):0;
            if(line){if(ch=='\n')line=false;continue;}
            if(block){if(ch=='*'&&next=='/'){block=false;i++;}continue;}
            if(quote!=0){if(ch==quote){if(next==quote)i++;else quote=0;}continue;}
            if(ch=='\''||ch=='"'||ch=='`')quote=ch;
            else if(ch=='-'&&next=='-'){line=true;i++;}
            else if(ch=='/'&&next=='*'){block=true;i++;}
        }
        return quote!=0||line||block;
    }
    private static List<String> split(String text) {
        List<String> parts=new ArrayList<>();int offset=0,next;
        while((next=topComma(text.substring(offset)))>=0){parts.add(text.substring(offset,offset+next).trim());offset+=next+1;}
        parts.add(text.substring(offset).trim());return parts;
    }
    private static int closing(String sql,int open) {
        int depth=0;char quote=0;
        for(int i=open;i<sql.length();i++){char ch=sql.charAt(i);
            if(quote!=0){if(ch==quote){if(i+1<sql.length()&&sql.charAt(i+1)==quote)i++;else quote=0;}continue;}
            if(ch=='\''||ch=='"')quote=ch;else if(ch=='(')depth++;else if(ch==')'&&--depth==0)return i;
        }throw new IllegalArgumentException("Unclosed SQL expression");
    }
}
