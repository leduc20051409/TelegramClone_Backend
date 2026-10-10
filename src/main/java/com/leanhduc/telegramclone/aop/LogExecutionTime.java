package com.leanhduc.telegramclone.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to monitor execution time of methods or classes via Spring AOP.
 * If execution time exceeds thresholdMs, a WARN log is recorded.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface LogExecutionTime {


    long thresholdMs() default 200;
    String description() default "";
}
