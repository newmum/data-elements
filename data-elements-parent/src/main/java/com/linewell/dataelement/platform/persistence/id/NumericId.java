package com.linewell.dataelement.platform.persistence.id;

import cn.hutool.core.util.IdUtil;

/**
 * Generates numeric-only distributed identifiers for persisted records.
 */
public final class NumericId {

    private NumericId() {
    }

    public static String nextId() {
        return IdUtil.getSnowflakeNextIdStr();
    }

    public static boolean isValid(String value) {
        return value != null && value.length() >= 17 && value.length() <= 19
                && value.chars().allMatch(Character::isDigit);
    }
}
