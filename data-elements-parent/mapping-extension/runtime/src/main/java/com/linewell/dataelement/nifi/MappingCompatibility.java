package com.linewell.dataelement.nifi;

import com.linewell.dataelement.mapping.core.*;
import org.apache.nifi.components.*;

/** Method calls deliberately avoid inlining a version constant from a mixed NAR. */
final class MappingCompatibility {
    private MappingCompatibility() {}
    static final PropertyDescriptor REQUIRED_VERSION=new PropertyDescriptor.Builder().name("required-engine-version").defaultValue("1.1.21").required(true).addValidator((subject,input,context)->{
        boolean valid=false;
        try{valid=input.equals(MappingEngine.engineVersion())&&input.equals(FunctionRegistry.libraryVersion())&&FunctionRegistry.NAMES.size()==56;}
        catch(LinkageError ignored){/* incompatible core is rejected before service enablement */}
        return new ValidationResult.Builder().subject(subject).input(input).valid(valid).explanation(valid?"兼容":"执行内核或函数库版本不匹配").build();
    }).build();
}
