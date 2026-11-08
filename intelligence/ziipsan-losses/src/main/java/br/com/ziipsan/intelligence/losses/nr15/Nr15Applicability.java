package br.com.ziipsan.intelligence.losses.nr15;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Aplicabilidade da NR 15 a um tenant (ED-78; item literal do
 * aceite): "aplicavel ao tenant cuja agencia adote a NR 15, pela
 * aplicabilidade do perfil regulatorio".
 *
 * <p>Regra (fail-closed, espelhando a semantica da P2.31): o tenant
 * e aplicavel SOMENTE quando o perfil regulatorio vigente declara a
 * adocao da NR15 COM ao menos um ato publicado ({@code acts} nao
 * vazio - o mesmo criterio de {@code AnaNormCatalogLink
 * .adoptedCatalogCodes} na P2.31, que filtra por atos nao vazios).
 * Sem perfil, sem entrada NR15 no mapa, ou com status sem atos
 * (ex.: {@code NO_ACT_LOCATED}, como consta hoje para NR15 nos
 * perfis ARIS/AGIR da P2.31), o resultado e NAO aplicavel, com o
 * motivo nomeado - e os servicos da ED-78 recusam declarar/publicar
 * para tenant nao aplicavel.
 *
 * <p>A data de verificacao da ANA ({@code Nr15NormCatalog
 * .VERIFICATION_START}, maio/2028) NAO entra nesta regra: ela e
 * data de norma registrada no catalogo, nao condicao de
 * aplicabilidade presumida hoje nem no futuro.
 */
public final class Nr15Applicability {

    /** Codigos de motivo da nao aplicabilidade (auditaveis). */
    public enum Reason {
        APPLICABLE,
        NO_REGULATORY_PROFILE,
        NR15_NOT_IN_PROFILE,
        NR15_WITHOUT_ADOPTION_ACT
    }

    /**
     * Resultado da avaliacao: aplicabilidade, agencia (quando ha
     * perfil), status/atos declarados da adocao e o motivo.
     */
    public record Assessment(UUID tenantId, boolean applicable, String agencyCodeOrNull,
                             String adoptionStatusOrNull, List<String> adoptionActs,
                             Reason reason) {
        public Assessment {
            adoptionActs = adoptionActs == null ? List.of() : List.copyOf(adoptionActs);
        }
    }

    private final RegulatoryProfilePort profiles;

    public Nr15Applicability(RegulatoryProfilePort profiles) {
        this.profiles = Objects.requireNonNull(profiles, "porta de perfil regulatorio obrigatoria");
    }

    public Assessment assess(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenant da avaliacao obrigatorio");
        var profile = profiles.profileOf(tenantId);
        if (profile.isEmpty()) {
            return new Assessment(tenantId, false, null, null, List.of(),
                    Reason.NO_REGULATORY_PROFILE);
        }
        RegulatoryProfilePort.ProfileView view = profile.get();
        var adoption = view.adoptionOf(Nr15NormCatalog.NR_KEY);
        if (adoption.isEmpty()) {
            return new Assessment(tenantId, false, view.agencyCode(), null, List.of(),
                    Reason.NR15_NOT_IN_PROFILE);
        }
        RegulatoryProfilePort.Adoption ad = adoption.get();
        if (!ad.hasActs()) {
            return new Assessment(tenantId, false, view.agencyCode(), ad.status(), ad.acts(),
                    Reason.NR15_WITHOUT_ADOPTION_ACT);
        }
        return new Assessment(tenantId, true, view.agencyCode(), ad.status(), ad.acts(),
                Reason.APPLICABLE);
    }

    /** Exige aplicabilidade; recusa nomeada citando o motivo e a agencia. */
    public Assessment requireApplicable(UUID tenantId) {
        Assessment assessment = assess(tenantId);
        if (!assessment.applicable()) {
            throw new IllegalStateException("NR15_NOT_APPLICABLE: tenant " + tenantId
                    + " sem adocao da NR 15 pela sua agencia (motivo " + assessment.reason()
                    + ", agencia " + assessment.agencyCodeOrNull()
                    + ", status " + assessment.adoptionStatusOrNull() + ")");
        }
        return assessment;
    }
}
