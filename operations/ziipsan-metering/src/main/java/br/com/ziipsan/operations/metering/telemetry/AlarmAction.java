package br.com.ziipsan.operations.metering.telemetry;

/**
 * O que um alarme faz quando dispara (ED-17): abre OS, avisa o usuário ou os
 * dois — conforme a configuração do tenant em {@code ami.alert_thresholds}
 * (ver {@link AmiAlertThresholds}). Não há ação padrão: alarme sem ação
 * configurada é persistido com ação nula e o fato fica visível na trilha,
 * nunca convertido em silêncio para uma das ações.
 */
public enum AlarmAction {
    OPEN_SERVICE_ORDER,
    NOTIFY_CUSTOMER,
    BOTH
}
