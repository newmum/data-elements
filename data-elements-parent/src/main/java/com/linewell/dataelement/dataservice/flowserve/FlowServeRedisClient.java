package com.linewell.dataelement.dataservice.flowserve;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 轻量 Redis RESP 客户端，避免云梯运行时数据源污染平台默认 Redis 连接。 */
@Service
public class FlowServeRedisClient {
    public Object execute(Map<String, Object> config, String... command) {
        RedisEndpoint endpoint = endpoint(config);
        try (Socket socket = new Socket(endpoint.host(), endpoint.port())) {
            socket.setSoTimeout((int) endpoint.timeout().toMillis());
            InputStream input = socket.getInputStream();
            OutputStream output = socket.getOutputStream();
            if (!endpoint.password().isBlank()) {
                writeCommand(output, "AUTH", endpoint.password());
                readReply(input);
            }
            if (endpoint.database() > 0) {
                writeCommand(output, "SELECT", String.valueOf(endpoint.database()));
                readReply(input);
            }
            writeCommand(output, command);
            return readReply(input);
        } catch (Exception exception) {
            throw new IllegalStateException("Redis 执行失败：" + exception.getMessage(), exception);
        }
    }

    private RedisEndpoint endpoint(Map<String, Object> config) {
        Map<String, Object> values = config == null ? Map.of() : config;
        String url = String.valueOf(values.getOrDefault("url", ""));
        String host = String.valueOf(values.getOrDefault("host", "127.0.0.1"));
        int port = intValue(values.get("port"), 6379);
        if (url.startsWith("redis://")) {
            String body = url.substring("redis://".length());
            int at = body.lastIndexOf('@');
            if (at >= 0) {
                body = body.substring(at + 1);
            }
            int slash = body.indexOf('/');
            String hostPort = slash >= 0 ? body.substring(0, slash) : body;
            int colon = hostPort.lastIndexOf(':');
            if (colon >= 0) {
                host = hostPort.substring(0, colon);
                port = intValue(hostPort.substring(colon + 1), 6379);
            }
        }
        return new RedisEndpoint(
                host,
                port,
                String.valueOf(values.getOrDefault("password", "")),
                intValue(values.get("database"), 0),
                Duration.ofMillis(longValue(values.get("timeoutMs"), 5000)));
    }

    private void writeCommand(OutputStream output, String... parts) throws Exception {
        StringBuilder buffer = new StringBuilder("*").append(parts.length).append("\r\n");
        for (String part : parts) {
            byte[] bytes = (part == null ? "" : part).getBytes(StandardCharsets.UTF_8);
            buffer.append("$").append(bytes.length).append("\r\n")
                    .append(part == null ? "" : part).append("\r\n");
        }
        output.write(buffer.toString().getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    private Object readReply(InputStream input) throws Exception {
        int type = input.read();
        if (type < 0) {
            throw new EOFException("Redis 已关闭连接");
        }
        return switch ((char) type) {
            case '+' -> readLine(input);
            case '-' -> throw new IllegalStateException(readLine(input));
            case ':' -> Long.parseLong(readLine(input));
            case '$' -> bulk(input);
            case '*' -> array(input);
            default -> throw new IllegalStateException("未知 Redis 响应类型：" + (char) type);
        };
    }

    private String bulk(InputStream input) throws Exception {
        int length = Integer.parseInt(readLine(input));
        if (length < 0) {
            return null;
        }
        byte[] data = input.readNBytes(length);
        input.readNBytes(2);
        return new String(data, StandardCharsets.UTF_8);
    }

    private List<Object> array(InputStream input) throws Exception {
        int count = Integer.parseInt(readLine(input));
        List<Object> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            result.add(readReply(input));
        }
        return result;
    }

    private String readLine(InputStream input) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int previous = -1;
        int current;
        while ((current = input.read()) >= 0) {
            if (previous == '\r' && current == '\n') {
                byte[] bytes = buffer.toByteArray();
                return new String(bytes, 0, bytes.length - 1, StandardCharsets.UTF_8);
            }
            buffer.write(current);
            previous = current;
        }
        throw new EOFException("Redis 响应意外结束");
    }

    private int intValue(Object value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private long longValue(Object value, long fallback) {
        try {
            return value == null ? fallback : Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private record RedisEndpoint(
            String host, int port, String password, int database, Duration timeout) {}
}
