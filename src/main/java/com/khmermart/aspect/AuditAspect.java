package com.khmermart.aspect;

import com.khmermart.security.UserPrincipal;
import com.khmermart.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class AuditAspect {

  private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

  private final AuditLogService auditLogService;

  public AuditAspect(AuditLogService auditLogService) {
    this.auditLogService = auditLogService;
  }

  @Around("@annotation(audited)")
  public Object handleAudit(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
    Long actorId = resolveActorId();
    String ip = resolveClientIp();
    String entityId = resolveEntityId(joinPoint);

    Object result = joinPoint.proceed();

    try {
      auditLogService.log(actorId, audited.action(), audited.entity(), entityId, null, result, ip);
    } catch (Exception e) {
      log.warn(
          "Failed to audit action {} on {}: {}",
          audited.action(),
          audited.entity(),
          e.getMessage());
    }

    return result;
  }

  private Long resolveActorId() {
    try {
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
        return principal.id();
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  private String resolveClientIp() {
    try {
      ServletRequestAttributes attrs =
          (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
      if (attrs != null) {
        HttpServletRequest request = attrs.getRequest();
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
          return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
      }
    } catch (Exception ignored) {
    }
    return "127.0.0.1";
  }

  private String resolveEntityId(ProceedingJoinPoint joinPoint) {
    try {
      MethodSignature signature = (MethodSignature) joinPoint.getSignature();
      Method method = signature.getMethod();
      Parameter[] parameters = method.getParameters();
      Object[] args = joinPoint.getArgs();

      for (int i = 0; i < parameters.length; i++) {
        if (parameters[i].isAnnotationPresent(PathVariable.class)) {
          return String.valueOf(args[i]);
        }
        String name = parameters[i].getName();
        if ("id".equalsIgnoreCase(name)
            || "orderNumber".equalsIgnoreCase(name)
            || "code".equalsIgnoreCase(name)) {
          return String.valueOf(args[i]);
        }
      }
      if (args.length > 0 && (args[0] instanceof Long || args[0] instanceof String)) {
        return String.valueOf(args[0]);
      }
    } catch (Exception ignored) {
    }
    return null;
  }
}
