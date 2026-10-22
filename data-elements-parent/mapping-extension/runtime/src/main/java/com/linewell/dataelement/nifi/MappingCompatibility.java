package com.linewell.dataelement.nifi;

import com.linewell.dataelement.mapping.core.*;
import org.apache.nifi.components.*;

/** Method calls deliberately avoid inlining a version constant from a mixed NAR. */
final class MappingCompatibility {
    private MappingCompatibility() {}
    static final PropertyDescriptor REQUIRED_VERSION=new PropertyDescriptor.Builder().name("required-engine-version")
            .description("兼容旧流程保留，升级后不再要求与此值相等")
            .required(false).dynamic(true).addValidator(org.apache.nifi.processor.util.StandardValidators.NON_EMPTY_VALIDATOR).build();
    static void requireRuntime() {
        if(!MappingEngine.engineVersion().equals(FunctionRegistry.libraryVersion()))
            throw new MappingException("RUNTIME_INCOMPATIBLE","扩展包内部的执行内核与函数库不一致，请重新安装完整NAR包");
    }
}
