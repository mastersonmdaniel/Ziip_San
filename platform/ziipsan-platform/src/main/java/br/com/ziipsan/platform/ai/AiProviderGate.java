package br.com.ziipsan.platform.ai;

import java.util.UUID;

/** Provedor no DPA e no RIPD (aceite; RAS-20 da P3.9): o modelo de linguagem e SUBOPERADOR.
 * Processamento no Brasil OU transferencia internacional amparada na Res. CD/ANPD 19/2024 e registrada
 * pela P3.9. TRAVA da P3.9 aplicada por mecanismo: suboperador ESTRANGEIRO sem clausula-padrao ANPD
 * incorporada BLOQUEIA a aprovacao/uso. Vedacao de treinar modelo com dado de tenant: sem atestacao
 * registrada do provedor, nao ha chamada. NENHUM provedor/modelo e nomeado aqui. */
public final class AiProviderGate {
    public record ProviderRegistration(boolean inDpa, boolean inRipd, boolean foreign,
                                       boolean anpdStandardClausesIncorporated,
                                       boolean noTrainingOnTenantDataAttested, String processingRegion) {}
    public interface ProviderRegistryPort { java.util.Optional<ProviderRegistration> registration(UUID tenantId); }
    public enum Verdict { ALLOWED, NOT_REGISTERED, NOT_IN_DPA, NOT_IN_RIPD, FOREIGN_WITHOUT_STANDARD_CLAUSES, TRAINING_NOT_ATTESTED, REGION_MISMATCH }
    private AiProviderGate() {}
    public static Verdict check(ProviderRegistration r, String configuredRegion) {
        if (r == null) return Verdict.NOT_REGISTERED;
        if (!r.inDpa()) return Verdict.NOT_IN_DPA;
        if (!r.inRipd()) return Verdict.NOT_IN_RIPD;
        if (r.foreign() && !r.anpdStandardClausesIncorporated()) return Verdict.FOREIGN_WITHOUT_STANDARD_CLAUSES;
        if (!r.noTrainingOnTenantDataAttested()) return Verdict.TRAINING_NOT_ATTESTED;
        if (configuredRegion == null || configuredRegion.isBlank() || r.processingRegion() == null
            || !configuredRegion.equals(r.processingRegion())) return Verdict.REGION_MISMATCH;
        return Verdict.ALLOWED;
    }
}
