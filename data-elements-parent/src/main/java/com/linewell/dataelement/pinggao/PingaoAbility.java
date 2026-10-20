package com.linewell.dataelement.pinggao;

/** Minimal, non-sensitive part of a Pingao fjrbAbility response. */
public record PingaoAbility(String id, String abilityName, String status) {

    public boolean isOnline() {
        return "1".equals(status);
    }

    public boolean isUsable() {
        return id != null && !id.isBlank() && abilityName != null && !abilityName.isBlank();
    }
}
