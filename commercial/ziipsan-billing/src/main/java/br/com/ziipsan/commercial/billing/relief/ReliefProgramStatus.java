package br.com.ziipsan.commercial.billing.relief;

import br.com.ziipsan.commercial.billing.tariff.TariffConfigurationException;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Maquina de estados do programa de desoneracao (DESENHO-PA02 secao 7.2).
 *
 * <p>{@code DRAFT -> SIMULATED -> APPROVED -> PUBLISHED -> ACTIVE -> SUPERSEDED};
 * {@code CANCELLED} so antes de {@code PUBLISHED}. Transicao fora do mapa e erro
 * fechado: sem simulacao e sem publicacao nao ha guarda que faca sentido.
 */
public enum ReliefProgramStatus {
    DRAFT, SIMULATED, APPROVED, PUBLISHED, ACTIVE, SUPERSEDED, CANCELLED;

    private static final Map<ReliefProgramStatus, Set<ReliefProgramStatus>> ALLOWED = Map.of(
            DRAFT, EnumSet.of(SIMULATED, CANCELLED),
            SIMULATED, EnumSet.of(APPROVED, CANCELLED),
            APPROVED, EnumSet.of(PUBLISHED, CANCELLED),
            PUBLISHED, EnumSet.of(ACTIVE, SUPERSEDED),
            ACTIVE, EnumSet.of(SUPERSEDED),
            SUPERSEDED, EnumSet.noneOf(ReliefProgramStatus.class),
            CANCELLED, EnumSet.noneOf(ReliefProgramStatus.class));

    /** Exige que a transicao {@code this -> next} seja uma das desenhadas. */
    public void requireCanTransitionTo(ReliefProgramStatus next) {
        if (next == null || !ALLOWED.get(this).contains(next)) {
            throw new TariffConfigurationException(
                    "Transicao de programa de desoneracao nao permitida: " + this + " -> " + next);
        }
    }

    /** A guarda de emissao so se liga a programas publicados/activos. */
    public boolean engagesEmissionGuard() {
        return this == PUBLISHED || this == ACTIVE;
    }
}
