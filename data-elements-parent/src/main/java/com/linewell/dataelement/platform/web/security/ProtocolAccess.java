package com.linewell.dataelement.platform.web.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** The matched MVC controller owns its external protocol access. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ProtocolAccess {
    boolean bindTenant() default true;
    boolean auditIdentityAccount() default false;
}
