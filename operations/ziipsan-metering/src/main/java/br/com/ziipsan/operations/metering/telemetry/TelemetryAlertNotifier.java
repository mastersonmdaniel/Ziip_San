package br.com.ziipsan.operations.metering.telemetry;

import java.util.Map;
import java.util.UUID;

/**
 * Porta do canal de aviso ao consumidor da P3.5 (ED-17): os alertas de
 * telemetria — alarme do medidor, consumo contínuo por 24 horas e pico —
 * saem pelo mesmo canal do alerta preventivo de consumo anormal da P3.5,
 * disparados por evento do catálogo da P1.38 e montados pelo motor de
 * modelos da P2.30. A implementação é a fiação sobre o serviço de disparo
 * da P3.5; este módulo não conhece canal, provedor nem modelo.
 *
 * <p>Os {@code alertCode} desta entrega são códigos de modelo a registrar no
 * catálogo de modelos do tenant (P2.30) na implantação, como o
 * {@code alert.high-consumption} da P3.5: {@code telemetry.meter-alarm},
 * {@code telemetry.continuous-consumption} e {@code telemetry.peak-consumption}.
 */
public interface TelemetryAlertNotifier {

    void alertCustomer(UUID tenantId, UUID unitId, String alertCode, Map<String, Object> variables);
}
