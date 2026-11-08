package br.com.ziipsan.commercial.billing.leakprotocol;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Evidencia LOCAL do cenario do POC-26 (alinea g do roteiro VISAN):
 * "duas revisoes completas e duas faturas alteradas por vazamento".
 *
 * <p>Esta classe valida, sobre dois protocolos, exatamente o que o
 * roteiro pede -- nada alem: os dois estao fechados como GRANTED, sao
 * de faturas DISTINTAS, de pedidos da P3.28 DISTINTOS
 * ({@code leakReviewId}) e de protocolos DISTINTOS
 * ({@code protocolId}) -- duas revisoes do mesmo pedido, ou o mesmo
 * protocolo repetido, nao contam como duas revisoes (correcao B2 da
 * revisao) --, cada um tem laudo assinado, decisao assinada e
 * recibo de retificacao da P2.20 (a fatura alterada). E a prova que os
 * testes desta entrega exercitam com portas falsas.
 *
 * <p>O ENSAIO do POC-26 no roteiro (ambiente, aparelho, roteiro
 * externo) NAO e esta classe: e prova externa, pendencia P-ED23-01 do
 * relatorio -- aqui ela nao e fingida nem presumida.
 */
public final class Poc26Evidence {

    private Poc26Evidence() {
    }

    /** Resultado da validacao do cenario: aprovado ou o primeiro motivo da recusa. */
    public record Verdict(boolean satisfied, String reason) {
    }

    /** Valida o cenario POC-26 sobre exatamente dois protocolos. */
    public static Verdict check(List<LeakReviewProtocol> protocols) {
        Objects.requireNonNull(protocols, "protocolos do cenario sao obrigatorios");
        if (protocols.size() != 2) {
            return new Verdict(false, "POC-26 pede exatamente duas revisoes; recebidas " + protocols.size());
        }
        LeakReviewProtocol first = protocols.get(0);
        LeakReviewProtocol second = protocols.get(1);
        if (first.billId().equals(second.billId())) {
            return new Verdict(false, "POC-26 pede duas faturas distintas alteradas por vazamento");
        }
        if (first.protocolId().equals(second.protocolId())) {
            return new Verdict(false, "POC-26 pede dois protocolos distintos; protocolId repetido");
        }
        if (first.leakReviewId().equals(second.leakReviewId())) {
            return new Verdict(false,
                    "POC-26 pede duas revisoes de pedidos distintos da P3.28; leakReviewId repetido");
        }
        for (LeakReviewProtocol protocol : protocols) {
            Verdict perProtocol = checkOne(protocol);
            if (!perProtocol.satisfied()) {
                return perProtocol;
            }
        }
        return new Verdict(true, "duas revisoes completas, duas faturas alteradas por vazamento (P2.20)");
    }

    private static Verdict checkOne(LeakReviewProtocol protocol) {
        UUID bill = protocol.billId();
        if (protocol.phase() != LeakReviewProtocol.Phase.CLOSED_GRANTED) {
            return new Verdict(false, "protocolo da fatura " + bill + " nao esta CLOSED_GRANTED: " + protocol.phase());
        }
        if (protocol.report() == null || !protocol.report().signed()) {
            return new Verdict(false, "protocolo da fatura " + bill + " sem laudo assinado (P3.34)");
        }
        if (protocol.decisionSignature() == null) {
            return new Verdict(false, "protocolo da fatura " + bill + " sem decisao assinada (P3.34)");
        }
        if (protocol.rectification() == null) {
            return new Verdict(false, "protocolo da fatura " + bill + " sem recibo de retificacao da P2.20");
        }
        return new Verdict(true, "ok");
    }
}
