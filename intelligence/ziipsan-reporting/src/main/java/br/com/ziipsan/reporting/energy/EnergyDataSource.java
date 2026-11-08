package br.com.ziipsan.reporting.energy;

import br.com.ziipsan.reporting.indicator.ConfidenceGrade;

/**
 * Origem de um dado de energia/emissoes da ED-90 e o grau de confianca
 * que o substitui nas fichas da ED-77 (aceite: "fonte automatica
 * (telemetria, fatura importada) no lugar da entrada manual das fichas
 * da ED-77, com grau de confianca atualizado").
 *
 * <p>Mapeamento DECLARADO desta entrega (decisao de execucao, ajustavel
 * na integracao — a P3.41 continua exigindo o grau declarado na ficha;
 * este mapa e o que a ED-90 declara ao atualizar a ficha):
 * <ul>
 *   <li>{@code MANUAL} = LOW — o grau forcado da ED-77
 *       ({@code ManualIndicatorInputs.FORCED_GRADE});</li>
 *   <li>{@code IMPORTED_BILL} = MEDIUM — documento da distribuidora,
 *       mas lido por arquivo com leiaute conferido na implantacao;</li>
 *   <li>{@code FLEET_ED59} = MEDIUM — registro operacional da frota
 *       (abastecimentos da ED-59), nao medicao de energia;</li>
 *   <li>{@code TELEMETRY} = HIGH — medicao automatica continua.</li>
 * </ul>
 */
public enum EnergyDataSource {
    MANUAL(ConfidenceGrade.LOW),
    IMPORTED_BILL(ConfidenceGrade.MEDIUM),
    FLEET_ED59(ConfidenceGrade.MEDIUM),
    TELEMETRY(ConfidenceGrade.HIGH);

    private final ConfidenceGrade confidenceGrade;

    EnergyDataSource(ConfidenceGrade confidenceGrade) {
        this.confidenceGrade = confidenceGrade;
    }

    /** Grau de confianca atualizado que esta origem carrega para a ficha. */
    public ConfidenceGrade confidenceGrade() {
        return confidenceGrade;
    }
}
