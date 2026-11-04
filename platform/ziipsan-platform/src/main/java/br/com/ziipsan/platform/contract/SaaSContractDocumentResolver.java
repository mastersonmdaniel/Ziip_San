package br.com.ziipsan.platform.contract;

import java.util.Optional;

/**
 * Resolucao das referencias de documento da cobrança SaaS (P2.14, ADR-0022).
 *
 * <p>A P2.12 guarda nas colunas {@code invoice_document_ref} e
 * {@code nfse_document_ref} apenas referencias (texto), nao o documento:
 * quem produz o documento (o motor fiscal da P2.13, no caso da NFS-e)
 * decide a forma da referencia; quem serve o download (esta frente)
 * resolve a referencia para bytes e confere o hash. Esta interface e o
 * ponto de extensao entre os dois lados: a implementacao de producao
 * ({@link ObjectStorageSaaSContractDocumentResolver}) entende a
 * convencao proposta pela P2.14 (ver o javadoc dela); referencia em
 * forma desconhecida resolve para vazio — o download responde 404
 * (documento indisponivel), nunca serve bytes errados nem vaza a
 * existencia de documento de outro tenant (o escopo ja foi conferido
 * antes de chegar aqui, em {@link TenantSaaSContractService}).
 *
 * <p>O hash ({@code sha256Hex}) e o hash registrado no momento da
 * autorizacao/emissao, quando a referencia o carrega. Quando presente,
 * o servico o confere contra o SHA-256 dos bytes lidos e recusa o
 * download em divergencia (fail-closed); quando ausente, o download
 * serve com o hash calculado no cabecalho, sem conferencia — e o
 * relatorio da P2.14 registra que o caminho do XML da NFS-e so esta
 * fechado quando a P2.13 passar a gravar o hash junto da referencia.
 */
public interface SaaSContractDocumentResolver {

    /** Documento resolvido: bytes, tipo, nome de arquivo e hash registrado (ou null). */
    record ResolvedDocument(byte[] content, String contentType, String filename, String sha256Hex) {
    }

    /** Resolve a referencia para o documento, ou vazio quando a forma e desconhecida ou o objeto nao existe. */
    Optional<ResolvedDocument> resolve(String documentRef);
}
