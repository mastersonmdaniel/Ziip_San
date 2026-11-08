package br.com.ziipsan.commercial.billing.field;

import br.com.ziipsan.commercial.billingrules.FieldBillResult;

import java.util.Optional;
import java.util.UUID;

/**
 * Portas do servico de submissao da conta de campo (ED-19). As
 * implementacoes de producao (JPA sobre as tabelas da migration pendente,
 * fila de analise, alertas da Central) vivem na raiz de composicao; aqui —
 * como nas demais tarefas — ficam os contratos e as regras.
 */
public final class FieldBillPorts {

    private FieldBillPorts() {
    }

    /** Persistencia do registro unico por task_id. */
    public interface FieldBillStore {
        Optional<FieldBillRecord> findByTask(UUID tenantId, UUID taskId);

        FieldBillRecord save(FieldBillRecord record);
    }

    /** Fila de divergencias (a mesma fila de analise que a Central mostra). */
    public interface DivergenceQueuePort {
        /**
         * Enfileira a divergencia de uma conta. Devolve o id do item da fila.
         * Chamado UMA vez por conta: o reenvio nao reenfileira.
         */
        UUID enqueue(FieldBillRecord record, String explanationJson);
    }

    /** Alerta de divergencia na Central (painel operacional). */
    public interface CentralAlertPort {
        void divergenceDetected(UUID tenantId, UUID taskId, UUID billId, UUID queueItemId);
    }

    /**
     * Correcao por nova versao (fluxo de retificacao da P2.20): cria a nova
     * versao da cadeia da conta com os valores corrigidos SEM tocar na
     * versao impressa. Devolve o numero da nova versao.
     */
    public interface BillCorrectionPort {
        int correctByNewVersion(UUID tenantId, UUID billId, FieldBillResult correctedValues,
                                UUID requestedBy, UUID approvedBy, String reason);
    }
}
