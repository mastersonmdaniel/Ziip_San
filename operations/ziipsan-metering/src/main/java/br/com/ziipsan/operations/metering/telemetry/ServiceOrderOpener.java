package br.com.ziipsan.operations.metering.telemetry;

import java.util.UUID;

/**
 * Porta de abertura de OS a partir de um alarme (ED-17). Quem resolve o tipo
 * de OS no catálogo da P1.31 e cria a ordem no motor da {@code V9_1} é a
 * fiação da aplicação; este módulo só pede a abertura com a unidade e o tipo
 * de alarme, e guarda o identificador devolvido no alarme persistido.
 */
public interface ServiceOrderOpener {

    /**
     * Abre a OS do alarme e devolve o id dela.
     *
     * @param detailJson detalhe do alarme em JSON (pela classe {@code Json} do core)
     */
    UUID openForAlarm(UUID tenantId, UUID unitId, MeterAlarmKind kind, String detailJson);
}
