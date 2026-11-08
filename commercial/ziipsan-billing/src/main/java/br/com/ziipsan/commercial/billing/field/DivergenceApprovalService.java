package br.com.ziipsan.commercial.billing.field;

import br.com.ziipsan.commercial.billingrules.FieldBillResult;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Aprovacao da divergencia de campo e correcao por nova versao (ED-19),
 * sobre a permissao e a disciplina da P2.20:
 *
 * <ul>
 *   <li>so aprova quem tem a permissao de retificacao/manutencao de contas
 *       da P2.20 — aqui nomeada pelo recurso:acao
 *       {@value #APPROVE_PERMISSION} (catalogo da P0.13; a amarracao ao
 *       catalogo do tenant e da integracao);</li>
 *   <li>aprovador diferente de quem submeteu/pediu (a disciplina de
 *       aprovacao por outra pessoa do motor de OS que a P2.20 usa);</li>
 *   <li><b>dupla acima do limite:</b> divergencia cujo |delta| do total
 *       supera o limite configurado exige um SEGUNDO aprovador, distinto
 *       do primeiro e tambem com a permissao;</li>
 *   <li>aprovada, a correcao e uma NOVA VERSAO da conta
 *       ({@link FieldBillPorts.BillCorrectionPort}) com os valores
 *       recalculados pelo servidor — a conta impressa em campo NAO e
 *       alterada (o registro guarda o impresso intacto e a versao sobe).</li>
 * </ul>
 */
public final class DivergenceApprovalService {

    /** Permissao (recurso:acao) que habilita aprovar divergencia de campo. */
    public static final String APPROVE_PERMISSION = "billing:approve-field-divergence";

    /** Chave do parametro do limite da dupla aprovacao. */
    public static final String DOUBLE_APPROVAL_LIMIT_KEY = "bill.field_divergence_double_approval_limit";

    private final FieldBillPorts.FieldBillStore store;
    private final FieldBillPorts.BillCorrectionPort corrections;

    public DivergenceApprovalService(FieldBillPorts.FieldBillStore store,
                                     FieldBillPorts.BillCorrectionPort corrections) {
        this.store = Objects.requireNonNull(store, "porta de persistencia e obrigatoria");
        this.corrections = Objects.requireNonNull(corrections, "porta de correcao e obrigatoria");
    }

    /**
     * @param submittedBy quem submeteu a conta (o leiturista/origem do pedido)
     * @param firstApprover primeiro aprovador (obrigatorio)
     * @param firstApproverPermissions permissoes do primeiro aprovador
     * @param secondApprover segundo aprovador (exigido acima do limite)
     * @param secondApproverPermissions permissoes do segundo aprovador
     * @param doubleApprovalLimit limite do |delta| para exigir dupla
     */
    public record Approval(UUID tenantId, UUID taskId, UUID submittedBy,
                           UUID firstApprover, Set<String> firstApproverPermissions,
                           UUID secondApprover, Set<String> secondApproverPermissions,
                           BigDecimal doubleApprovalLimit, String reason) {

        public Approval {
            Objects.requireNonNull(tenantId, "tenant da aprovacao e obrigatorio");
            Objects.requireNonNull(taskId, "task_id da aprovacao e obrigatorio");
            Objects.requireNonNull(firstApprover, "primeiro aprovador e obrigatorio");
            firstApproverPermissions = firstApproverPermissions == null
                    ? Set.of() : Set.copyOf(firstApproverPermissions);
            secondApproverPermissions = secondApproverPermissions == null
                    ? Set.of() : Set.copyOf(secondApproverPermissions);
            Objects.requireNonNull(doubleApprovalLimit, "limite da dupla aprovacao e obrigatorio");
            if (doubleApprovalLimit.signum() < 0) {
                throw new IllegalArgumentException("Limite da dupla aprovacao negativo: " + doubleApprovalLimit);
            }
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("Motivo da aprovacao da divergencia e obrigatorio");
            }
        }
    }

    /** Aprova e corrige por nova versao. Recusas sao explicitas, sem efeito parcial. */
    public FieldBillRecord approveAndCorrect(Approval approval) {
        Objects.requireNonNull(approval, "aprovacao e obrigatoria");
        FieldBillRecord record = store.findByTask(approval.tenantId(), approval.taskId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Sem conta de campo para a task " + approval.taskId()));
        if (record.status() != FieldBillStatus.DIVERGENT_QUEUED) {
            throw new IllegalStateException("Conta da task " + approval.taskId()
                    + " nao esta em divergencia (situacao: " + record.status() + ")");
        }
        requireApprover(approval.firstApprover(), approval.firstApproverPermissions(),
                approval.submittedBy(), "primeiro");
        BigDecimal delta = record.serverRecalc().total().subtract(record.printed().total()).abs();
        if (delta.compareTo(approval.doubleApprovalLimit()) > 0) {
            if (approval.secondApprover() == null) {
                throw new IllegalStateException("Divergencia de " + delta.toPlainString()
                        + " acima do limite " + approval.doubleApprovalLimit().toPlainString()
                        + " exige SEGUNDO aprovador (dupla acima do limite, P2.20)");
            }
            if (approval.secondApprover().equals(approval.firstApprover())) {
                throw new IllegalStateException("A dupla aprovacao exige duas pessoas distintas");
            }
            requireApprover(approval.secondApprover(), approval.secondApproverPermissions(),
                    approval.submittedBy(), "segundo");
        }

        UUID approvedBy = approval.secondApprover() != null
                ? approval.secondApprover() : approval.firstApprover();
        int newVersion = corrections.correctByNewVersion(record.tenantId(), record.billId(),
                record.serverRecalc(), approval.submittedBy(), approvedBy, approval.reason());
        if (newVersion <= record.currentBillVersion()) {
            throw new IllegalStateException("A correcao nao produziu versao nova (devolvida: " + newVersion + ")");
        }
        FieldBillResult printedBefore = record.printed();
        FieldBillRecord corrected = record.withStatus(
                FieldBillStatus.CORRECTED_BY_NEW_VERSION, newVersion);
        if (!corrected.printed().fingerprint().equals(printedBefore.fingerprint())) {
            throw new IllegalStateException("A conta impressa foi alterada na correcao — proibido");
        }
        return store.save(corrected);
    }

    private static void requireApprover(UUID approver, Set<String> permissions, UUID submittedBy, String role) {
        if (!permissions.contains(APPROVE_PERMISSION)) {
            throw new IllegalStateException("O " + role + " aprovador nao tem a permissao "
                    + APPROVE_PERMISSION + " (permissao da P2.20)");
        }
        if (approver.equals(submittedBy)) {
            throw new IllegalStateException("Quem submeteu nao aprova a propria divergencia (" + role + " aprovador)");
        }
    }
}
