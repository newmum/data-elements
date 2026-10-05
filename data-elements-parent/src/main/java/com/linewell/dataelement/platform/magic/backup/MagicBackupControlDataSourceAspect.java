package com.linewell.dataelement.platform.magic.backup;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MagicBackupControlDataSourceAspect {

    @Around("execution(* org.ssssssss.magicapi.backup..*(..))")
    public Object useControlDataSource(ProceedingJoinPoint joinPoint) throws Throwable {
        try (TenantContext.Scope ignored = TenantContext.control()) {
            return joinPoint.proceed();
        }
    }
}
