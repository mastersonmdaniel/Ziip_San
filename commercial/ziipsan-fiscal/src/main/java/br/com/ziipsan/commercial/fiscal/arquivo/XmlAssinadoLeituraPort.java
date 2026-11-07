package br.com.ziipsan.commercial.fiscal.arquivo;

/**
 * Leitura dos bytes do XML assinado na área separada do cofre (§2.4/P1.8).
 *
 * <p>A porta de assinatura da P2.6 ({@code NfageSignerGateway}) devolve só a
 * URI e o SHA-256 do XML assinado — o XML em si vive na área do cofre e não
 * trafega pelo módulo fiscal. Para fazer a <b>cópia</b> de retenção da P2.18,
 * alguém precisa ler esses bytes da área do cofre: é esta porta. A integração
 * a implementa sobre a mesma infraestrutura do cofre (leitura pela URI que a
 * assinatura devolveu); o serviço de arquivamento confere o SHA-256 dos bytes
 * lidos contra o {@code xml_sha256} do documento <b>antes</b> de gravar
 * qualquer cópia — bytes divergentes são erro de integridade, nunca arquivados.
 */
public interface XmlAssinadoLeituraPort {

    /**
     * @param xmlStorageUri URI do XML assinado na área do cofre
     *                    ({@code fiscal_documents.xml_storage_uri}, via P2.6)
     * @return bytes exatos do XML assinado
     */
    byte[] lerXmlAssinado(String xmlStorageUri);
}
