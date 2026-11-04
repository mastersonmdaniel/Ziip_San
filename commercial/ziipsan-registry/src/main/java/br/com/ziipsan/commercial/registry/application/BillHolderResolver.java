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
 *
 * <p>P1.41 (§2.2, unidade sem usuário vigente): o que acontece sem usuário
 * vigente depende de {@code registry.vacant_unit_holder} — padrão de
 * fábrica {@code NO_HOLDER_BILL_HELD} (sem titular, com fatura retida até
 * definir o responsável: devolve nulo). {@code OWNER_AS_PROVISIONAL_HOLDER}
 * devolve o proprietário vigente como titular provisório, e
 * {@code LAST_USER_UNTIL_NOTICE} devolve o último usuário encerrado, até
 * que um aviso (nova titularidade, marcação de vacância) o substitua. O
 * responsável provisório da sucessão (papel RESPONSIBLE) NUNCA vira nome na
 * conta por si só: a regra do usuário titular vale "inclusive quando há
 * responsável provisório".
 *
 * <p>Nome social (P1.41, P2.17): a conta mostra o nome social cadastrado
 * (PF com social_name) quando {@code registry.social_name_display} é
 * {@code SOCIAL_EVERYWHERE_EXCEPT_FISCAL} (padrão); o nome civil só vale
 * onde a lei exige (documento fiscal próprio, P2.8 — fora desta conta).
 */
@Component
public class BillHolderResolver {

    @PersistenceContext
    private EntityManager em;

    /**
     * Nome a imprimir na conta: o usuário titular vigente; com
     * USER_HOLDER_AND_OWNER, "usuário titular / proprietário" quando houver
     * proprietário vigente distinto. Nulo quando não há usuário vigente e o
     * padrão de fábrica (fatura retida) vale.
     */
    public String holderName(UUID unitId, String displayMode) {
        return holderName(unitId, displayMode,
                HolderSettingsService.Factory.VACANT_UNIT_HOLDER,
                HolderSettingsService.Factory.SOCIAL_NAME_DISPLAY);
    }

    /**
     * Nome na conta com a política de unidade sem usuário e o modo de nome
     * social explícitos (o chamador lê ambos em HolderSettingsService).
     */
    public String holderName(UUID unitId, String displayMode, String vacantHolderPolicy,
                             String socialNameDisplayMode) {
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
        if (userId != null) {
            String userName = displayName(userId, socialNameDisplayMode);
            if (!"USER_HOLDER_AND_OWNER".equals(displayMode) || ownerId == null || ownerId.equals(userId)) {
                return userName;
            }
            return userName + " / " + displayName(ownerId, socialNameDisplayMode);
        }
        if (HolderSettingsService.VacantHolder.OWNER_AS_PROVISIONAL.equals(vacantHolderPolicy) && ownerId != null) {
            return displayName(ownerId, socialNameDisplayMode);
        }
        if (HolderSettingsService.VacantHolder.LAST_USER_UNTIL_NOTICE.equals(vacantHolderPolicy)) {
            UUID lastUser = lastEndedUser(unitId);
            if (lastUser != null) {
                return displayName(lastUser, socialNameDisplayMode);
            }
        }
        return null;
    }

    /** Nome de exibição do cliente: social (PF) salvo modo CIVIL_NAME_ONLY. */
    public String displayName(UUID customerId, String socialNameDisplayMode) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT name, social_name, person_type FROM customers WHERE tenant_id = :t AND id = :id")
                .setParameter("t", RegistryContext.tenantId())
                .setParameter("id", customerId)
                .getResultList();
        if (rows.isEmpty()) {
            return null;
        }
        Object[] row = rows.get(0);
        String name = (String) row[0];
        String socialName = (String) row[1];
        String personType = (String) row[2];
        if (!HolderSettingsService.SocialNameDisplay.CIVIL_NAME_ONLY.equals(socialNameDisplayMode)
                && "PF".equals(personType) && socialName != null && !socialName.isBlank()) {
            return socialName;
        }
        return name;
    }

    private UUID lastEndedUser(UUID unitId) {
        @SuppressWarnings("unchecked")
        List<UUID> rows = em.createNativeQuery(
                        "SELECT uc.customer_id FROM unit_customers uc "
                                + "WHERE uc.tenant_id = :t AND uc.unit_id = :u AND uc.role = 'USER' "
                                + "AND uc.end_date IS NOT NULL "
                                + "ORDER BY uc.end_date DESC, uc.start_date DESC LIMIT 1")
                .setParameter("t", RegistryContext.tenantId())
                .setParameter("u", unitId)
                .getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }
}
