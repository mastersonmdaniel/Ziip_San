package br.com.ziipsan.commercial.fiscal.nfage;

import java.util.Objects;

/**
 * Porta de assinatura do XML da NFAg-e com o e-CNPJ do emitente guardado no cofre
 * local (§2.4; tratamento dos XMLs assinados como na P1.8: área separada e apagados ao
 * fim de cada ciclo de testes) — <b>sem implementação nesta tarefa</b>. O certificado
 * e a sua senha nunca passam por esta porta nem por este módulo: quem implementar a
 * porta lê o certificado do cofre, assina e devolve somente a referência do XML
 * assinado e o seu hash. A aceitação do e-CNPJ da Ziip/TGP como emitente de teste do
 * modelo 75 é aceite externo pendente (ver relatório da P2.6).
 */
public interface NfageSignerGateway {

    /**
     * @param xmlStorageUri URI do XML assinado na área separada de XMLs (§2.4/P1.8)
     * @param xmlSha256     SHA-256 do XML assinado (64 hex minúsculos), o mesmo que vai
     *                      a {@code fiscal_documents.xml_sha256}
     */
    record Assinatura(String xmlStorageUri, String xmlSha256) {

        // Contrato imposto na construção (correção da revisão cruzada da P2.6):
        // documento SIGNED sem a referência do XML no cofre ou com hash fora do
        // formato do dicionário (CHAR(64), hex minúsculo) é erro de quem
        // implementa a porta, nunca um estado gravável do documento.
        public Assinatura {
            Objects.requireNonNull(xmlStorageUri, "URI do XML assinado no cofre é obrigatória");
            if (xmlStorageUri.isBlank()) {
                throw new IllegalArgumentException("URI do XML assinado no cofre é obrigatória");
            }
            if (xmlSha256 == null || !xmlSha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "SHA-256 do XML assinado exige 64 hex minúsculos (fiscal_documents.xml_sha256)");
            }
        }
    }

    /**
     * @param accessKey chave de acesso do documento a assinar
     * @param payloadJson payload do documento montado pelo serviço (JSON via {@code Json}
     *                    do core) — o leiaute XML definitivo é do MOC e não é inventado aqui
     */
    Assinatura assinar(String accessKey, String payloadJson);
}
