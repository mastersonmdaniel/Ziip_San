package br.com.ziipsan.reporting.forecast;

/**
 * Tecnica da projecao (ED-100). O aceite manda tecnica estatistica
 * simples e reserva aprendizado de maquina ao ADR-0042: esta entrega
 * implementa somente {@link #WEIGHTED_AVERAGE} (media ponderada das
 * coortes e razoes observadas). Pedir {@link #MACHINE_LEARNING} sem a
 * aprovacao do ADR-0042 e recusado; com a aprovacao, continua recusado
 * como nao implementado nesta entrega — nenhum modelo e fingido.
 */
public enum ForecastTechnique {
    WEIGHTED_AVERAGE,
    MACHINE_LEARNING
}
