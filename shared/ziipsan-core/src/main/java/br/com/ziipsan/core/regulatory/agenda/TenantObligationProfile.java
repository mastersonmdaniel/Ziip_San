package br.com.ziipsan.core.regulatory.agenda;

import java.time.MonthDay;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * ED-98 (GES-05): o perfil do tenant do qual o calendario e gerado —
 * natureza juridica + perfil {@code reg.*} (o parametro
 * {@code reg.obligations}, classe d, definido no SQL pendente desta
 * ED-98 no mesmo padrao do {@code reg.reports} da ED-97).
 *
 * <p>Cada item exige responsavel (aceite): o perfil nomeia o responsavel
 * padrao e pode nomear responsaveis por tipo de obrigacao. Sem
 * responsavel resolvido, a geracao RECUSA — nenhum responsavel e
 * inventado. A data-base tarifaria (ancora do reajuste) vem do perfil;
 * sem ela, o item do reajuste nasce com vencimento [A DEFINIR].</p>
 */
public record TenantObligationProfile(UUID tenantId, TenantLegalNature legalNature,
                                      MonthDay tariffBaseDate, boolean fiscalReportsApplicable,
                                      UUID defaultResponsibleUserId,
                                      Map<ObligationKind, UUID> responsibleByKind) {

    public TenantObligationProfile {
        Objects.requireNonNull(tenantId, "tenant do perfil e obrigatorio");
        Objects.requireNonNull(legalNature, "natureza juridica e obrigatoria para gerar o calendario");
        responsibleByKind = responsibleByKind == null ? Map.of()
                : Map.copyOf(new EnumMap<>(responsibleByKind.isEmpty()
                        ? new EnumMap<>(ObligationKind.class) : responsibleByKind));
    }

    /** Responsavel do tipo: especifico do perfil ou o padrao; nulo se nenhum (geracao recusa). */
    public UUID responsibleFor(ObligationKind kind) {
        UUID specific = responsibleByKind.get(kind);
        return specific != null ? specific : defaultResponsibleUserId;
    }
}
