package br.com.ziipsan.commercial.registry.legacyxml;

/**
 * Motivos de recusa de um XML no importador de documentos anteriores à
 * primeira produção (P2.19). Cada motivo é um fato verificável do arquivo
 * recebido — nenhum é presumido:
 *
 * <ul>
 *   <li>{@link #XML_INVALID} — o conteúdo não é XML bem-formado, está vazio
 *       ou traz construção proibida (DOCTYPE/XXE recusados pelo parser
 *       seguro);</li>
 *   <li>{@link #ACCESS_KEY_INVALID} — sem chave de acesso de 44 dígitos
 *       localizável, ou com dígito verificador (módulo 11) incorreto, ou
 *       com competência (AAMM) impossível;</li>
 *   <li>{@link #SIGNATURE_INVALID} — sem elemento {@code Signature}, com
 *       mais de um, ou com a validação criptográfica XMLDSig reprovada
 *       (conteúdo alterado depois da assinatura cai aqui);</li>
 *   <li>{@link #MISSING_AUTHORIZATION_PROTOCOL} — sem o protocolo de
 *       autorização ({@code infProt/nProt}), ou com {@code nProt} mas SEM
 *       {@code cStat} (correção A4 da revisão: número de protocolo sem o
 *       status é alegação, não autorização provada), ou com {@code cStat}
 *       diferente de autorizado (100/150): XML assinado mas não autorizado
 *       não é documento fiscal autorizado;</li>
 *   <li>{@link #ISSUER_MISMATCH} — emitente diferente do emitente esperado
 *       do tenant: ou o {@code emit/CNPJ} diverge do CNPJ embutido na chave
 *       de acesso (documento internamente inconsistente), ou o emitente do
 *       documento não é o emitente esperado declarado na importação;</li>
 *   <li>{@link #MODEL_NOT_ACCEPTED} — modelo do documento (posições 21–22
 *       da chave) fora dos modelos aceitos declarados no mapeamento;</li>
 *   <li>{@link #DUPLICATE} — chave de acesso já aceita neste lote ou já
 *       importada em lote anterior do tenant (repetido).</li>
 * </ul>
 */
public enum LegacyXmlRejection {
    XML_INVALID,
    ACCESS_KEY_INVALID,
    SIGNATURE_INVALID,
    MISSING_AUTHORIZATION_PROTOCOL,
    ISSUER_MISMATCH,
    MODEL_NOT_ACCEPTED,
    DUPLICATE
}
