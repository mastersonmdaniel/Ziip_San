package br.com.ziipsan.commercial.fiscal.queue;

import java.util.Objects;

/**
 * Porta do evento de cancelamento de documento autorizado — sem implementação nesta
 * tarefa (mesmo desenho das demais portas fiscais). O pedido do operador nasce
 * PENDING na V4_11; só o retorno desta porta o conclui (protocolo) ou o mantém
 * pendente. Cancelamento nunca é dado como feito pela tela.
 */
public interface FiscalCancellationGateway {

    record CancellationOutcome(boolean confirmed, String protocol) {
        public CancellationOutcome {
            if (confirmed && (protocol == null || protocol.isBlank())) {
                throw new IllegalArgumentException("Cancelamento confirmado exige o protocolo do evento");
            }
        }
    }

    CancellationOutcome requestCancellation(String accessKey, String reason);
}
