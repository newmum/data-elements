package com.linewell.dataelement.integration.nifi.canvas.compile;

import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** External programs run on the selected NiFi host; arguments are separate ProcessBuilder values. */
final class ScriptProcessPolicy {
    static final String PROCESSOR_TYPE = "org.apache.nifi.processors.standard.ExecuteStreamCommand";
    private static final Set<String> KEYS = Set.of("transform.python", "transform.java", "transform.shell");

    static boolean supports(String key) { return KEYS.contains(key); }

    static Map<String, String> properties(Pipeline.Node node) {
        Map<String, Object> config = node.config() == null ? Map.of() : node.config();
        String executable = text(config, "executable", switch (node.manifestKey()) {
            case "transform.python" -> "python3";
            case "transform.java" -> "java";
            default -> "/bin/sh";
        });
        String program = text(config, "programPath", "");
        if (program.isBlank()) fail(node, "programPath", "请填写 NiFi 服务器上的脚本或 JAR 路径");
        if (executable.isBlank()) fail(node, "executable", "请填写 NiFi 服务器上的运行程序");
        if (program.startsWith("-")) fail(node, "programPath", "文件路径不能以 - 开头，请使用绝对路径或 ./ 相对路径");
        String suffix = switch (node.manifestKey()) {
            case "transform.python" -> ".py";
            case "transform.java" -> ".jar";
            default -> ".sh";
        };
        if (!program.toLowerCase(java.util.Locale.ROOT).endsWith(suffix))
            fail(node, "programPath", "请选择 " + suffix + " 文件；路径指向 NiFi 服务器，不是浏览器所在电脑");
        List<String> arguments = new ArrayList<>();
        if ("transform.java".equals(node.manifestKey())) arguments.add("-jar");
        if ("transform.python".equals(node.manifestKey())) arguments.add("-u");
        arguments.add(program);
        Object supplied = config.get("arguments");
        if (supplied != null && !(supplied instanceof List<?>)) fail(node, "arguments", "程序参数必须逐项填写");
        if (supplied instanceof List<?> values) {
            if (values.size() > 64) fail(node, "arguments", "程序参数最多 64 项");
            for (Object value : values) {
                if (!(value instanceof String)) fail(node, "arguments", "每个程序参数必须是文本");
                arguments.add((String) value);
            }
        }
        Map<String, String> props = new LinkedHashMap<>();
        props.put("Command Path", literal(node, "executable", executable));
        props.put("Command Arguments Strategy", "Dynamic Property Arguments");
        props.put("Ignore STDIN", "false");
        String directory = text(config, "workingDirectory", "");
        if (!directory.isBlank()) props.put("Working Directory", literal(node, "workingDirectory", directory));
        for (int i = 0; i < arguments.size(); i++) {
            String value = literal(node, "arguments", arguments.get(i));
            if (value.isEmpty()) fail(node, "arguments", "请移除未填写的参数行");
            props.put("command.argument." + i, value);
        }
        return props;
    }

    private static String text(Map<String, Object> config, String key, String fallback) {
        Object value = config.get(key);
        return value == null ? fallback : String.valueOf(value).trim();
    }

    private static String literal(Pipeline.Node node, String field, String value) {
        if (value.length() > 8192 || value.indexOf('\0') >= 0 || value.contains("${"))
            fail(node, field, "请使用不含 NiFi 表达式的普通文本，单项不超过 8192 字符");
        return value;
    }

    private static void fail(Pipeline.Node node, String field, String message) {
        throw new DslCompiler.NodeConfigException(node.id(), node.label(), field, message, null);
    }
}
