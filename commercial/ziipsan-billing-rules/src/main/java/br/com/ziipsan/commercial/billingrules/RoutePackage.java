package br.com.ziipsan.commercial.billingrules;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Pacote da rota da emissao simultanea (ED-19): o que o servidor entrega ao
 * aparelho antes da saida e o que o aparelho devolve como referencia na
 * submissao. Carrega, por exigencia do aceite:
 *
 * <ul>
 *   <li>{@code task_id} de cada tarefa ({@link RouteTask});</li>
 *   <li>as tarifas (retrato por tarefa, {@link FieldTariffSnapshot});</li>
 *   <li>as versoes de parametro (por tarefa no retrato e, no pacote, o mapa
 *       {@code parameterVersions} da rota);</li>
 *   <li>a versao do contrato das regras e o HASH do artefato
 *       {@code ziipsan-billing-rules} que o servidor usa — o aparelho confere
 *       o JAR embarcado contra ele antes de aceitar a rota
 *       ({@link ArtifactHash#requireMatch});</li>
 *   <li>as versoes dos modelos termicos (ZPL/CPCL) da P2.30 por tipo de
 *       documento, para a impressao sair do modelo vigente da rota;</li>
 *   <li>o N do envio em lote ({@code field.send_every_n} resolvido).</li>
 * </ul>
 */
public record RoutePackage(UUID routeId, UUID tenantId, OffsetDateTime issuedAt,
                           String rulesContract, String rulesArtifactSha256,
                           List<RouteTask> tasks,
                           Map<String, String> parameterVersions,
                           Map<String, String> thermalTemplateVersions,
                           int sendEveryN) {

    public RoutePackage {
        Objects.requireNonNull(routeId, "rota do pacote e obrigatoria");
        Objects.requireNonNull(tenantId, "tenant do pacote e obrigatorio");
        Objects.requireNonNull(issuedAt, "emissao do pacote e obrigatoria");
        if (rulesContract == null || rulesContract.isBlank()) {
            throw new IllegalArgumentException("Versao do contrato das regras e obrigatoria no pacote");
        }
        if (rulesArtifactSha256 == null || rulesArtifactSha256.isBlank()) {
            throw new IllegalArgumentException(
                    "Hash do artefato ziipsan-billing-rules e obrigatorio no pacote (hash conferido)");
        }
        if (tasks == null || tasks.isEmpty()) {
            throw new IllegalArgumentException("Pacote da rota sem nenhuma tarefa");
        }
        tasks = List.copyOf(tasks);
        parameterVersions = parameterVersions == null ? Map.of() : Map.copyOf(parameterVersions);
        thermalTemplateVersions = thermalTemplateVersions == null ? Map.of() : Map.copyOf(thermalTemplateVersions);
        if (sendEveryN < 1) {
            throw new IllegalArgumentException("Envio a cada N imoveis deve ser >= 1: " + sendEveryN);
        }
    }

    /** Recusa pacote de outro contrato de regras: nem calcula com base errada. */
    public void requireCompatibleRules() {
        if (!RulesVersion.CONTRACT.equals(rulesContract)) {
            throw new IllegalStateException("Pacote da rota e do contrato " + rulesContract
                    + ", mas este artefato e " + RulesVersion.CONTRACT
                    + " — rota recusada (divergencia de base nem calcula)");
        }
    }
}
