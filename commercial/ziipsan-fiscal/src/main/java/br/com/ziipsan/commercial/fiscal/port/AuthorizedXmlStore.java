package br.com.ziipsan.commercial.fiscal.port;

import java.util.UUID;

/**
 * Guarda do XML autorizado em {@code fiscal_documents_xml} (somente-inserção, V4_5).
 * Recebe SOMENTE XML do simulador ou sintético de teste: o XML assinado da homologação
 * com o e-CNPJ da Ziip/TGP nunca passa por aqui — ele vai para a
 * {@link HomologationXmlArea} (ADR-0033, ajuste v2.9).
 */
public interface AuthorizedXmlStore {

    /** Grava o XML (já compactado com gzip) com seu SHA-256; {@code source} é SIMULATOR ou SYNTHETIC_TEST. */
    void store(UUID tenantId, UUID documentId, byte[] xmlGzip, String sha256, String source);
}
