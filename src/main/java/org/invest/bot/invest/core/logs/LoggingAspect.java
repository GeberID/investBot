package org.invest.bot.invest.core.logs;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Aspect
@Component
public class LoggingAspect {
    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    @Pointcut("execution(* org.invest.bot.invest.api.InvestApiCore.*(..))")
    public void investApiCoreMethods() {}

    @Before("investApiCoreMethods()")
    public void logBefore(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();
        String args = Arrays.toString(joinPoint.getArgs());
        log.info("==> Вызов InvestApiCore метода: {} с аргументами: {}", methodName, args);
    }

    @AfterReturning(pointcut = "investApiCoreMethods()", returning = "result")
    public void logAfterReturning(JoinPoint joinPoint, Object result) {
        String methodName = joinPoint.getSignature().getName();
        log.info("<== Метод InvestApiCore {} успешно выполнен.", methodName);
        log.info("Результат: {}", result);
    }
}
