package br.com.sigsan.core.tenant;

import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class TenantContextHolder {

    private static final ThreadLocal<String> currentTenant = new ThreadLocal<>();
    private static final ThreadLocal<UUID> currentTenantId = new ThreadLocal<>();
    private static final ThreadLocal<UUID> currentUserId = new ThreadLocal<>();

    public static void setTenant(String tenant) {
        currentTenant.set(tenant);
    }

    public static String getTenant() {
        return currentTenant.get();
    }

    public static void setTenantId(UUID tenantId) {
        currentTenantId.set(tenantId);
    }

    public static UUID getTenantId() {
        return currentTenantId.get();
    }

    /** Usuário autenticado da requisição; vai para a GUC app.current_user (created_by da auditoria). */
    public static void setUserId(UUID userId) {
        currentUserId.set(userId);
    }

    public static UUID getUserId() {
        return currentUserId.get();
    }

    public static void clear() {
        currentTenant.remove();
        currentTenantId.remove();
        currentUserId.remove();
    }

    public static boolean hasTenant() {
        return currentTenant.get() != null;
    }
}
