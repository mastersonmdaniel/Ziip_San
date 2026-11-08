package br.com.ziipsan.commercial.billing.crisis;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Fiacao de aplicacao da ED-91 por PORTAS (nenhum envio/OS nasce aqui):
 * - medida excepcional aplicada por OS de lote (BatchOrderPort - nucleo
 *   de OS da P1.31/ED-36 na integracao);
 * - comunicacao do rodizio SO pelos canais existentes: atendimento da
 *   ED-35 (SacNoticePort) e aviso da ED-54/ED-41 (ManeuverNoticePort).
 */
public final class CrisisApplicationService {

    public interface BatchOrderPort { UUID openBatch(String purpose, List<UUID> measureIds); }
    public interface SacNoticePort { void notifyRotation(RotationPlan plan); }
    public interface ManeuverNoticePort { void notifyRotation(RotationPlan plan); }

    private final BatchOrderPort batchOrders;
    private final SacNoticePort sacNotices;
    private final ManeuverNoticePort maneuverNotices;

    public CrisisApplicationService(BatchOrderPort batchOrders, SacNoticePort sacNotices,
                                    ManeuverNoticePort maneuverNotices) {
        this.batchOrders = Objects.requireNonNull(batchOrders, "porta de lote obrigatoria");
        this.sacNotices = Objects.requireNonNull(sacNotices, "porta ED-35 obrigatoria");
        this.maneuverNotices = Objects.requireNonNull(maneuverNotices, "porta ED-54/ED-41 obrigatoria");
    }

    /** Aplica a medida por OS de lote e devolve o id do lote registrado nela. */
    public UUID applyMeasure(ExceptionalMeasure measure) {
        Objects.requireNonNull(measure, "medida obrigatoria");
        if (measure.status() != ExceptionalMeasure.Status.ACTIVE)
            throw new IllegalStateException("medida nao ativa nao se aplica por lote: " + measure.status());
        UUID batchId = batchOrders.openBatch("EXCEPTIONAL_MEASURE", List.of(measure.id()));
        measure.applyByBatchOrder(batchId);
        return batchId;
    }

    /** Comunica o rodizio pelos DOIS canais existentes (ED-35 e ED-54). */
    public void communicateRotation(RotationPlan plan) {
        Objects.requireNonNull(plan, "plano obrigatorio");
        sacNotices.notifyRotation(plan);
        maneuverNotices.notifyRotation(plan);
    }
}
