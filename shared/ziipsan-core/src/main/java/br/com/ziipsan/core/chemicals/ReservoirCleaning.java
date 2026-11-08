package br.com.ziipsan.core.chemicals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Reservatorios e o plano de limpeza e desinfeccao (ED-89, OPE-07).
 *
 * <p>Periodicidade: {@code ops.reservoir_cleaning_interval_months} —
 * valor da AGENCIA, sem padrao de norma confirmado (texto do
 * acceptance): o plano so nasce com o parametro vigente do tenant
 * (ver {@link #createPlan}); nenhum intervalo vive nesta classe.
 *
 * <p>Execucao: OS recorrente pelo nucleo de OS (porta
 * {@link ChemPorts.CleaningOrderPort}), LAUDO obrigatorio para
 * encerrar, produto usado (item da ED-58/ED-89) e VOLUME DESCARTADO
 * em m3 — unidade declarada nesta entrega para o volume, que alimenta
 * o balanco operacional da ED-85 como volume autorizado nao faturado
 * (uso operacional "limpeza de reservatorio" da ficha de perdas).
 *
 * <p>Registro PUBLICO: as execucoes encerradas com laudo formam o
 * registro consultavel no portal (Decreto 5.440/2005, Anexo, arts. 16
 * e 17; Portaria GM/MS 888/2021, art. 14, XVII) e a fonte do relatorio
 * anual da ED-79 — ver {@link ChemReports}.
 */
public final class ReservoirCleaning {
    private ReservoirCleaning() {}

    public record Reservoir(UUID tenantId, String code, String name, String supplySystem, boolean active) {
        public Reservoir {
            Objects.requireNonNull(tenantId, "tenant e obrigatorio");
            if (code == null || code.isBlank()) throw new IllegalArgumentException("codigo do reservatorio e obrigatorio");
            if (name == null || name.isBlank()) throw new IllegalArgumentException("nome do reservatorio e obrigatorio: " + code);
            if (supplySystem == null || supplySystem.isBlank()) throw new IllegalArgumentException("sistema de abastecimento do reservatorio e obrigatorio: " + code);
        }
    }

    public record CleaningPlan(UUID tenantId, String reservoirCode, int intervalMonths,
                               LocalDate anchorOn, LocalDate lastCleaningOn, LocalDate nextDueOn) {
        public CleaningPlan {
            Objects.requireNonNull(tenantId, "tenant e obrigatorio");
            if (reservoirCode == null || reservoirCode.isBlank()) throw new IllegalArgumentException("reservatorio do plano e obrigatorio");
            if (intervalMonths <= 0) throw new IllegalArgumentException("intervalo do plano deve ser positivo (valor da agencia)");
            Objects.requireNonNull(anchorOn, "data-base do plano e obrigatoria");
            Objects.requireNonNull(nextDueOn, "proximo vencimento do plano e obrigatorio");
        }
        public boolean dueOn(LocalDate day) { return !day.isBefore(nextDueOn); }
        public boolean overdueOn(LocalDate day) { return day.isAfter(nextDueOn); }
        public CleaningPlan advance(LocalDate executedOn) {
            return new CleaningPlan(tenantId, reservoirCode, intervalMonths, anchorOn, executedOn, executedOn.plusMonths(intervalMonths));
        }
    }

    /**
     * Nasce o plano de um reservatorio: o intervalo vem do parametro do
     * tenant (agencia) — ausencia recusa com o nome da chave.
     * Sem limpeza anterior, o primeiro vencimento e base + intervalo.
     */
    public static CleaningPlan createPlan(ChemParameters parameters, UUID tenantId, String reservoirCode,
                                          LocalDate anchorOn, LocalDate lastCleaningOnOrNull) {
        int months = ChemParameters.requireReservoirIntervalMonths(parameters.reservoirCleaningIntervalMonths(tenantId));
        LocalDate base = lastCleaningOnOrNull != null ? lastCleaningOnOrNull : anchorOn;
        return new CleaningPlan(tenantId, reservoirCode, months, anchorOn, lastCleaningOnOrNull, base.plusMonths(months));
    }

    public record CleaningExecution(UUID tenantId, String reservoirCode, UUID orderId, LocalDate executedOn,
                                    String reportRef, String productItemCode, BigDecimal productQty,
                                    BigDecimal discardedVolumeM3) {
        public CleaningExecution {
            Objects.requireNonNull(tenantId, "tenant e obrigatorio");
            if (reservoirCode == null || reservoirCode.isBlank()) throw new IllegalArgumentException("reservatorio da execucao e obrigatorio");
            Objects.requireNonNull(orderId, "OS da limpeza e obrigatoria");
            Objects.requireNonNull(executedOn, "data da execucao e obrigatoria");
            if (reportRef == null || reportRef.isBlank())
                throw new ChemExceptions.ValidationException("CLEANING_REPORT_REQUIRED",
                        "execucao de limpeza so encerra com o laudo registrado: " + reservoirCode);
            if (productItemCode == null || productItemCode.isBlank())
                throw new IllegalArgumentException("produto usado na limpeza e obrigatorio");
            if (productQty == null || productQty.signum() <= 0)
                throw new IllegalArgumentException("quantidade do produto usado deve ser positiva");
            if (discardedVolumeM3 == null || discardedVolumeM3.signum() < 0)
                throw new IllegalArgumentException("volume descartado (m3) nao pode ser nulo/negativo");
        }
    }

    /**
     * Gera a OS recorrente dos planos vencidos, UMA vez por
     * (reservatorio, vencimento) — idempotente pelo store.
     * Devolve os ids das OS criadas nesta rodada.
     */
    public static java.util.List<UUID> generateDueOrders(ChemStore store, ChemPorts.CleaningOrderPort orders,
                                                         UUID tenantId, LocalDate today) {
        java.util.List<UUID> created = new java.util.ArrayList<>();
        for (CleaningPlan plan : store.cleaningPlans(tenantId)) {
            if (!plan.dueOn(today)) continue;
            Reservoir reservoir = store.reservoir(tenantId, plan.reservoirCode()).orElse(null);
            if (reservoir == null || !reservoir.active()) continue;
            if (store.cleaningOrderGenerated(tenantId, plan.reservoirCode(), plan.nextDueOn())) continue;
            UUID orderId = orders.openCleaningOrder(tenantId, plan.reservoirCode(), plan.nextDueOn(),
                    "limpeza e desinfeccao programada do reservatorio (ED-89/OPE-07)");
            store.markCleaningOrderGenerated(tenantId, plan.reservoirCode(), plan.nextDueOn(), orderId);
            created.add(orderId);
        }
        return created;
    }

    /**
     * Registra a execucao (com laudo) e avanca o plano para o proximo
     * vencimento. Validacoes minimas de fronteira (correcao R4 da
     * revisao da ED-89), todas sobre registros/portas existentes:
     * <ul>
     *   <li>o PRODUTO usado tem de ser produto quimico CADASTRADO e
     *       ativo do tenant ({@link ChemStore#product}) — sem isso, o
     *       registro publico nasceria de produto inexistente;</li>
     *   <li>a OS tem de ser a GERADA para este reservatorio e para o
     *       vencimento vigente do plano, com o id casado
     *       ({@link ChemStore#cleaningOrderId}, gerada por
     *       {@link #generateDueOrders}) — UUID qualquer nao encerra
     *       limpeza.</li>
     * </ul>
     */
    public static CleaningPlan recordExecution(ChemStore store, CleaningExecution execution) {
        CleaningPlan plan = store.cleaningPlan(execution.tenantId(), execution.reservoirCode())
                .orElseThrow(() -> new ChemExceptions.NotFoundException("CLEANING_PLAN_NOT_FOUND",
                        "sem plano de limpeza para o reservatorio " + execution.reservoirCode()));
        ChemProduct product = store.product(execution.tenantId(), execution.productItemCode())
                .orElseThrow(() -> new ChemExceptions.NotFoundException("CHEM_PRODUCT_NOT_FOUND",
                        "execucao de limpeza com produto nao cadastrado: " + execution.productItemCode()));
        if (!product.active())
            throw new ChemExceptions.ValidationException("PRODUCT_INACTIVE",
                    "execucao de limpeza com produto inativo: " + execution.productItemCode());
        UUID generatedOrderId = store.cleaningOrderId(execution.tenantId(), execution.reservoirCode(), plan.nextDueOn())
                .orElseThrow(() -> new ChemExceptions.ValidationException("CLEANING_ORDER_NOT_GENERATED",
                        "nenhuma OS de limpeza foi gerada para o reservatorio " + execution.reservoirCode()
                                + " no vencimento " + plan.nextDueOn() + ": execucao recusada"));
        if (!generatedOrderId.equals(execution.orderId()))
            throw new ChemExceptions.ValidationException("CLEANING_ORDER_MISMATCH",
                    "OS " + execution.orderId() + " nao e a OS gerada para o reservatorio "
                            + execution.reservoirCode() + " no vencimento " + plan.nextDueOn()
                            + " (gerada: " + generatedOrderId + "): execucao recusada");
        if (plan.lastCleaningOn() != null && execution.executedOn().isBefore(plan.lastCleaningOn()))
            throw new IllegalArgumentException("execucao anterior a ultima limpeza registrada: " + execution.executedOn());
        store.saveCleaningExecution(execution);
        CleaningPlan advanced = plan.advance(execution.executedOn());
        store.saveCleaningPlan(advanced);
        return advanced;
    }
}
