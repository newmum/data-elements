package com.haitong.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/** Pure, dependency-free invariants shared by the HTTP, file and worker paths. */
public final class Rules {
    private Rules() {}
    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static boolean constantEquals(String a, String b) {
        return a != null && b != null && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
    public static String key(String value) {
        if (value == null || value.isBlank() || value.length() > 200 || value.chars().anyMatch(c -> c < 33 || c > 126))
            throw new IllegalArgumentException("Idempotency-Key 必须为 1–200 个无空白的可打印 ASCII 字符");
        return value;
    }
    public static String code(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}"))
            throw new IllegalArgumentException("无效的通道、目标或操作编码");
        return value;
    }
    public static String fileName(String name) {
        if (name == null || name.isBlank() || name.length() > 240 || name.equals(".") || name.equals("..") ||
            name.contains("/") || name.contains("\\") || name.chars().anyMatch(c -> c < 32 || c == 127))
            throw new IllegalArgumentException("文件名不得包含路径或控制字符");
        return name;
    }
    public static String remoteDirectory(String input) {
        if (input == null || !input.startsWith("/") || input.contains("\\") || input.chars().anyMatch(c -> c < 32 || c == 127))
            throw new IllegalArgumentException("FTP 目录必须为不含控制字符的绝对目录");
        for (String s : input.split("/")) if (s.equals("..") || s.equals(".")) throw new IllegalArgumentException("FTP 目录不能包含 . 或 ..");
        String s=input.replaceAll("/+", "/");
        return s.length()>1 && s.endsWith("/") ? s.substring(0,s.length()-1) : s;
    }
    public static boolean overlaps(String first, String second) {
        String a=remoteDirectory(first), b=remoteDirectory(second);
        return a.equals(b) || a.equals("/") || b.equals("/") || a.startsWith(b+"/") || b.startsWith(a+"/");
    }
    public static Path within(Path root, String relative) {
        Path base=root.toAbsolutePath().normalize();
        Path result=base.resolve(relative).normalize();
        if (!result.startsWith(base) || Path.of(relative).isAbsolute()) throw new IllegalArgumentException("路径越界");
        return result;
    }
    public static long backoffMs(int attempts, double jitter) {
        long base = Math.min(300_000L, 1000L << Math.min(18, Math.max(0, attempts-1)));
        return Math.min(300_000L, Math.max(100L, (long)(base * (0.8 + 0.4 * Math.clamp(jitter,0,1)))));
    }
    public static String aggregate(List<String> states) {
        if (states.isEmpty()) return "QUARANTINED";
        if (states.stream().allMatch("SUCCEEDED"::equals)) return "SUCCEEDED";
        if (states.contains("UNKNOWN")) return "UNKNOWN";
        if (states.contains("SUCCEEDED")) return "PARTIAL_SUCCESS";
        if (states.stream().allMatch("FAILED"::equals)) return "FAILED";
        if (states.contains("RUNNING")) return "RUNNING";
        if (states.contains("RETRY_WAIT")) return "RETRY_WAIT";
        return "PENDING";
    }
    public static boolean terminal(String state) { return Set.of("SUCCEEDED","FAILED","UNKNOWN","QUARANTINED").contains(state); }
    public static String extension(String name) {
        int i=name.lastIndexOf('.'); String e=i<0?".bin":name.substring(i).toLowerCase(Locale.ROOT);
        return e.matches("\\.[a-z0-9]{1,10}") ? e : ".bin";
    }
}
