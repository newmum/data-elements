package com.linewell.dataelement.bk.domain;

/** Adapter seam for external HTTP and platform-table rule sources. */
public interface SurveillanceRuleProvider {
    String type();

    SurveillanceRuleSnapshot load(String engineCode, String channelCode, String sourceRef);
}
