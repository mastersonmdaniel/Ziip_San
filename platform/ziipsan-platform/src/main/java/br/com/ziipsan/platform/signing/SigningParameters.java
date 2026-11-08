package br.com.ziipsan.platform.signing;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta dos parametros {@code sign.*} do tenant (P0.19: valor aceito e vigente
 * em {@code parameter_versions}; catalogo de definicoes no core). Todos sao
 * CLASSE D na spec — valor do tenant, sem padrao de fabrica neste codigo:
 *
 * <ul>
 *   <li>{@code sign.min_level.<document_type>} — nivel minimo por tipo de
 *       documento: {@code SIMPLE}, {@code ADVANCED} ou {@code QUALIFIED}.
 *       A spec manda o nivel ser do tenant "porque a lei atribui a cada ente
 *       a definicao e sua aplicacao entre privados nao foi verificada" —
 *       logo, ausencia de valor e RECUSA, nunca um minimo presumido;</li>
 *   <li>{@code sign.psc_provider} — PSC credenciado do certificado em nuvem
 *       da assinatura qualificada;</li>
 *   <li>{@code sign.timestamp_authority} — ACT credenciada do carimbo do
 *       tempo (RFC 3161).</li>
 * </ul>
 */
public interface SigningParameters {

    /** Valor cru de {@code sign.min_level.<document_type>} para o tenant, se houver vigente. */
    Optional<String> minLevelRaw(UUID tenantId, SignableDocumentType documentType);

    /** Valor de {@code sign.psc_provider} para o tenant, se houver vigente. */
    Optional<String> pscProvider(UUID tenantId);

    /** Valor de {@code sign.timestamp_authority} para o tenant, se houver vigente. */
    Optional<String> timestampAuthority(UUID tenantId);

    /**
     * Decisao do dono sobre documentos de entrega ao tenant (DPA, contrato)
     * usarem o servico — COM PROVENIENCIA (correcao D8 da revisao cruzada):
     * quem decidiu, quando e a referencia do registro (ata, chamado, ADR...).
     * Uma decisao HABILITADORA sem proveniencia completa nem se constroi.
     *
     * <p><b>Origem obrigatoria:</b> esta decisao NAO e um parametro
     * {@code sign.*} do tenant nem pode nascer de configuracao editavel pelo
     * proprio tenant (self-service) — se nascesse, o portao seria decorativo.
     * O adaptador desta porta le o REGISTRO DA DECISAO DO DONO DA PLATAFORMA
     * (a spec: "so por decisao do dono"), com a trilha de quem/quando/
     * referencia desse registro. Sem decisao registrada, o servico recusa
     * esses tipos.
     */
    record TenantDeliveryDecision(boolean enabled, String decidedBy, Instant decidedAt, String reference) {

        public TenantDeliveryDecision {
            if (decidedBy == null || decidedBy.isBlank()) {
                throw new IllegalArgumentException(
                        "decisao do dono exige quem decidiu (proveniencia, D8)");
            }
            if (decidedAt == null) {
                throw new IllegalArgumentException(
                        "decisao do dono exige quando foi decidida (proveniencia, D8)");
            }
            if (reference == null || reference.isBlank()) {
                throw new IllegalArgumentException(
                        "decisao do dono exige a referencia do registro da decisao (proveniencia, D8)");
            }
        }
    }

    /** Decisao do dono registrada para este tenant, se houver (com proveniencia). */
    Optional<TenantDeliveryDecision> tenantDeliveryDecision(UUID tenantId);
}
