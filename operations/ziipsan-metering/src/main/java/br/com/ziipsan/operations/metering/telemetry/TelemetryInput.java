package br.com.ziipsan.operations.metering.telemetry;

import br.com.ziipsan.operations.metering.rules.MeteringErrorCode;
import br.com.ziipsan.operations.metering.rules.MeteringInputException;

/**
 * Validação de entrada dos pacotes de telemetria e supervisório (ED-17).
 *
 * <p>Existe porque os utilitários {@code require}/{@code notNull} de
 * {@link MeteringInputException} são de pacote (só o pacote {@code rules}
 * os usa): os pacotes novos validam por aqui, lançando a mesma exceção e os
 * mesmos códigos do módulo, sem tocar na classe dona.
 */
public final class TelemetryInput {

    private TelemetryInput() {
    }

    public static void require(boolean condition, MeteringErrorCode code, String message) {
        if (!condition) {
            throw new MeteringInputException(code, message);
        }
    }

    public static <T> T notNull(T value, String field) {
        if (value == null) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT, field + " é obrigatório");
        }
        return value;
    }
}
