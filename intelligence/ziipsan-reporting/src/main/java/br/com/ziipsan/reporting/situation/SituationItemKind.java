package br.com.ziipsan.reporting.situation;

/**
 * ED-93 (OPE-22): os SEIS conjuntos do console operacional em mapa,
 * literais do aceite — e somente eles:
 * ocorrencias abertas (ED-92), OS em execucao e vencidas, posicao das
 * equipes, alarmes de telemetria, nivel de reservatorios e unidades
 * fora de operacao. Nenhum outro conjunto e inventado aqui.
 */
public enum SituationItemKind {
    INCIDENT,
    WORK_ORDER,
    CREW,
    TELEMETRY_ALARM,
    RESERVOIR,
    UNIT_OUTAGE
}
