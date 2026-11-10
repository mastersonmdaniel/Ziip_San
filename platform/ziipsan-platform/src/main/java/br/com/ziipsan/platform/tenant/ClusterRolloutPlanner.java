package br.com.ziipsan.platform.tenant;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * P4.6, item 5 do aceite: as migrations sao aplicadas por tenant E POR CLUSTER
 * pelo rollout da P1.14.
 *
 * <p>O {@link TenantMigrationRollout} da P1.14 ja e por tenant (situacao por
 * tenant, quarentena sem parar os demais, canario, paralelismo limitado) e ja
 * agrupa por schema (um schema compartilhado migra uma vez — regra 4 dele).
 * O que lhe faltava era a dimensao CLUSTER: um rollout nao pode misturar
 * tenants de clusters diferentes na mesma corrida, porque cada cluster tem o
 * seu banco, o seu pool de migracao e a sua janela. Este planeador parte a
 * lista de resolucoes por cluster, preservando a ordem de entrada dentro de
 * cada cluster (o chamador ordena por schema_name, como o DatabaseMigrator
 * faz, para o canario de cada cluster ser deterministico), e devolve, por
 * cluster, os alvos no formato que o rollout consome.
 *
 * <p>Estado de integração (declaração da correcção R-P46-08): este
 * planeador é uma função pura, testada, AINDA SEM chamador de produção —
 * o {@code DatabaseMigrator} continua a correr o rollout sem a partição
 * por cluster. Ligá-lo é a pendência nomeada no relatório da correcção da
 * P4.6; até lá o item 5 está entregue ao nível de componente, não de
 * aplicação ligada. O mesmo vale para {@link ClusterPoolRouter} (item 4).
 */
public final class ClusterRolloutPlanner {

    private ClusterRolloutPlanner() {
    }

    /**
     * Agrupa as resolucoes por cluster_id -> lista de (tenant, schema) para o
     * {@link TenantMigrationRollout} desse cluster. Tenants inactivos sao
     * excluidos (o rollout nao migra tenant suspenso/encerrado).
     */
    public static Map<String, List<TenantMigrationRollout.Tenant>> byCluster(
            List<TenantResolution> resolutions) {
        Objects.requireNonNull(resolutions, "resolucoes sao obrigatorias");
        Map<String, List<TenantMigrationRollout.Tenant>> grouped = new LinkedHashMap<>();
        for (TenantResolution resolution : resolutions) {
            Objects.requireNonNull(resolution, "resolucao nao pode ser nula");
            if (!resolution.active()) {
                continue;
            }
            grouped.computeIfAbsent(resolution.clusterId(), k -> new java.util.ArrayList<>())
                    .add(new TenantMigrationRollout.Tenant(resolution.id(), resolution.schema()));
        }
        Map<String, List<TenantMigrationRollout.Tenant>> immutable = new LinkedHashMap<>();
        grouped.forEach((cluster, tenants) -> immutable.put(cluster, List.copyOf(tenants)));
        return Map.copyOf(immutable);
    }
}
