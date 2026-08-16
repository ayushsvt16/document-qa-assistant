package com.ayush.documentqa.security;

/**
 * ThreadLocal-based tenant context.
 * Set by TenantInterceptor on every request, cleared after response.
 * All service/repository layers read the tenant from here — never from the raw request.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static String getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void setTenantId(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String requireTenantId() {
        String tenantId = CURRENT_TENANT.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("Tenant ID not set in context");
        }
        return tenantId;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
