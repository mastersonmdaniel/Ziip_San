package br.com.ziipsan.commercial.fiscal.queue;

import br.com.ziipsan.commercial.fiscal.nfage.NfageStatus;

import java.util.Objects;

/**
 * Porta de REENVIO (retransmissão) do MESMO documento à SEFAZ — sem implementação
 * nesta tarefa, no mesmo desenho do {@code NfageGateway} da P2.6: reenviar é
 * retransmitir o documento existente (mesma chave de acesso, mesmo número), nunca
 * emitir documento novo. Sem implementação ligada, a ação fica REQUESTED na trilha
 * e nenhum desfecho é fingido.
 */
public interface FiscalResendGateway {

    record ResendOutcome(NfageStatus resultingStatus, String protocol,
                         String rejectionCode, String rejectionMessage) {
        public ResendOutcome {
            Objects.requireNonNull(resultingStatus, "Estado resultante do reenvio é obrigatório");
            if (resultingStatus == NfageStatus.AUTHORIZED
                    && (protocol == null || protocol.isBlank())) {
                throw new IllegalArgumentException("Reenvio AUTHORIZED exige o protocolo da SEFAZ");
            }
            if (resultingStatus == NfageStatus.REJECTED
                    && (rejectionCode == null || rejectionCode.isBlank())) {
                throw new IllegalArgumentException("Reenvio REJECTED exige o código de rejeição da SEFAZ");
            }
        }
    }

    ResendOutcome retransmit(String accessKey);
}
