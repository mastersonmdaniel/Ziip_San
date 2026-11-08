package br.com.ziipsan.core.inspection;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Consumo em ligacao cortada (aceite da ED-24, de v2.15: "consumo em
 * ligacao cortada gera OS de fiscalizacao, sem religacao automatica —
 * teste, 20d OS-10").
 *
 * <p>A regra e deliberadamente estreita:
 * <ul>
 *   <li>ligacao em situacao de CORTE com consumo medido positivo no periodo
 *       gera o hit {@link InspectionCriterion#CONSUMPTION_WHILE_CUT} — e so
 *       esse e o efeito desta regra;</li>
 *   <li>{@link Outcome#automaticReconnectionAllowed()} e SEMPRE falso neste
 *       caminho: religacao e ato proprio, por OS de religacao autorizada
 *       (fluxo de corte/religacao fora desta entrega), nunca efeito
 *       automatico da deteccao de consumo;</li>
 *   <li>ligacao ativa, ou corte sem consumo, nao gera hit.</li>
 * </ul>
 *
 * <p>[A DEFINIR] Janela de apuracao e limiar de consumo nao tem chave
 * catalogada nas fontes (ver {@link InspectionParameters}); chegam como
 * fatos medidos do chamador (leitura da P1.31/metering), nao como
 * constantes desta classe.
 */
public final class CutConsumptionRule {

    private CutConsumptionRule() {}

    /** Situacao da ligacao de agua para esta regra (espelha o fato do cadastro/corte; nao redefine o dominio deles). */
    public enum ConnectionState { ACTIVE, CUT }

    /**
     * Fato de entrada: estado da ligacao e consumo medido (m3) no periodo
     * apurado pelo chamador.
     */
    public record CutConsumptionFact(UUID consumerUnitId, ConnectionState connectionState,
                                     long measuredConsumptionM3, LocalDateTime periodEnd) {
        public CutConsumptionFact {
            Objects.requireNonNull(consumerUnitId, "unidade e obrigatoria");
            Objects.requireNonNull(connectionState, "situacao da ligacao e obrigatoria");
            if (measuredConsumptionM3 < 0)
                throw new IllegalArgumentException("consumo medido nao pode ser negativo: " + measuredConsumptionM3);
            Objects.requireNonNull(periodEnd, "fim do periodo apurado e obrigatorio");
        }
    }

    public record Outcome(InspectionCriterion.CriterionHit hit, boolean automaticReconnectionAllowed) {
        public boolean generatesInspection() {
            return hit != null;
        }
    }

    public static Outcome evaluate(CutConsumptionFact fact) {
        Objects.requireNonNull(fact, "fato de consumo em ligacao cortada e obrigatorio");
        if (fact.connectionState() == ConnectionState.CUT && fact.measuredConsumptionM3() > 0) {
            InspectionCriterion.CriterionHit hit = new InspectionCriterion.CriterionHit(
                    InspectionCriterion.CONSUMPTION_WHILE_CUT, fact.consumerUnitId(),
                    "consumo de " + fact.measuredConsumptionM3() + " m3 medido em ligacao cortada ate " + fact.periodEnd(),
                    fact.periodEnd());
            // Religacao automatica PROIBIDA neste caminho — sempre false.
            return new Outcome(hit, false);
        }
        return new Outcome(null, false);
    }
}
