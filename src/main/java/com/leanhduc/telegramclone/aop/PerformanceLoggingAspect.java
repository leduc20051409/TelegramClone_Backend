package com.leanhduc.telegramclone.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Aspect for profiling method execution times and warning on slow operations.
 */
@Slf4j
@Aspect
@Component
public class PerformanceLoggingAspect {

    @Around("@annotation(com.leanhduc.telegramclone.aop.LogExecutionTime) || @within(com.leanhduc.telegramclone.aop.LogExecutionTime)")
    public Object measureExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 1. Resolve LogExecutionTime annotation (method-level takes precedence over class-level)
        LogExecutionTime annotation = AnnotationUtils.findAnnotation(method, LogExecutionTime.class);
        if (annotation == null && joinPoint.getTarget() != null) {
            annotation = AnnotationUtils.findAnnotation(joinPoint.getTarget().getClass(), LogExecutionTime.class);
        }

        long thresholdMs = annotation != null ? annotation.thresholdMs() : 200;
        String taskName = resolveTaskName(joinPoint, annotation);

        long startTime = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;

            if (durationMs >= thresholdMs) {
                log.warn("[SLOW EXECUTION] {} took {} ms (exceeded threshold {} ms)",
                        taskName, durationMs, thresholdMs);
            } else {
                log.debug("[EXECUTION TIME] {} completed in {} ms", taskName, durationMs);
            }

            return result;
        } catch (Throwable ex) {
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            log.warn("[EXECUTION FAILED] {} failed after {} ms with {}: {}",
                    taskName, durationMs, ex.getClass().getSimpleName(), ex.getMessage());
            throw ex;
        }
    }

    private String resolveTaskName(ProceedingJoinPoint joinPoint, LogExecutionTime annotation) {
        if (annotation != null && !annotation.description().isBlank()) {
            return annotation.description() + " (" + joinPoint.getSignature().toShortString() + ")";
        }
        return joinPoint.getSignature().toShortString();
    }
}
