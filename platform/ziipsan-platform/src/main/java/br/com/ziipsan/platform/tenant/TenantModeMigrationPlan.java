package br.com.ziipsan.platform.tenant;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * P4.6, item 7 do aceite: a mudanca de modo de um tenant (SCHEMA / SHARED /
 * DEDICATED) faz-se por PROCEDIMENTO DE MIGRACAO ENSAIADO — nunca por UPDATE
 * directo em {@code tenant_catalog.isolation_mode}, que moveria o rotulo sem
 * mover os dados.
 *
 * <p>Esta classe e o plano executavel desse procedimento: dada a transicao,
 * devolve a sequencia ordenada e fechada de passos, recusando as transicoes
 * malformadas antes de qualquer passo correr. O ensaio (docs/operacao/
 * P4.6-MUDANCA-DE-MODO-ENSAIO.md) executa este plano sobre tenants ficticios
 * nos tres pares de modos e regista a evidencia antes de a primeira mudanca
 * real ser autorizada.
 *
 * <p>Passos variam com a origem:
 * <ul>
 *   <li>Sair de SHARED: a fonte apaga-se POR tenant_id + crypto-shredding
 *       ({@link SharedTenantLifecycle}), porque nao ha schema para apagar.</li>
 *   <li>Sair de SCHEMA ou DEDICATED: a fonte descomissiona-se por schema/cluster
 *       inteiro, depois da verificacao de contagens no destino.</li>
 *   <li>Em todas: as migrations do destino correm pelo rollout da P1.14
 *       (por tenant e por cluster) e o isolamento por RLS do destino e
 *       verificado com o MESMO teste dos tres modos (item 8) antes da troca
 *       do catalogo — a troca do rotulo e o 8.º passo de 11 (depois da
 *       verificacao de RLS, antes da limpeza da fonte e do descongelamento
 *       das escritas), nunca o primeiro. (Correcção R-P46-09: este javadoc
 *       dizia "penultimo passo", o que contradizia a sequencia emitida e
 *       o relatorio; vale a sequencia: SWITCH_CATALOG_RESOLUTION e o
 *       passo 8 de 11.)</li>
 * </ul>
 */
public final class TenantModeMigrationPlan {

    /** Passos do procedimento, na ordem em que o plano os emite. */
    public enum Step {
        VALIDATE_TARGET,
        FREEZE_WRITES,
        EXPORT_SOURCE_BY_TENANT_ID,
        PROVISION_TARGET,
        APPLY_MIGRATIONS_BY_ROLLOUT,
        COPY_DATA_AND_VERIFY_COUNTS,
        VERIFY_RLS_ISOLATION,
        SWITCH_CATALOG_RESOLUTION,
        ERASE_SOURCE_BY_TENANT_ID_AND_SHRED_KEY,
        DECOMMISSION_SOURCE_SCHEMA,
        UNFREEZE_WRITES,
        RECORD_EVIDENCE
    }

    /** O plano de uma transicao: origem, destino e a sequencia fechada de passos. */
    public record Plan(UUID tenantId, IsolationMode from, IsolationMode to,
                       String sourceSchema, String sourceClusterId,
                       String targetSchema, String targetClusterId,
                       List<Step> steps) {
        public Plan {
            steps = List.copyOf(steps);
        }
    }

    private TenantModeMigrationPlan() {
    }

    /**
     * Monta o plano de uma transicao.
     *
     * @param tenantId       tenant que muda de modo
     * @param from           modo actual (do catalogo)
     * @param to             modo de destino
     * @param sourceSchema   schema actual
     * @param sourceClusterId cluster actual
     * @param targetSchema   schema de destino (obrigatorio: no SHARED, o schema
     *                       compartilhado de destino; nos demais, o schema proprio)
     * @param targetClusterId cluster de destino (obrigatorio e distinto de
     *                       {@code "default"} quando o destino e DEDICATED)
     */
    public static Plan plan(UUID tenantId, IsolationMode from, IsolationMode to,
                            String sourceSchema, String sourceClusterId,
                            String targetSchema, String targetClusterId) {
        Objects.requireNonNull(tenantId, "tenantId e obrigatorio");
        Objects.requireNonNull(from, "modo de origem e obrigatorio");
        Objects.requireNonNull(to, "modo de destino e obrigatorio");
        if (from == to) {
            throw new IllegalArgumentException(
                    "Mudanca de modo sem efeito: origem e destino sao " + from);
        }
        if (sourceSchema == null || sourceSchema.isBlank()) {
            throw new IllegalArgumentException("schema de origem e obrigatorio");
        }
        if (targetSchema == null || targetSchema.isBlank()) {
            throw new IllegalArgumentException("schema de destino e obrigatorio");
        }
        String sourceCluster = sourceClusterId == null || sourceClusterId.isBlank()
                ? "default" : sourceClusterId;
        String targetCluster = targetClusterId == null || targetClusterId.isBlank()
                ? "default" : targetClusterId;
        if (to == IsolationMode.DEDICATED && "default".equals(targetCluster)) {
            throw new IllegalArgumentException(
                    "Destino DEDICATED exige cluster proprio nomeado (nunca o 'default' compartilhado)");
        }
        if (from == IsolationMode.SHARED && to != IsolationMode.SHARED
                && targetSchema.equals(sourceSchema)) {
            throw new IllegalArgumentException(
                    "Sair do schema compartilhado exige um schema de destino distinto do compartilhado");
        }
        if (from == to && targetSchema.equals(sourceSchema) && targetCluster.equals(sourceCluster)) {
            throw new IllegalArgumentException("Destino identico a origem");
        }

        List<Step> steps = new java.util.ArrayList<>(List.of(
                Step.VALIDATE_TARGET,
                Step.FREEZE_WRITES,
                Step.EXPORT_SOURCE_BY_TENANT_ID,
                Step.PROVISION_TARGET,
                Step.APPLY_MIGRATIONS_BY_ROLLOUT,
                Step.COPY_DATA_AND_VERIFY_COUNTS,
                Step.VERIFY_RLS_ISOLATION,
                Step.SWITCH_CATALOG_RESOLUTION));
        if (from == IsolationMode.SHARED) {
            steps.add(Step.ERASE_SOURCE_BY_TENANT_ID_AND_SHRED_KEY);
        } else {
            steps.add(Step.DECOMMISSION_SOURCE_SCHEMA);
        }
        steps.add(Step.UNFREEZE_WRITES);
        steps.add(Step.RECORD_EVIDENCE);
        return new Plan(tenantId, from, to, sourceSchema, sourceCluster,
                targetSchema, targetCluster, steps);
    }
}
