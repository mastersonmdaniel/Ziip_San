package br.com.sigsan.platform.tenant;

import br.com.sigsan.core.tenant.TenantContextHolder;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Tenant da sessão Hibernate, a partir do {@link TenantContextHolder}.
 *
 * <p>Sem tenant no contexto devolve {@link TenantRef#NONE}: a conexão fica sem contexto e a RLS não
 * devolve nada. Antes devolvia "public", o que punha a sessão no schema compartilhado.
 *
 * <p>Schema sem UUID (ou o inverso) é contexto inconsistente, erro de programação: falha em vez de
 * escolher um dos dois. A conferência schema ↔ UUID contra public.tenant_catalog é feita por quem
 * preenche o contexto (o filtro de autenticação, etapa E5), que resolve o schema a partir do UUID.
 */
@Component
public class TenantSchemaResolver implements CurrentTenantIdentifierResolver<TenantRef> {

    @Override
    public TenantRef resolveCurrentTenantIdentifier() {
        String schema = TenantContextHolder.getTenant();
        UUID tenantId = TenantContextHolder.getTenantId();
        if (schema == null && tenantId == null) {
            return TenantRef.NONE;
        }
        if (schema == null || tenantId == null) {
            throw new IllegalStateException(
                    "Contexto de tenant inconsistente: schema=" + schema + ", tenantId=" + tenantId);
        }
        return TenantRef.of(schema, tenantId);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
