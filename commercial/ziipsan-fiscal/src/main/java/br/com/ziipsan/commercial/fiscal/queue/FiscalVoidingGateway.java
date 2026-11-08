package br.com.ziipsan.commercial.fiscal.queue;

import java.util.Objects;
import java.util.UUID;

/**
 * Porta do evento de inutilização de numeração conforme o leiaute — sem
 * implementação nesta tarefa. O evento real (XML do evento, assinatura, endpoint da
 * SEFAZ do modelo 75) depende do MOC e da homologação ({@code tpAmb=2}, aceite
 * externo da P2.33); aqui fica o contrato: série, faixa, motivo e ambiente entram, o
 * protocolo do evento registrado volta. Sem implementação ligada, a ação fica
 * REQUESTED — nenhuma inutilização é fingida como registrada na SEFAZ.
 */
public interface FiscalVoidingGateway {

    record VoidingEvent(UUID issuerId, int serie, long numberFrom, long numberTo,
                        String reason, int tpAmb) {
        public VoidingEvent {
            Objects.requireNonNull(issuerId, "Emitente da inutilização é obrigatório");
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("Evento de inutilização exige o motivo");
            }
            if (tpAmb != 1 && tpAmb != 2) {
                throw new IllegalArgumentException("tpAmb do evento de inutilização é 1 (produção) ou 2 (homologação)");
            }
        }
    }

    record VoidingResult(String protocol) {
        public VoidingResult {
            if (protocol == null || protocol.isBlank()) {
                throw new IllegalArgumentException("Evento de inutilização registrado exige o protocolo");
            }
        }
    }

    VoidingResult registerVoiding(VoidingEvent event);
}
