package br.com.ziipsan.core.stationops;

import java.util.Optional;
import java.util.UUID;

/**
 * Sincronizacao da ronda offline (ED-87): o formulario funciona sem
 * rede porque a execucao nasce e se completa no aparelho sobre o
 * pacote do roteiro; quando o enlace volta, o aparelho envia a
 * execucao e este servico a grava UMA vez — a identidade e a
 * {@code clientExecutionKey} gerada no aparelho, e o reenvio da mesma
 * execucao devolve {@code DUPLICATE} sem gravar de novo e sem erro
 * (o mesmo contrato de reenvio da ingestao da ED-17 e da execucao de
 * campo da ED-20).
 *
 * <p>Execucao sincronizada precisa estar COMPLETED no aparelho:
 * rascunho parcial nao sobe como ronda feita — ele permanece no
 * aparelho ate concluir.</p>
 */
public final class RoundSyncService {

    public interface RoundStorePort {
        Optional<RoundExecution> findByClientKey(UUID tenantId, String clientExecutionKey);
        void save(RoundExecution execution);
    }

    public enum SyncOutcome { ACCEPTED, DUPLICATE }

    public record SyncResult(SyncOutcome outcome, UUID executionId) {}

    private final RoundStorePort store;

    public RoundSyncService(RoundStorePort store) {
        if (store == null) throw new IllegalArgumentException("porta de persistencia da ronda e obrigatoria");
        this.store = store;
    }

    public SyncResult sync(UUID tenantId, RoundExecution execution) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId e obrigatorio");
        if (execution == null) throw new IllegalArgumentException("execucao e obrigatoria");
        if (!execution.tenantId().equals(tenantId))
            throw new IllegalArgumentException("execucao de outro tenant");
        Optional<RoundExecution> existing = store.findByClientKey(tenantId, execution.clientExecutionKey());
        if (existing.isPresent()) {
            return new SyncResult(SyncOutcome.DUPLICATE, existing.get().id());
        }
        if (!execution.isCompleted())
            throw new IllegalStateException("execucao " + execution.clientExecutionKey()
                    + " ainda nao concluida no aparelho: rascunho offline nao sincroniza como ronda feita");
        store.save(execution);
        return new SyncResult(SyncOutcome.ACCEPTED, execution.id());
    }
}
