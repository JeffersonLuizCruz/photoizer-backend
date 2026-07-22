package com.photoizer.crm.shared.logging;

import jakarta.annotation.PostConstruct;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;
import java.util.Optional;

@Aspect
@Component
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    @PostConstruct
    void init() {
        log.info("LoggingAspect initialized and AOP is active");
    }

    @Pointcut("within(org.springframework.web.bind.annotation.RestController+)")
    public void controllerLayer() {
    }

    @Pointcut("within(org.springframework.stereotype.Service+)")
    public void serviceLayer() {
    }

    @Pointcut("within(org.springframework.stereotype.Repository+)")
    public void repositoryLayer() {
    }

    @Pointcut("within(com.photoizer.crm.shared.logging..*)")
    public void loggingLayer() {
    }

    @Around("controllerLayer() && !loggingLayer()")
    public Object logController(ProceedingJoinPoint joinPoint) throws Throwable {
        var start = System.currentTimeMillis();
        var methodName = joinPoint.getSignature().toShortString();
        var httpMethod = getHttpMethod();
        var path = getRequestPath();

        try {
            var result = joinPoint.proceed();
            var duration = System.currentTimeMillis() - start;
            log.info("[CONTROLLER] {} {} {} -> {} ({}ms)",
                httpMethod.orElse(""), path.orElse(""), methodName,
                getResultSummary(result), duration);
            return result;
        } catch (Exception e) {
            var duration = System.currentTimeMillis() - start;
            log.error("[CONTROLLER] {} {} {} -> ERROR {} ({}ms): {}",
                httpMethod.orElse(""), path.orElse(""), methodName,
                e.getClass().getSimpleName(), duration, e.getMessage());
            throw e;
        }
    }

    @Around("serviceLayer() && !loggingLayer()")
    public Object logService(ProceedingJoinPoint joinPoint) throws Throwable {
        var start = System.currentTimeMillis();
        var methodName = joinPoint.getSignature().toShortString();
        var args = SensitiveDataMask.maskArgs(joinPoint.getArgs());

        log.debug("[SERVICE] {} args=[{}]", methodName, args);

        try {
            var result = joinPoint.proceed();
            var duration = System.currentTimeMillis() - start;
            log.debug("[SERVICE] {} -> {} ({}ms)", methodName, getResultSummary(result), duration);
            return result;
        } catch (Exception e) {
            var duration = System.currentTimeMillis() - start;
            log.error("[SERVICE] {} -> {} ({}ms): {}", methodName,
                e.getClass().getSimpleName(), duration, e.getMessage());
            throw e;
        }
    }

    @Around("repositoryLayer() && !loggingLayer()")
    public Object logRepository(ProceedingJoinPoint joinPoint) throws Throwable {
        var start = System.currentTimeMillis();
        var methodName = joinPoint.getSignature().toShortString();

        try {
            var result = joinPoint.proceed();
            var duration = System.currentTimeMillis() - start;
            log.trace("[REPOSITORY] {} ({}ms)", methodName, duration);
            return result;
        } catch (Exception e) {
            var duration = System.currentTimeMillis() - start;
            log.error("[REPOSITORY] {} -> {} ({}ms): {}", methodName,
                e.getClass().getSimpleName(), duration, e.getMessage());
            throw e;
        }
    }

    private Optional<String> getHttpMethod() {
        return getRequestAttributes()
            .map(req -> req.getRequest().getMethod());
    }

    private Optional<String> getRequestPath() {
        return getRequestAttributes()
            .map(req -> req.getRequest().getRequestURI());
    }

    private Optional<ServletRequestAttributes> getRequestAttributes() {
        return Optional.ofNullable(
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes()
        );
    }

    private String getResultSummary(Object result) {
        if (result == null) {
            return "void";
        }
        if (result instanceof Collection<?> c) {
            return c.size() + " result(s)";
        }
        if (result instanceof Optional<?> o) {
            return o.isPresent() ? "present" : "empty";
        }
        if (result instanceof String s) {
            return s.length() > 100 ? s.substring(0, 100) + "..." : s;
        }
        if (result instanceof ResponseEntity<?> re) {
            var status = re.getStatusCode();
            return "ResponseEntity[" + status.value() + "]";
        }
        return result.getClass().getSimpleName();
    }
}
