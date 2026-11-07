package br.com.ziipsan.commercial.fiscal.arquivo;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro de retenção de uma cópia de XML autorizado (P2.18): o que foi
 * arquivado, onde, com que hash e até quando está retido.
 *
 * <p>O documento fiscal continua sendo o do dicionário (V4_5, P2.6): este
 * registro <b>não</b> duplica o dicionário — ele referencia o documento
 * ({@code documentoId}, {@code accessKey}) e guarda o que é próprio da cópia
 * de retenção: a chave e a URI no armazenamento da P0.45, o elo da
 * {@code HashChain} e o fim da retenção. O {@code xmlSha256} aqui é o mesmo
 * valor de {@code fiscal_documents.xml_sha256} (conferido sobre os bytes antes
 * da gravação), repetido no registro porque é contra ele que a leitura
 * confere os bytes, e é contra o elo aqui gravado que a leitura confere a
 * coerência registro × elo da cadeia (a porta da cadeia não recebe o hash —
 * não há conferência elo × conteúdo nesta camada, ver {@code HashChainPort}).
 *
 * @param id          identificador do registro de retenção
 * @param tenantId    tenant do documento
 * @param documentoId documento fiscal arquivado ({@code fiscal_documents.id})
 * @param accessKey   chave de acesso de 44 posições do documento
 * @param chaveObjeto chave determinística do objeto no armazenamento da P0.45
 * @param uriRetencao URI da cópia retida, como devolvida pelo armazenamento
 * @param xmlSha256   SHA-256 do XML autorizado (64 hex minúsculos)
 * @param hashChain   hash do elo gravado na {@code HashChain} para esta cópia
 * @param origem      origem declarada do XML (guarda de origem, ver enum)
 * @param ambiente    ambiente do dicionário do documento (nome do enum da P2.6)
 * @param retidoAte   fim do bloqueio de retenção (instante de quem configura)
 * @param arquivadoEm instante do arquivamento, do {@code Clock} injetado
 * @param registroJson retrato de auditoria do arquivamento, serializado com o
 *                     {@code Json} do core (nunca {@code Map.toString()})
 */
public record RegistroRetencaoXml(UUID id, UUID tenantId, UUID documentoId, String accessKey,
                                  String chaveObjeto, String uriRetencao, String xmlSha256,
                                  String hashChain, OrigemXmlAutorizado origem, String ambiente,
                                  Instant retidoAte, Instant arquivadoEm, String registroJson) {

    public RegistroRetencaoXml {
        Objects.requireNonNull(id, "Id do registro de retenção é obrigatório");
        Objects.requireNonNull(tenantId, "Tenant do registro de retenção é obrigatório");
        Objects.requireNonNull(documentoId, "Documento do registro de retenção é obrigatório");
        if (accessKey == null || !accessKey.matches("\\d{44}")) {
            throw new IllegalArgumentException("Registro de retenção exige chave de acesso de 44 dígitos");
        }
        if (chaveObjeto == null || chaveObjeto.isBlank()) {
            throw new IllegalArgumentException("Registro de retenção exige a chave do objeto");
        }
        if (uriRetencao == null || uriRetencao.isBlank()) {
            throw new IllegalArgumentException("Registro de retenção exige a URI da cópia retida");
        }
        if (!Sha256.formatoValido(xmlSha256)) {
            throw new IllegalArgumentException("Registro de retenção exige xmlSha256 em 64 hex minúsculos");
        }
        if (!Sha256.formatoValido(hashChain)) {
            throw new IllegalArgumentException("Registro de retenção exige o hash do elo da HashChain em 64 hex minúsculos");
        }
        Objects.requireNonNull(origem, "Origem do registro de retenção é obrigatória");
        Objects.requireNonNull(ambiente, "Ambiente do registro de retenção é obrigatório");
        Objects.requireNonNull(registroJson, "Retrato JSON do registro de retenção é obrigatório");
        Objects.requireNonNull(retidoAte, "Fim da retenção é obrigatório");
        Objects.requireNonNull(arquivadoEm, "Instante do arquivamento é obrigatório");
    }
}
