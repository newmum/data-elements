package com.linewell.dataelement.nifi;

import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.processor.util.StandardValidators;

final class JsonWireOptions {
    // NiFi 2.11 removed the shared descriptor. Keep the saved JSON reader setting compatible with both SDKs.
    static final PropertyDescriptor ALLOW_COMMENTS = new PropertyDescriptor.Builder()
            .name("Allow Comments")
            .description("允许解析 JSON 文档中的注释。")
            .required(true)
            .allowableValues("true", "false")
            .defaultValue("false")
            .addValidator(StandardValidators.BOOLEAN_VALIDATOR)
            .build();

    private JsonWireOptions() {}
}
