package com.linewell.dataelement.feature.surveillance.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/** Canonicalizes fixed-channel identifiers before they are hashed and queried. */
public final class SurveillanceIdentifierCodec {
    private SurveillanceIdentifierCodec() {
    }

    public static String normalize(String channelCode, String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.trim();
        return switch (channelCode == null ? "" : channelCode.toLowerCase(Locale.ROOT)) {
            case "person" -> normalized.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
            case "mobile" -> normalized.replaceAll("[^0-9+]", "");
            case "vehicle" -> normalized.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
            default -> normalized;
        };
    }

    public static String hash(String channelCode, String value) {
        String normalized = normalize(channelCode, value);
        if (normalized.isBlank()) return "";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算布控标识摘要", exception);
        }
    }

    public static String identifierType(String channelCode) {
        return switch (channelCode == null ? "" : channelCode.toLowerCase(Locale.ROOT)) {
            case "person" -> "ID_CARD_NO";
            case "mobile" -> "PHONE_NO";
            case "vehicle" -> "PLATE_NO";
            default -> throw new IllegalArgumentException("不支持的布控通道: " + channelCode);
        };
    }

    public static String eventField(String channelCode) {
        return switch (channelCode == null ? "" : channelCode.toLowerCase(Locale.ROOT)) {
            case "person" -> "idCardNo";
            case "mobile" -> "phoneNo";
            case "vehicle" -> "plateNo";
            default -> throw new IllegalArgumentException("不支持的布控通道: " + channelCode);
        };
    }
}
