package br.com.ziipsan.reporting.bi;

import java.time.Instant;
import java.util.Objects;

/**
 * ED-47: portão de acesso do usuário do SSO a uma fonte do Superset.
 *
 * <p>Regra única: o usuário só usa a fonte do SEU tenant. A defesa é em
 * camadas e esta é só a primeira — mesmo que alguém passe daqui, a role
 * de banco da fonte só lê a camada do próprio tenant
 * ({@code BiRoleProvisioningSql}); e mesmo que a sessão tente trocar a
 * GUC, a função de contexto ignora a GUC. Negação é auditada
 * ({@code ACCESS_DENIED}, SG-1252) ANTES de lançar: tentativa de acesso
 * cruzado é exatamente o evento que a auditoria existe para guardar.
 *
 * <p>Sem porta de auditoria ligada, o construtor recusa: não existe
 * modo "BI sem auditoria" nesta política.
 */
public final class BiAccessPolicy {

    private final BiAccessAuditPort audit;

    public BiAccessPolicy(BiAccessAuditPort audit) {
        this.audit = Objects.requireNonNull(audit,
                "Auditoria (SG-1252) é obrigatória: BI sem auditoria não é habilitado");
    }

    /**
     * Autoriza e audita o acesso; tenant divergente ou ausente lança
     * {@link BiAccessDeniedException} depois de gravar a negação.
     */
    public void authorize(BiUserIdentity identity, SupersetDataSource dataSource, Instant now) {
        Objects.requireNonNull(identity, "identity é obrigatória");
        Objects.requireNonNull(dataSource, "dataSource é obrigatória");
        if (!identity.hasTenant() || !identity.tenantId().equals(dataSource.tenantId())) {
            // A negação é registrada sob o tenant ALVO da tentativa (o da
            // fonte) quando o usuário não tem tenant próprio: é na trilha
            // desse tenant que a tentativa de acesso cruzado interessa.
            audit.record(BiAccessEvent.of(
                    identity.hasTenant() ? identity.tenantId() : dataSource.tenantId(),
                    identity.userId(), BiAccessEvent.Action.ACCESS_DENIED,
                    dataSource.name(), null, now));
            throw new BiAccessDeniedException(
                    "Usuário sem acesso à fonte de BI de outro tenant");
        }
        audit.record(BiAccessEvent.of(identity.tenantId(), identity.userId(),
                BiAccessEvent.Action.DATASET_ACCESS, dataSource.name(), null, now));
    }
}
