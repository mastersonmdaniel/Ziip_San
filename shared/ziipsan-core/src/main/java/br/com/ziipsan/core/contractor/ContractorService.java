package br.com.ziipsan.core.contractor;

import br.com.ziipsan.core.certification.governance.GovernanceAccess;
import br.com.ziipsan.core.certification.governance.GovernanceAccessDeniedException;
import br.com.ziipsan.core.certification.governance.GovernanceStore;
import br.com.ziipsan.core.certification.governance.GovernanceValidationException;
import br.com.ziipsan.core.certification.governance.SupplierRecord;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Empreiteiras terceirizadas (ED-37; SG-492 a SG-496).
 *
 * <p>Regras centrais:
 * <ul>
 *   <li>SG-492: a empreiteira E um fornecedor da P3.31 de tipo CONTRACTOR;
 *       o perfil desta tarefa apenas o ESTENDE (sem razao/documento/contato
 *       duplicados, sem cadastro paralelo);</li>
 *   <li>SG-493: contrato exige empreiteira cadastrada como tal neste tenant;</li>
 *   <li>SG-494: penalidades so existem como clausulas PARAMETRIZADAS por
 *       contrato — nenhuma clausula/percentual padrao e criado (o catalogo
 *       juridico de fabrica da P3.31 ficou vazio por desenho);</li>
 *   <li>SG-495/SG-496: a medicao nasce das OS EXECUTADAS pela empreiteira
 *       (porta sobre a ED-36/P1.31), dentro da vigencia do contrato e do
 *       periodo, sem OS repetida em medicao viva; a aprovacao (so APPROVER)
 *       congela bruto, deducoes das penalidades aplicadas vinculadas e
 *       liquido, recusando liquido negativo.</li>
 * </ul>
 * Escrita deny-by-default, como na P3.31/P3.41: so EDITOR/APPROVER gravam;
 * aprovacao de medicao e relevancia de penalidade exigem APPROVER.
 */
public final class ContractorService {
    private final GovernanceStore governance;
    private final ContractorStore store;
    private final Measurement.ExecutedOrdersPort executedOrders;

    public ContractorService(GovernanceStore governance, ContractorStore store, Measurement.ExecutedOrdersPort executedOrders) {
        this.governance = governance;
        this.store = store;
        this.executedOrders = executedOrders;
    }

    private static void write(GovernanceAccess a, String op) {
        if (a != GovernanceAccess.EDITOR && a != GovernanceAccess.APPROVER)
            throw new GovernanceAccessDeniedException(op, "escrita exige EDITOR ou APPROVER declarado");
    }
    private static void approve(GovernanceAccess a, String op) {
        if (a != GovernanceAccess.APPROVER)
            throw new GovernanceAccessDeniedException(op, "APPROVER", "operacao exige APPROVER");
    }
    private void trail(UUID t, String action, String detail, UUID actor) { store.audit(t, action, detail, actor); }

    /** Fornecedor CONTRACTOR da P3.31 neste tenant, ou recusa nomeada. */
    private SupplierRecord requireContractorSupplier(UUID tenantId, UUID supplierId) {
        SupplierRecord s = governance.suppliers(tenantId).stream()
                .filter(x -> x.id().equals(supplierId)).findFirst()
                .orElseThrow(() -> new GovernanceValidationException("CONTRACTOR_SUPPLIER_NOT_FOUND",
                        "fornecedor inexistente neste tenant no cadastro da P3.31: sem cadastro paralelo (ED-37 le a P3.31)"));
        if (s.type() != SupplierRecord.Type.CONTRACTOR)
            throw new GovernanceValidationException("SUPPLIER_NOT_CONTRACTOR",
                    "fornecedor da P3.31 nao e do tipo CONTRACTOR (empreiteira): tipo atual " + s.type());
        return s;
    }

    // ---- SG-492: cadastro de empreiteira como extensao da P3.31 ----
    public ContractorProfile registerContractor(GovernanceAccess a, ContractorProfile p, UUID actor) {
        write(a, "registerContractor");
        requireContractorSupplier(p.tenantId(), p.supplierId());
        if (store.profileBySupplier(p.tenantId(), p.supplierId()).isPresent())
            throw new GovernanceValidationException("CONTRACTOR_PROFILE_DUPLICATE",
                    "empreiteira ja tem perfil de extensao neste tenant: um fornecedor, um perfil (sem duplicar)");
        store.saveProfile(p);
        trail(p.tenantId(), "CONTRACTOR_PROFILE", "supplier=" + p.supplierId(), actor);
        return p;
    }

    // ---- SG-493: contratos ----
    public ContractorContract registerContract(GovernanceAccess a, ContractorContract c, UUID actor) {
        write(a, "registerContract");
        requireContractorSupplier(c.tenantId(), c.contractorSupplierId());
        if (store.profileBySupplier(c.tenantId(), c.contractorSupplierId()).isEmpty())
            throw new GovernanceValidationException("CONTRACTOR_PROFILE_REQUIRED",
                    "contrato exige o perfil de empreiteira (SG-492) cadastrado antes");
        boolean numberTaken = store.contracts(c.tenantId()).stream()
                .anyMatch(x -> x.contractNumber().equals(c.contractNumber()) && !x.id().equals(c.id()));
        if (numberTaken)
            throw new GovernanceValidationException("CONTRACT_NUMBER_DUPLICATE", "numero de contrato repetido neste tenant");
        store.saveContract(c);
        trail(c.tenantId(), "CONTRACTOR_CONTRACT", "number=" + c.contractNumber(), actor);
        return c;
    }

    /**
     * Maquina de transicoes de estado do contrato (SG-493): os unicos saltos
     * validos sao DRAFT -> ACTIVE, DRAFT -> CLOSED, ACTIVE -> SUSPENDED,
     * ACTIVE -> CLOSED, SUSPENDED -> ACTIVE e SUSPENDED -> CLOSED.
     * CLOSED e terminal; "transicao" para o proprio estado tambem e recusada.
     * Qualquer outro salto (ex.: CLOSED -> ACTIVE, ACTIVE -> DRAFT,
     * DRAFT -> SUSPENDED) e recusado com CONTRACT_STATUS_TRANSITION_INVALID.
     */
    static boolean contractTransitionAllowed(ContractorContract.Status from, ContractorContract.Status to) {
        if (from == null || to == null || from == to) return false;
        return switch (from) {
            case DRAFT -> to == ContractorContract.Status.ACTIVE || to == ContractorContract.Status.CLOSED;
            case ACTIVE -> to == ContractorContract.Status.SUSPENDED || to == ContractorContract.Status.CLOSED;
            case SUSPENDED -> to == ContractorContract.Status.ACTIVE || to == ContractorContract.Status.CLOSED;
            case CLOSED -> false;
        };
    }

    public ContractorContract changeContractStatus(GovernanceAccess a, UUID tenantId, UUID contractId,
                                                   ContractorContract.Status newStatus, UUID actor) {
        write(a, "changeContractStatus");
        ContractorContract c = requireContract(tenantId, contractId);
        if (!contractTransitionAllowed(c.status(), newStatus))
            throw new GovernanceValidationException("CONTRACT_STATUS_TRANSITION_INVALID",
                    "transicao de estado de contrato recusada: " + c.status() + " -> " + newStatus
                            + " (validas: DRAFT->ACTIVE/CLOSED, ACTIVE->SUSPENDED/CLOSED, SUSPENDED->ACTIVE/CLOSED; CLOSED e terminal)");
        ContractorContract updated = c.withStatus(newStatus);
        store.saveContract(updated);
        trail(tenantId, "CONTRACTOR_CONTRACT_STATUS", "contract=" + contractId + " status=" + newStatus, actor);
        return updated;
    }

    private ContractorContract requireContract(UUID tenantId, UUID contractId) {
        ContractorContract c = store.contract(contractId)
                .orElseThrow(() -> new GovernanceValidationException("CONTRACT_NOT_FOUND", "contrato inexistente"));
        if (!c.tenantId().equals(tenantId))
            throw new GovernanceValidationException("CONTRACT_NOT_FOUND", "contrato inexistente neste tenant");
        return c;
    }

    // ---- SG-494: penalidades parametrizadas por contrato ----
    public Penalties.PenaltyClause registerClause(GovernanceAccess a, Penalties.PenaltyClause clause, UUID actor) {
        write(a, "registerClause");
        requireContract(clause.tenantId(), clause.contractId());
        boolean codeTaken = store.clauses(clause.contractId()).stream()
                .anyMatch(x -> x.code().equals(clause.code()) && !x.id().equals(clause.id()));
        if (codeTaken)
            throw new GovernanceValidationException("PENALTY_CODE_DUPLICATE", "codigo de clausula repetido neste contrato");
        store.saveClause(clause);
        trail(clause.tenantId(), "PENALTY_CLAUSE", "contract=" + clause.contractId() + " code=" + clause.code(), actor);
        return clause;
    }

    /**
     * Aplica uma clausula a uma ocorrencia: o valor e CALCULADO da clausula
     * parametrizada (nunca digitado livre) e congelado na aplicacao.
     * Clausula percentual sobre medicao exige a medicao vinculada, do mesmo
     * contrato e tenant; em QUALQUER base, um measurementId declarado e
     * sempre conferido (existe, mesmo contrato e tenant) antes de gravar.
     */
    public Penalties.PenaltyApplication applyPenalty(GovernanceAccess a, UUID tenantId, UUID contractId, UUID clauseId,
                                                     UUID measurementIdOrNull, LocalDate occurredOn, String description,
                                                     int days, UUID actor) {
        write(a, "applyPenalty");
        ContractorContract contract = requireContract(tenantId, contractId);
        Penalties.PenaltyClause clause = store.clause(clauseId)
                .filter(x -> x.contractId().equals(contractId) && x.tenantId().equals(tenantId))
                .orElseThrow(() -> new GovernanceValidationException("PENALTY_CLAUSE_NOT_FOUND", "clausula inexistente neste contrato"));
        BigDecimal measurementGross = null;
        if (clause.basis() == Penalties.Basis.PERCENT_OF_MEASUREMENT && measurementIdOrNull == null)
            throw new GovernanceValidationException("PENALTY_MEASUREMENT_REQUIRED", "clausula percentual sobre medicao exige a medicao vinculada");
        if (measurementIdOrNull != null) {
            // Vinculo declarado e SEMPRE conferido, em qualquer base de
            // calculo (nao so PERCENT_OF_MEASUREMENT): medicao inexistente,
            // de outro contrato ou de outro tenant recusa a aplicacao —
            // o nucleo nao grava referencia que o FK do banco barraria.
            Measurement.ContractorMeasurement m = store.measurement(measurementIdOrNull)
                    .filter(x -> x.contractId().equals(contractId) && x.tenantId().equals(tenantId))
                    .orElseThrow(() -> new GovernanceValidationException("MEASUREMENT_NOT_FOUND", "medicao vinculada inexistente neste contrato"));
            measurementGross = m.grossAmount();
        }
        BigDecimal amount = clause.compute(contract.totalValue(), measurementGross, days);
        Penalties.PenaltyApplication application = new Penalties.PenaltyApplication(UUID.randomUUID(), tenantId, contractId,
                clauseId, measurementIdOrNull, occurredOn, description, days, amount,
                Penalties.PenaltyApplication.Status.APPLIED, null);
        store.saveApplication(application);
        trail(tenantId, "PENALTY_APPLIED", "clause=" + clause.code() + " amount=" + amount, actor);
        return application;
    }

    /** Relevamento (waive) de penalidade: so APPROVER, com motivo, e uma
     *  unica vez — aplicacao ja relevada (WAIVED) nao pode ser relevada de
     *  novo (PENALTY_ALREADY_WAIVED, guarda tambem no record). */
    public Penalties.PenaltyApplication waivePenalty(GovernanceAccess a, UUID tenantId, UUID applicationId,
                                                     String reason, UUID actor) {
        approve(a, "waivePenalty");
        Penalties.PenaltyApplication current = store.application(applicationId)
                .filter(x -> x.tenantId().equals(tenantId))
                .orElseThrow(() -> new GovernanceValidationException("PENALTY_APPLICATION_NOT_FOUND", "aplicacao de penalidade inexistente neste tenant"));
        if (current.status() == Penalties.PenaltyApplication.Status.WAIVED)
            throw new GovernanceValidationException("PENALTY_ALREADY_WAIVED",
                    "aplicacao de penalidade ja relevada (WAIVED): relevamento nao se repete");
        Penalties.PenaltyApplication waived = current.waived(reason);
        store.saveApplication(waived);
        trail(tenantId, "PENALTY_WAIVED", "application=" + applicationId, actor);
        return waived;
    }

    // ---- SG-495/SG-496: medicao ligada as OS executadas (ED-36) ----
    /**
     * Constroi a medicao do periodo a partir das OS executadas pela
     * empreiteira (porta da ED-36). Cada OS vira um item com quantidade e
     * preco declarados pelo chamador em {@code pricingByOrderId}; OS sem
     * preco declarado, fora da vigencia do contrato ou ja presente em
     * medicao viva do contrato recusam a medicao inteira (sem medicao
     * parcial silenciosa).
     */
    public Measurement.ContractorMeasurement buildMeasurement(GovernanceAccess a, UUID tenantId, UUID contractId,
                                                              LocalDate periodStart, LocalDate periodEnd,
                                                              Map<UUID, Measurement.MeasurementItem> pricingByOrderId, UUID actor) {
        write(a, "buildMeasurement");
        if (executedOrders == null)
            throw new GovernanceValidationException("EXECUTED_ORDERS_PORT_NOT_WIRED", "porta de OS executadas da ED-36 nao fiada");
        if (periodStart == null || periodEnd == null || periodEnd.isBefore(periodStart))
            throw new GovernanceValidationException("MEASUREMENT_PERIOD_INVALID", "periodo da medicao invalido");
        ContractorContract contract = requireContract(tenantId, contractId);
        if (contract.status() != ContractorContract.Status.ACTIVE)
            throw new GovernanceValidationException("CONTRACT_NOT_ACTIVE", "medicao exige contrato ACTIVE (atual: " + contract.status() + ")");
        List<Measurement.ExecutedOrder> executed = executedOrders.ordersExecutedBy(tenantId, contract.contractorSupplierId(), periodStart, periodEnd);
        if (executed == null || executed.isEmpty())
            throw new GovernanceValidationException("MEASUREMENT_EMPTY", "nenhuma OS executada pela empreiteira no periodo (ED-36)");
        Set<UUID> alreadyMeasured = store.liveMeasurements(contractId).stream()
                .flatMap(m -> m.items().stream().map(Measurement.MeasurementItem::orderId)).collect(Collectors.toSet());
        List<Measurement.MeasurementItem> items = new java.util.ArrayList<>();
        Set<UUID> seenInBatch = new java.util.HashSet<>();
        for (Measurement.ExecutedOrder o : executed) {
            if (!o.tenantId().equals(tenantId) || !o.contractorSupplierId().equals(contract.contractorSupplierId()))
                throw new GovernanceValidationException("EXECUTED_ORDER_MISMATCH", "porta devolveu OS de outro tenant/empreiteira: " + o.orderId());
            LocalDate executedDate = o.executedAt().toLocalDate();
            if (executedDate.isBefore(periodStart) || executedDate.isAfter(periodEnd))
                throw new GovernanceValidationException("EXECUTED_ORDER_OUTSIDE_PERIOD", "OS " + o.orderId() + " executada fora do periodo da medicao");
            if (!contract.covers(executedDate))
                throw new GovernanceValidationException("EXECUTED_ORDER_OUTSIDE_CONTRACT", "OS " + o.orderId() + " executada fora da vigencia do contrato");
            if (alreadyMeasured.contains(o.orderId()))
                throw new GovernanceValidationException("ORDER_ALREADY_MEASURED", "OS " + o.orderId() + " ja esta em medicao viva deste contrato: dupla medicao recusada");
            if (!seenInBatch.add(o.orderId()))
                throw new GovernanceValidationException("ORDER_ALREADY_MEASURED", "OS " + o.orderId() + " devolvida em duplicidade pela porta NESTA chamada: dupla medicao recusada");
            Measurement.MeasurementItem item = pricingByOrderId == null ? null : pricingByOrderId.get(o.orderId());
            if (item == null)
                throw new GovernanceValidationException("MEASUREMENT_PRICE_MISSING", "OS executada sem quantidade/preco declarados para a medicao: " + o.orderId());
            if (!item.orderId().equals(o.orderId()))
                throw new GovernanceValidationException("MEASUREMENT_PRICE_MISMATCH", "item de preco nao corresponde a OS " + o.orderId());
            items.add(item);
        }
        Measurement.ContractorMeasurement measurement = new Measurement.ContractorMeasurement(UUID.randomUUID(), tenantId,
                contractId, contract.contractorSupplierId(), periodStart, periodEnd, items, Measurement.Status.DRAFT);
        store.saveMeasurement(measurement);
        trail(tenantId, "MEASUREMENT_BUILT", "contract=" + contractId + " gross=" + measurement.grossAmount(), actor);
        return measurement;
    }

    public Measurement.ContractorMeasurement submitMeasurement(GovernanceAccess a, UUID tenantId, UUID measurementId, UUID actor) {
        write(a, "submitMeasurement");
        return transition(tenantId, measurementId, Measurement.Status.DRAFT, Measurement.Status.SUBMITTED, "MEASUREMENT_SUBMITTED", actor);
    }

    public Measurement.ContractorMeasurement rejectMeasurement(GovernanceAccess a, UUID tenantId, UUID measurementId, UUID actor) {
        approve(a, "rejectMeasurement");
        return transition(tenantId, measurementId, Measurement.Status.SUBMITTED, Measurement.Status.REJECTED, "MEASUREMENT_REJECTED", actor);
    }

    /** Resultado da aprovacao: bruto, deducoes (penalidades aplicadas vinculadas) e liquido. */
    public record ApprovalResult(BigDecimal gross, BigDecimal deductions, BigDecimal net) {}

    public ApprovalResult approveMeasurement(GovernanceAccess a, UUID tenantId, UUID measurementId, UUID actor) {
        approve(a, "approveMeasurement");
        Measurement.ContractorMeasurement m = requireMeasurement(tenantId, measurementId);
        if (m.status() != Measurement.Status.SUBMITTED)
            throw new GovernanceValidationException("MEASUREMENT_NOT_SUBMITTED", "aprovacao exige medicao SUBMITTED (atual: " + m.status() + ")");
        List<Penalties.PenaltyApplication> applications = store.applicationsOfMeasurement(measurementId);
        BigDecimal net = m.netAmount(applications);
        if (net.signum() < 0)
            throw new GovernanceValidationException("MEASUREMENT_NET_NEGATIVE",
                    "penalidades vinculadas (" + m.grossAmount().subtract(net) + ") excedem o bruto da medicao (" + m.grossAmount() + "): aprovacao recusada");
        BigDecimal deductions = m.grossAmount().subtract(net);
        store.saveMeasurement(m.withStatus(Measurement.Status.APPROVED));
        trail(tenantId, "MEASUREMENT_APPROVED", "gross=" + m.grossAmount() + " deductions=" + deductions + " net=" + net, actor);
        return new ApprovalResult(m.grossAmount(), deductions, net);
    }

    private Measurement.ContractorMeasurement transition(UUID tenantId, UUID measurementId, Measurement.Status from,
                                                         Measurement.Status to, String action, UUID actor) {
        Measurement.ContractorMeasurement m = requireMeasurement(tenantId, measurementId);
        if (m.status() != from)
            throw new GovernanceValidationException("MEASUREMENT_TRANSITION_INVALID", "medicao em " + m.status() + " nao pode ir para " + to);
        Measurement.ContractorMeasurement updated = m.withStatus(to);
        store.saveMeasurement(updated);
        trail(tenantId, action, "measurement=" + measurementId, actor);
        return updated;
    }

    private Measurement.ContractorMeasurement requireMeasurement(UUID tenantId, UUID measurementId) {
        Measurement.ContractorMeasurement m = store.measurement(measurementId)
                .orElseThrow(() -> new GovernanceValidationException("MEASUREMENT_NOT_FOUND", "medicao inexistente"));
        if (!m.tenantId().equals(tenantId))
            throw new GovernanceValidationException("MEASUREMENT_NOT_FOUND", "medicao inexistente neste tenant");
        return m;
    }

    // ---- Leituras (item 2.17 SAMAE, parte de empreiteiras: consulta completa) ----
    public List<ContractorProfile> contractors(UUID tenantId) { return store.profiles(tenantId); }
    public List<ContractorContract> contracts(UUID tenantId) { return store.contracts(tenantId); }
    public List<Penalties.PenaltyClause> clauses(UUID contractId) { return store.clauses(contractId); }
    public List<Penalties.PenaltyApplication> applications(UUID contractId) { return store.applications(contractId); }
    public List<Measurement.ContractorMeasurement> measurements(UUID tenantId) { return store.measurements(tenantId); }
}
