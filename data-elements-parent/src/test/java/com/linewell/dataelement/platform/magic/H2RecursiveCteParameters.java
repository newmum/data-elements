package com.linewell.dataelement.platform.magic;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TreeMap;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import org.ssssssss.magicapi.modules.db.BoundSql;

/**
 * H2测试适配：其递归CTE仅支持主SELECT中的绑定参数，CTE内参数会返回错误空集。
 * 仅在测试拦截器把CTE参数转为严格转义的字面量，主查询仍绑定；生产Magic始终使用参数绑定。
 * 限制来源：https://h2database.github.io/html/advanced.html#recursive_queries
 */
final class H2RecursiveCteParameters {
    private H2RecursiveCteParameters() { }

    static PreparedStatement prepareStructured(Connection connection, String sql) {
        int main = mainSelect(sql);
        var arguments = new TreeMap<Integer, Call>();
        var settings = new ArrayList<Call>();
        return (PreparedStatement) Proxy.newProxyInstance(H2RecursiveCteParameters.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class}, new java.lang.reflect.InvocationHandler() {
                    private PreparedStatement prepared;

                    @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if (prepared != null) return new Call(method, args).apply(prepared);
                        if (method.getName().startsWith("set")) {
                            if (args.length >= 2 && args[0] instanceof Integer index) {
                                arguments.put(index, new Call(method, args.clone()));
                            } else {
                                settings.add(new Call(method, args));
                            }
                            return null;
                        }
                        if (method.getName().equals("close")) return null;
                        if (!method.getName().equals("executeQuery")) {
                            throw new IllegalStateException("Unexpected deferred JDBC method " + method.getName());
                        }
                        List<Object> values = arguments.values().stream().map(Call::value).toList();
                        var statement = new BoundSql(null, sql, new ArrayList<>(values), null);
                        inlinePrefix(statement, main);
                        int inlined = arguments.size() - statement.getParameters().length;
                        prepared = connection.prepareStatement(statement.getSql());
                        for (Call setting : settings) setting.apply(prepared);
                        for (var binding : arguments.tailMap(inlined, false).entrySet()) {
                            Call call = binding.getValue();
                            call.arguments()[0] = binding.getKey() - inlined;
                            call.apply(prepared);
                        }
                        return prepared.executeQuery();
                    }
                });
    }

    private static int mainSelect(String sql) {
        int depth = 0;
        char quote = 0;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (quote != 0) {
                if (ch == quote) quote = 0;
            } else if (ch == '\'' || ch == '"' || ch == '`') {
                quote = ch;
            } else if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
            } else if (depth == 0 && sql.startsWith("SELECT ", i)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Missing main SELECT in structured recursive query");
    }

    private record Call(Method method, Object[] arguments) {
        Object value() { return method.getName().equals("setNull") ? null : arguments[1]; }

        Object apply(PreparedStatement statement) throws Throwable {
            try {
                return method.invoke(statement, arguments);
            } catch (InvocationTargetException error) {
                throw error.getCause();
            }
        }
    }

    private static void inlinePrefix(BoundSql statement, int main) {
        String sql = statement.getSql();
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
