package br.com.ziipsan.commercial.billing.relief;

import br.com.ziipsan.commercial.billing.tariff.TariffConfigurationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Aliquota efectiva de PIS/Cofins aplicavel a um escopo (DESENHO-PA02 secao
 * 3.3). E o <b>unico</b> numero de que a formula F-03 precisa — e e sempre
 * parametro do tenant com proveniencia declarada ({@code rateSource}
 * obrigatoria): um numero sem proveniencia nao se aceita. Sem default, sem
 * seed, sem literal em codigo.
 */
public record ReliefRate(UUID id, UUID programId, ScopeKind scopeKind, String scopeRef,
                         BigDecimal effectiveRatePct, String rateSource,
                         LocalDate validFrom, LocalDate validTo) {

    /** Escopos da aliquota; a precedencia e SERVICE > CATEGORY > TARIFF > TENANT. */
    public enum ScopeKind {
        TENANT(0), TARIFF(1), CATEGORY(2), SERVICE(3);

        private final int specificity;

        ScopeKind(int specificity) {
            this.specificity = specificity;
        }

        public int specificity() {
            return specificity;
        }
    }

    public ReliefRate {
        Objects.requireNonNull(id, "id da aliquota e obrigatorio");
        Objects.requireNonNull(programId, "programa da aliquota e obrigatorio");
        Objects.requireNonNull(scopeKind, "escopo da aliquota e obrigatorio");
        if (scopeKind == ScopeKind.TENANT && scopeRef != null) {
            throw new TariffConfigurationException("Aliquota de escopo TENANT nao tem scopeRef");
        }
        if (scopeKind != ScopeKind.TENANT && (scopeRef == null || scopeRef.isBlank())) {
            throw new TariffConfigurationException(
                    "Aliquota de escopo " + scopeKind + " exige scopeRef");
        }
        Objects.requireNonNull(effectiveRatePct, "aliquota efectiva e obrigatoria");
        if (effectiveRatePct.signum() < 0
                || effectiveRatePct.compareTo(new BigDecimal("100")) >= 0) {
            throw new TariffConfigurationException(
                    "Aliquota efectiva fora de [0, 100): " + effectiveRatePct);
        }
        if (rateSource == null || rateSource.isBlank()) {
            throw new TariffConfigurationException(
                    "Aliquota efectiva sem proveniencia declarada nao se aceita");
        }
        Objects.requireNonNull(validFrom, "inicio de vigencia da aliquota e obrigatorio");
        if (validTo != null && validTo.isBefore(validFrom)) {
            throw new TariffConfigurationException("Vigencia da aliquota invertida");
        }
    }

    public boolean covers(LocalDate date) {
        return !date.isBefore(validFrom) && (validTo == null || !date.isAfter(validTo));
    }

    /** A linha aplica-se ao contexto (escopo e referencia batem)? */
    public boolean matches(ScopeKind kind, String ref) {
        return scopeKind == kind && Objects.equals(scopeRef, ref);
    }
}
