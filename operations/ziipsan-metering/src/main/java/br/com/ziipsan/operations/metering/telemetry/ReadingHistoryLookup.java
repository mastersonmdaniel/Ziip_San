package br.com.ziipsan.operations.metering.telemetry;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta do histórico de que a VEE precisa (ED-17): a leitura anterior do
 * ponto, os dígitos do mostrador do canal e a média diária de consumo do
 * ponto. A implementação lê o metering existente (a última leitura de
 * faturamento e a média da cascata de consumo); a VEE não presume nenhum
 * dos três — ausência é devolvida como vazio e tratada explicitamente.
 */
public interface ReadingHistoryLookup {

    /** A leitura do mostrador anterior à coleta, se o ponto já tem leitura. */
    Optional<Integer> previousDial(UUID tenantId, UUID measuringPointId);

    /** O instante da leitura anterior do ponto: é ele que dá o período (em dias) da estimativa. */
    Optional<java.time.OffsetDateTime> previousReadAt(UUID tenantId, UUID measuringPointId);

    /** Os dígitos do mostrador do canal; vazio quando o canal não tem mostrador cadastrado. */
    Optional<Integer> dialDigits(UUID tenantId, UUID channelId);

    /** A média diária de consumo do ponto (na unidade do mostrador), base da estimativa. */
    Optional<BigDecimal> averageDailyConsumption(UUID tenantId, UUID measuringPointId);
}
