package br.com.ziipsan.reporting.panels;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * ED-49: porta de leitura dos indicadores de ERP da ED-62 para o painel
 * estrategico. O modulo reporting NAO depende do modulo integration
 * (mesma fronteira da ED-48 com a ED-32/P4.2): a fiacao da integracao
 * adapta {@code ErpIndicators.strategicPanel} da ED-62 para esta porta,
 * transportando valor e origem sem recorte novo nem calculo. Sem
 * adaptador ligado, a secao nasce pendencia nomeada — nunca zero.
 */
public interface ErpIndicatorsPort {

    List<ErpIndicatorSnapshot> strategicIndicators(UUID tenantId, LocalDate competence);
}
