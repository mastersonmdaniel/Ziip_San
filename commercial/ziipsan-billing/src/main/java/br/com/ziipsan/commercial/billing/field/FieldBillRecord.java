package br.com.ziipsan.commercial.billing.field;

import br.com.ziipsan.commercial.billingrules.FieldBillResult;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro servidor da conta de campo (ED-19): UM por (tenant, task_id) —
 * e essa unicidade que faz o reenvio nao duplicar conta nem NFAg-e (a
 * NFAg-e da P2.6 nasce da conta por BILL + billId, e o billId daqui e
 * estavel por tarefa).
 *
 * @param billId          id da conta criada na primeira submissao aceita
 *                        (nunca muda em reenvio nem em correcao: a correcao
 *                        e nova VERSAO da mesma cadeia de conta, P2.20)
 * @param printed         resultado como impresso em campo (imutavel)
 * @param serverRecalc    resultado recalculado pelo servidor (mesmo modulo)
 * @param currentBillVersion versao vigente da cadeia da conta (1 = a impressa)
 */
public record FieldBillRecord(UUID tenantId, UUID taskId, UUID routeId, UUID billId,
                              FieldBillResult printed, FieldBillResult serverRecalc,
                              FieldBillStatus status, int currentBillVersion,
                              boolean nfageContingency, OffsetDateTime receivedAt) {

    public FieldBillRecord {
        Objects.requireNonNull(tenantId, "tenant do registro de campo e obrigatorio");
        Objects.requireNonNull(taskId, "task_id do registro de campo e obrigatorio");
        Objects.requireNonNull(billId, "billId do registro de campo e obrigatorio");
        Objects.requireNonNull(printed, "resultado impresso e obrigatorio");
        Objects.requireNonNull(status, "situacao do registro e obrigatoria");
        Objects.requireNonNull(receivedAt, "recebimento do registro e obrigatorio");
        if (currentBillVersion < 1) {
            throw new IllegalArgumentException("Versao da conta deve ser >= 1: " + currentBillVersion);
        }
    }

    /** Copia com nova situacao/versao (o impresso nunca e substituido). */
    public FieldBillRecord withStatus(FieldBillStatus newStatus, int newVersion) {
        return new FieldBillRecord(tenantId, taskId, routeId, billId, printed, serverRecalc,
                newStatus, newVersion, nfageContingency, receivedAt);
    }
}
