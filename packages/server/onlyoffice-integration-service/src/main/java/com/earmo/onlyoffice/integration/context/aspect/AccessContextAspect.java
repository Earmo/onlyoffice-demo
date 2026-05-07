package com.earmo.onlyoffice.integration.context.aspect;

import com.earmo.onlyoffice.integration.context.AccessContext;
import com.earmo.onlyoffice.integration.context.AccessContextResolver;
import com.earmo.onlyoffice.integration.context.CurrentAccessContext;
import com.earmo.onlyoffice.integration.exception.AccessContextException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 在业务 controller 同步生命周期内绑定并清理访问上下文。
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AccessContextAspect {

    private final AccessContextResolver accessContextResolver;

    @Around("""
            within(@org.springframework.web.bind.annotation.RestController *)
            && within(com.earmo.onlyoffice.integration.controller..*)
            && !@annotation(com.earmo.onlyoffice.integration.context.annotation.SkipAccessContext)
            && !@within(com.earmo.onlyoffice.integration.context.annotation.SkipAccessContext)
            """)
    public Object bindAccessContext(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = currentRequest();
        AccessContext accessContext = accessContextResolver.resolve(request);
        CurrentAccessContext.set(accessContext);
        log.info(
                "access context bound: method={}, tenantId={}, orgId={}, actorUser={}",
                joinPoint.getSignature().toShortString(),
                accessContext.tenantId(),
                accessContext.currentOrgId(),
                accessContext.actorUser()
        );
        try {
            return joinPoint.proceed();
        } finally {
            CurrentAccessContext.clear();
            log.info("access context cleared: method={}", joinPoint.getSignature().toShortString());
        }
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        throw new AccessContextException("当前请求无法解析访问上下文。");
    }
}
