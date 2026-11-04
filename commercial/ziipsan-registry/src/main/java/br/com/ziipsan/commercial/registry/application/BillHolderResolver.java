package br.com.ziipsan.commercial.registry.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Nome na conta (P1.3, NR 11, art. 33, §2º): sai em nome do usuário titular.
 * A opção do tenant {@code bill.holder_name_display} (classe b, padrão
 * {@code USER_HOLDER_ONLY}, decisão do dono 6) permite exibir também o
 * proprietário ({@code USER_HOLDER_AND_OWNER}). O faturamento (P2.5) lê esta
 * mesma regra; aqui ela vive porque os vínculos são do cadastro.
 */
@Component
public class BillHolderResolver {

    @PersistenceContext
    private EntityManager em;

    /**
     * Nome a imprimir na conta: o usuário titular vigente; com
     * USER_HOLDER_AND_OWNER, "usuário titular / proprietário" quando houver
     * proprietário vigente distinto. Nulo quando não há usuário vigente.
     */
    public String holderName(UUID unitId, String displayMode) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT uc.customer_id, uc.role FROM unit_customers uc "
                                + "WHERE uc.tenant_id = :t AND uc.unit_id = :u AND uc.end_date IS NULL")
                .setParameter("t", RegistryContext.tenantId())
                .setParameter("u", unitId)
                .getResultList();
        UUID userId = null;
        UUID ownerId = null;
        for (Object[] row : rows) {
            if ("USER".equals(row[1]) && userId == null) {
                userId = (UUID) row[0];
            } else if ("OWNER".equals(row[1]) && ownerId == null) {
                ownerId = (UUID) row[0];
            }
        }
        if (userId == null) {
            return null;
        }
        String userName = customerName(userId);
        if (!"USER_HOLDER_AND_OWNER".equals(displayMode) || ownerId == null || ownerId.equals(userId)) {
            return userName;
        }
        return userName + " / " + customerName(ownerId);
    }

    private String customerName(UUID customerId) {
        @SuppressWarnings("unchecked")
        List<String> rows = em.createNativeQuery(
                        "SELECT name FROM customers WHERE tenant_id = :t AND id = :id")
                .setParameter("t", RegistryContext.tenantId())
                .setParameter("id", customerId)
                .getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }
}
