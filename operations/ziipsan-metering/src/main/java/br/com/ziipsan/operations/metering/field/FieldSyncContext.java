package br.com.ziipsan.operations.metering.field;

import java.util.Map;
import java.util.UUID;

/**
 * Contexto de uma sincronizacao de rota, montado pela integracao a partir dos dados
 * vigentes: parametros de OCR do tenant, digitos do mostrador por instalacao e leitura
 * anterior por instalacao (para a comparacao com o historico).
 */
public record FieldSyncContext(FieldReadingParameters parameters,
                               Map<UUID, Integer> meterDigitsByInstallation,
                               Map<UUID, Integer> previousReadingByInstallation) {
}
