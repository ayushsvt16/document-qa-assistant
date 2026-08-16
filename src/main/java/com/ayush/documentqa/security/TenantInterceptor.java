package com.ayush.documentqa.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Extracts and validates X-Tenant-Id header on every /api/** request.
 * Rejects requests without a valid tenant with 400 Bad Request.
 */
@Component
public class TenantInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TenantInterceptor.class);
    private static final String TENANT_HEADER = "X-Tenant-Id";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String tenantId = request.getHeader(TENANT_HEADER);

        if (tenantId == null || tenantId.isBlank()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            response.setContentType("application/json");
            response.getWriter().write("""
                    {"status":400,"error":"BAD_REQUEST","message":"Missing required header: X-Tenant-Id"}""");
            return false;
        }

        tenantId = tenantId.trim();
        if (tenantId.length() > 100) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            response.setContentType("application/json");
            response.getWriter().write("""
                    {"status":400,"error":"BAD_REQUEST","message":"X-Tenant-Id exceeds maximum length of 100"}""");
            return false;
        }

        TenantContext.setTenantId(tenantId);
        log.debug("Tenant context set: {}", tenantId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContext.clear();
    }
}
