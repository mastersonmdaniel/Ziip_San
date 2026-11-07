package br.com.ziipsan.commercial.fiscal.nfage;

import java.util.Objects;

/**
 * Porta de transmissão da NFAg-e à SEFAZ — <b>sem implementação nesta tarefa</b>, no
 * mesmo desenho da porta {@code NfseNacionalGateway} da P3.6. A transmissão real
 * (endpoint estadual do modelo 75, retorno síncrono/assíncrono, consulta de recibo)
 * depende do aceite externo de homologação ({@code tpAmb=2}) com o e-CNPJ da
 * Ziip/TGP e não é fingida aqui: sem uma implementação ligada, o documento fica em
 * {@link NfageStatus#RESERVED}/{@link NfageStatus#SIGNED} — nunca "autorizado" por
 * presunção.
 */
public interface NfageGateway {

    /** Desfecho possível de uma transmissão, espelhando os estados do dicionário. */
    enum Desfecho { AUTHORIZED, REJECTED, PENDING_RECONCILIATION }

    /**
     * @param protocolo        protocolo de autorização (obrigatório quando AUTHORIZED)
     * @param codigoRejeicao   código da rejeição da SEFAZ (quando REJECTED)
     * @param mensagemRejeicao mensagem da rejeição da SEFAZ (quando REJECTED)
     */
    record ResultadoTransmissao(Desfecho desfecho, String protocolo,
                                String codigoRejeicao, String mensagemRejeicao) {

        // Contrato imposto na construção (correção da revisão cruzada da P2.6):
        // autorização sem protocolo e rejeição sem código/mensagem não são
        // desfechos completos do dicionário (DAD-18) — são erro de quem
        // implementa a porta, nunca um estado gravável do documento.
        public ResultadoTransmissao {
            Objects.requireNonNull(desfecho, "Desfecho da transmissão é obrigatório");
            if (desfecho == Desfecho.AUTHORIZED
                    && (protocolo == null || protocolo.isBlank())) {
                throw new IllegalArgumentException(
                        "Transmissão AUTHORIZED exige o protocolo de autorização da SEFAZ");
            }
            if (desfecho == Desfecho.REJECTED
                    && ((codigoRejeicao == null || codigoRejeicao.isBlank())
                        || (mensagemRejeicao == null || mensagemRejeicao.isBlank()))) {
                throw new IllegalArgumentException(
                        "Transmissão REJECTED exige código e mensagem de rejeição da SEFAZ");
            }
        }
    }

    /**
     * @param accessKey     chave de acesso de 44 posições do documento assinado
     * @param xmlStorageUri URI do XML assinado no cofre (§2.4/P1.8) — o XML em si não
     *                      trafega por esta porta em claro fora do cofre
     */
    record DocumentoParaTransmissao(String accessKey, String xmlStorageUri) {
    }

    ResultadoTransmissao transmitir(DocumentoParaTransmissao documento);
}
