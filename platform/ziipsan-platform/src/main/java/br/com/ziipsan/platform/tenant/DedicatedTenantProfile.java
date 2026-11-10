package br.com.ziipsan.platform.tenant;

import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

/**
 * P4.6, item 2 do aceite: o perfil operacional de um tenant em BANCO DEDICADO —
 * cluster proprio, workers proprios e janela de lote propria.
 *
 * <p>O ADR-0005 fixa a referencia inicial de porte (acima de 200 mil ligacoes,
 * revisao 17 §5) para um tenant ir para banco dedicado; ESTA classe nao decide
 * o porte nem guarda o limiar — ela regista, por tenant dedicado, os parametros
 * que o isolam operacionalmente dos demais:
 * <ul>
 *   <li>{@code clusterId}: o cluster so dele. {@code "default"} e recusado:
 *       um tenant "dedicado" no cluster compartilhado e uma contradicao que
 *       esconderia a disputa de recursos que o modo existe para evitar.</li>
 *   <li>{@code workerPoolSize} / {@code batchParallelism}: os workers de lote
 *       dele, dimensionados sem disputar o pool dos tenants do schema
 *       compartilhado nem dos de schema proprio.</li>
 *   <li>janela de lote [{@code batchWindowStart}, {@code batchWindowEnd}):
 *       a janela propria de manutencao/lote. Pode cruzar a meia-noite
 *       (ex.: 22:00–02:00); janela de comprimento zero (inicio == fim) e
 *       recusada por ser ambigua entre "nunca" e "sempre".</li>
 * </ul>
 *
 * <p>Estado de integração (declaração da correcção R-P46-08): a regra do
 * cluster próprio ({@link #requireOwnCluster}) É aplicada em produção pelo
 * provisionamento ({@code TenantProvisioningService.requireDedicatedOwnCluster})
 * a todo o pedido DEDICATED. O perfil completo (workers/janela por tenant)
 * ainda NÃO é persistido nem consumido por um escalonador: o pedido de
 * provisionamento não transporta workers/janela e não há tabela de perfil
 * — persisti-lo exige desenho próprio (migration + campos do pedido) e
 * fica como pendência nomeada no relatório da correcção da P4.6. Até lá
 * esta classe é o objecto de valor testado dessa regra, não um registo
 * operacional.
 */
public record DedicatedTenantProfile(UUID tenantId, String clusterId, int workerPoolSize,
                                     int batchParallelism, LocalTime batchWindowStart,
                                     LocalTime batchWindowEnd) {

    /**
     * A regra do cluster próprio, como método reutilizável: é a MESMA
     * validação do construtor do perfil, exposta para o provisionamento a
     * aplicar a pedidos DEDICATED sem precisar dos restantes campos do
     * perfil (correcção R-P46-05 — fonte única, sem duplicar a regra).
     */
    public static void requireOwnCluster(String clusterId) {
        if (clusterId == null || clusterId.isBlank()) {
            throw new IllegalArgumentException("clusterId do tenant dedicado e obrigatorio");
        }
        if ("default".equals(clusterId)) {
            throw new IllegalArgumentException(
                    "Tenant dedicado nao pode viver no cluster 'default' (compartilhado): "
                            + "banco dedicado exige cluster proprio (ADR-0005, item 2 da P4.6)");
        }
    }

    public DedicatedTenantProfile {
        Objects.requireNonNull(tenantId, "tenantId e obrigatorio");
        requireOwnCluster(clusterId);
        if (workerPoolSize < 1) {
            throw new IllegalArgumentException("workerPoolSize deve ser >= 1");
        }
        if (batchParallelism < 1) {
            throw new IllegalArgumentException("batchParallelism deve ser >= 1");
        }
        Objects.requireNonNull(batchWindowStart, "inicio da janela de lote e obrigatorio");
        Objects.requireNonNull(batchWindowEnd, "fim da janela de lote e obrigatorio");
        if (batchWindowStart.equals(batchWindowEnd)) {
            throw new IllegalArgumentException(
                    "Janela de lote de comprimento zero (inicio == fim) e ambigua e e recusada");
        }
    }

    /** A resolucao deste tenant tem de ser DEDICATED e apontar para o cluster deste perfil. */
    public boolean matches(TenantResolution resolution) {
        return resolution != null
                && tenantId.equals(resolution.id())
                && resolution.isolationMode() == IsolationMode.DEDICATED
                && clusterId.equals(resolution.clusterId());
    }

    /** true se o instante cai na janela propria de lote (janela pode cruzar a meia-noite). */
    public boolean inBatchWindow(LocalTime instant) {
        Objects.requireNonNull(instant, "instante e obrigatorio");
        if (batchWindowStart.isBefore(batchWindowEnd)) {
            return !instant.isBefore(batchWindowStart) && instant.isBefore(batchWindowEnd);
        }
        // Janela que cruza a meia-noite: [inicio, 24:00) U [00:00, fim).
        return !instant.isBefore(batchWindowStart) || instant.isBefore(batchWindowEnd);
    }
}
