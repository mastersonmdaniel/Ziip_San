package br.com.ziipsan.commercial.collection.dunning.thirdparty;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cobranca por empresa terceirizada, remunerada por resultado (ED-68).
 *
 * <p>Regras centrais, todas do aceite:
 * <ul>
 *   <li>E2/E3 (gates da cessao): sem contrato de operador (LGPD) registado
 *       para o contrato E sem empresa elegivel como empreiteira da ED-37
 *       (perfil + contrato de empreiteira activo), nenhuma conta e cedida;
 *       porta nao ligada = recusa explicita;</li>
 *   <li>E4/E5: a cessao nasce da seleccao PURA por criterio explicito e
 *       guarda/envia apenas o minimo de dados (whitelist);</li>
 *   <li>dupla cessao: conta com cessao ACTIVA nao e cedida outra vez;</li>
 *   <li>E7: pagamento so e atribuido a empresa DENTRO da janela do
 *       contrato E com acordo valido da P4.1 — fora de qualquer condicao,
 *       nao atribui e regista o motivo;</li>
 *   <li>E8: retirada terminal por pagamento, parcelamento ou fim da
 *       janela, sempre com causa e data;</li>
 *   <li>E9: remuneracao = percentual do contrato x pagamento atribuido,
 *       criada pelo evento, idempotente por evento;</li>
 *   <li>E10: relatorio por empresa derivado das entradas de remuneracao.</li>
 * </ul>
 */
public final class ThirdPartyCollectionService {

    private final ThirdPartyCollectionStore store;
    private final ThirdPartyPorts.ContractorDirectoryPort contractorDirectory;
    private final ThirdPartyPorts.EligibleDebtsPort eligibleDebts;
    private final ThirdPartyPorts.ValidAgreementPort validAgreement;

    public ThirdPartyCollectionService(ThirdPartyCollectionStore store,
                                       ThirdPartyPorts.ContractorDirectoryPort contractorDirectory,
                                       ThirdPartyPorts.EligibleDebtsPort eligibleDebts,
                                       ThirdPartyPorts.ValidAgreementPort validAgreement) {
        if (store == null)
            throw new ThirdPartyCollectionException("STORE_REQUIRED", "servico exige o store");
        this.store = store;
        this.contractorDirectory = contractorDirectory;
        this.eligibleDebts = eligibleDebts;
        this.validAgreement = validAgreement;
    }

    // ---- E1/E2: contrato e registo de operador ----
    public ThirdPartyContract registerContract(ThirdPartyContract contract) {
        boolean numberTaken = store.contracts(contract.tenantId()).stream()
                .anyMatch(c -> c.contractNumber().equals(contract.contractNumber()) && !c.id().equals(contract.id()));
        if (numberTaken)
            throw new ThirdPartyCollectionException("CONTRACT_NUMBER_DUPLICATE",
                    "numero de contrato repetido neste tenant");
        store.saveContract(contract);
        store.audit(contract.tenantId(), "THIRD_PARTY_CONTRACT", "number=" + contract.contractNumber());
        return contract;
    }

    public OperatorContract registerOperatorContract(OperatorContract operator) {
        requireContract(operator.tenantId(), operator.thirdPartyContractId());
        if (store.operatorContractOf(operator.thirdPartyContractId()).isPresent())
            throw new ThirdPartyCollectionException("OPERATOR_CONTRACT_DUPLICATE",
                    "contrato ja tem registo de operador (LGPD): um contrato, um registo");
        store.saveOperatorContract(operator);
        store.audit(operator.tenantId(), "OPERATOR_CONTRACT_REGISTERED",
                "contract=" + operator.thirdPartyContractId() + " ref=" + operator.reference());
        return operator;
    }

    private ThirdPartyContract requireContract(UUID tenantId, UUID contractId) {
        ThirdPartyContract c = store.contract(contractId)
                .orElseThrow(() -> new ThirdPartyCollectionException("CONTRACT_NOT_FOUND", "contrato inexistente"));
        if (!c.tenantId().equals(tenantId))
            throw new ThirdPartyCollectionException("CONTRACT_NOT_FOUND", "contrato inexistente neste tenant");
        return c;
    }

    // ---- E4: seleccao pura por criterio ----
    public List<ThirdPartyPorts.CandidateDebt> selectCandidates(UUID tenantId, AssignmentCriteria criteria, LocalDate asOf) {
        if (eligibleDebts == null)
            throw new ThirdPartyCollectionException("ELIGIBLE_DEBTS_PORT_NOT_WIRED",
                    "porta de dividas elegiveis (P3.1/P4.1) nao ligada: seleccao recusa, nao finge candidatas");
        if (criteria == null || asOf == null)
            throw new ThirdPartyCollectionException("SELECTION_INPUT_REQUIRED",
                    "seleccao exige criterio explicito e data de referencia declarados");
        List<ThirdPartyPorts.CandidateDebt> all = eligibleDebts.candidates(tenantId);
        if (all == null) return List.of();
        return all.stream().filter(c -> criteria.matches(c, asOf)).toList();
    }

    // ---- E2/E3/E4/E5: cessao com gates ----
    /**
     * Cede as contas seleccionadas pelo criterio ao contrato. Gates, nesta
     * ordem, todos com recusa nomeada: contrato existe e esta ACTIVE;
     * contrato de operador (LGPD) registado (E2); porta da ED-37 ligada e
     * empresa elegivel como empreiteira (E3); nenhuma seleccionada ja tem
     * cessao ACTIVA (dupla cessao). Se qualquer gate falha, NENHUMA cessao
     * e criada (sem cessao parcial silenciosa).
     */
    public List<DebtAssignment> assignDebts(UUID tenantId, UUID contractId,
                                            AssignmentCriteria criteria, LocalDate asOf) {
        ThirdPartyContract contract = requireContract(tenantId, contractId);
        if (contract.status() != ThirdPartyContract.Status.ACTIVE)
            throw new ThirdPartyCollectionException("CONTRACT_NOT_ACTIVE",
                    "cessao exige contrato ACTIVE (actual: " + contract.status() + ")");
        var operatorContract = store.operatorContractOf(contractId);
        if (operatorContract.isEmpty())
            throw new ThirdPartyCollectionException("OPERATOR_CONTRACT_NOT_REGISTERED",
                    "sem contrato de operador (LGPD) registado para este contrato: nenhuma cessao antes do registo (E2)");
        if (asOf != null && operatorContract.get().registeredOn().isAfter(asOf))
            throw new ThirdPartyCollectionException("OPERATOR_CONTRACT_REGISTERED_AFTER_ASSIGNMENT",
                    "contrato de operador (LGPD) registado em " + operatorContract.get().registeredOn()
                            + ", depois da data da cessao " + asOf
                            + ": registo tem de ser anterior ou igual ao primeiro envio (E2)");
        requireEligibleContractor(tenantId, contract.companySupplierId());
        List<ThirdPartyPorts.CandidateDebt> selected = selectCandidates(tenantId, criteria, asOf);
        for (ThirdPartyPorts.CandidateDebt c : selected) {
            if (store.activeAssignmentOfDebt(tenantId, c.debtId()).isPresent())
                throw new ThirdPartyCollectionException("DEBT_ALREADY_ASSIGNED",
                        "conta ja cedida e ACTIVA: dupla cessao recusada (divida " + c.debtId() + ")");
        }
        List<DebtAssignment> created = new ArrayList<>();
        for (ThirdPartyPorts.CandidateDebt c : selected) {
            DebtAssignment a = DebtAssignment.active(UUID.randomUUID(), tenantId, contractId, c.toMinimalData());
            store.saveAssignment(a);
            created.add(a);
        }
        store.audit(tenantId, "DEBTS_ASSIGNED", "contract=" + contractId + " count=" + created.size());
        return List.copyOf(created);
    }

    private void requireEligibleContractor(UUID tenantId, UUID companySupplierId) {
        if (contractorDirectory == null)
            throw new ThirdPartyCollectionException("CONTRACTOR_DIRECTORY_PORT_NOT_WIRED",
                    "porta do cadastro de empreiteiras da ED-37 nao ligada: cessao recusa, nao presume elegibilidade");
        ThirdPartyPorts.ContractorDirectoryPort.ContractorStanding standing =
                contractorDirectory.standingOf(tenantId, companySupplierId)
                        .orElseThrow(() -> new ThirdPartyCollectionException("CONTRACTOR_NOT_FOUND_ED37",
                                "empresa sem registo no cadastro de empreiteiras da ED-37 neste tenant"));
        if (!standing.contractorProfilePresent())
            throw new ThirdPartyCollectionException("CONTRACTOR_PROFILE_MISSING_ED37",
                    "empresa sem perfil de empreiteira da ED-37: nao recebe cessao (E3)");
        if (!standing.contractorContractActive())
            throw new ThirdPartyCollectionException("CONTRACTOR_CONTRACT_INACTIVE_ED37",
                    "empresa sem contrato de empreiteira ACTIVO da ED-37: nao recebe cessao (E3)");
    }

    // ---- E6: ida e volta ----
    /** Remessa deterministica das cessoes ACTIVAS do contrato. */
    public RemittanceAndReturn.Remittance buildRemittance(UUID tenantId, UUID contractId, LocalDate generatedOn) {
        requireContract(tenantId, contractId);
        if (generatedOn == null)
            throw new ThirdPartyCollectionException("REMITTANCE_DATE_REQUIRED", "remessa exige a data de geracao declarada");
        List<MinimalDebtorData> entries = store.assignmentsOfContract(contractId).stream()
                .filter(a -> a.status() == DebtAssignment.Status.ACTIVE)
                .map(DebtAssignment::minimalData)
                .sorted(java.util.Comparator.comparing(d -> d.debtId().toString()))
                .toList();
        String content = RemittanceAndReturn.Remittance.contentOf(contractId, generatedOn, entries);
        return new RemittanceAndReturn.Remittance(contractId, generatedOn, entries, content);
    }

    /**
     * Processa uma ocorrencia de retorno (E6): PAYMENT e
     * INSTALLMENT_AGREEMENT retiram a cessao com a causa correspondente
     * (E8); NO_PROGRESS mantem a cessao activa. Ocorrencia sobre cessao
     * inexistente ou ja retirada recusa.
     */
    public DebtAssignment processReturn(UUID tenantId, RemittanceAndReturn.ReturnOccurrence occurrence) {
        if (occurrence == null)
            throw new ThirdPartyCollectionException("RETURN_REQUIRED", "retorno exige a ocorrencia declarada");
        DebtAssignment a = store.assignmentOfDebt(tenantId, occurrence.debtId())
                .orElseThrow(() -> new ThirdPartyCollectionException("RETURN_FOR_UNKNOWN_ASSIGNMENT",
                        "retorno sobre divida sem cessao neste tenant: " + occurrence.debtId()));
        if (a.status() != DebtAssignment.Status.ACTIVE)
            throw new ThirdPartyCollectionException("RETURN_FOR_WITHDRAWN_ASSIGNMENT",
                    "retorno sobre cessao ja retirada (terminal): " + a.id());
        return switch (occurrence.kind()) {
            case PAYMENT -> withdraw(a, DebtAssignment.WithdrawalCause.PAYMENT, occurrence.occurredOn(), "RETURN_PAYMENT");
            case INSTALLMENT_AGREEMENT -> withdraw(a, DebtAssignment.WithdrawalCause.INSTALLMENT_AGREEMENT,
                    occurrence.occurredOn(), "RETURN_INSTALLMENT_AGREEMENT");
            case NO_PROGRESS -> {
                store.audit(tenantId, "RETURN_NO_PROGRESS", "assignment=" + a.id());
                yield a;
            }
        };
    }

    private DebtAssignment withdraw(DebtAssignment a, DebtAssignment.WithdrawalCause cause, LocalDate on, String action) {
        DebtAssignment w = a.withdrawn(cause, on);
        store.saveAssignment(w);
        store.audit(a.tenantId(), action, "assignment=" + a.id() + " cause=" + cause);
        return w;
    }

    // ---- E7/E9: atribuicao do pagamento e remuneracao ----
    /** Resultado da atribuicao: atribuido ou nao, sempre com motivo nomeado. */
    public record AttributionResult(boolean attributed, String reasonCode, RemunerationEntry remuneration) {
    }

    /**
     * Atribui um evento de pagamento a empresa SOMENTE se (a) a divida tem
     * cessao ACTIVA, (b) a data do pagamento cai DENTRO da janela do
     * contrato e (c) existe acordo valido da P4.1. Atribuido: cria a
     * remuneracao (E9) e retira a cessao por pagamento (E8). Nao
     * atribuido: nenhum efeito alem do registo do motivo.
     *
     * <p>Idempotencia (E9): se o evento ja gerou remuneracao, devolve a
     * entrada existente sem criar outra — mesmo que a cessao ja esteja
     * retirada pelo primeiro processamento.
     */
    public AttributionResult attributePayment(PaymentEvent event) {
        if (event == null)
            throw new ThirdPartyCollectionException("PAYMENT_EVENT_REQUIRED", "atribuicao exige o evento de pagamento");
        var existing = store.remunerationOfEvent(event.tenantId(), event.eventId());
        if (existing.isPresent()) {
            return new AttributionResult(true, "ALREADY_RECORDED_IDEMPOTENT", existing.get());
        }
        var assignmentOpt = store.assignmentOfDebt(event.tenantId(), event.debtId());
        if (assignmentOpt.isEmpty()) {
            store.audit(event.tenantId(), "PAYMENT_NOT_ATTRIBUTED", "event=" + event.eventId() + " reason=DEBT_NOT_ASSIGNED");
            return new AttributionResult(false, "DEBT_NOT_ASSIGNED", null);
        }
        DebtAssignment assignment = assignmentOpt.get();
        if (assignment.status() != DebtAssignment.Status.ACTIVE) {
            store.audit(event.tenantId(), "PAYMENT_NOT_ATTRIBUTED", "event=" + event.eventId() + " reason=ASSIGNMENT_WITHDRAWN");
            return new AttributionResult(false, "ASSIGNMENT_WITHDRAWN", null);
        }
        ThirdPartyContract contract = requireContract(event.tenantId(), assignment.contractId());
        if (!contract.covers(event.paidOn())) {
            store.audit(event.tenantId(), "PAYMENT_NOT_ATTRIBUTED", "event=" + event.eventId() + " reason=OUTSIDE_CONTRACT_WINDOW");
            return new AttributionResult(false, "OUTSIDE_CONTRACT_WINDOW", null);
        }
        if (validAgreement == null)
            throw new ThirdPartyCollectionException("VALID_AGREEMENT_PORT_NOT_WIRED",
                    "porta de acordo valido da P4.1 nao ligada: atribuicao recusa, nao presume acordo");
        if (!validAgreement.hasValidAgreement(event.tenantId(), event.debtId())) {
            store.audit(event.tenantId(), "PAYMENT_NOT_ATTRIBUTED", "event=" + event.eventId() + " reason=NO_VALID_AGREEMENT_P41");
            return new AttributionResult(false, "NO_VALID_AGREEMENT_P41", null);
        }
        RemunerationEntry entry = RemunerationEntry.forPayment(
                UUID.randomUUID(), event.tenantId(), contract, assignment, event);
        store.saveRemuneration(entry);
        store.recordPaymentDate(event.tenantId(), event.eventId(), event.paidOn());
        withdraw(assignment, DebtAssignment.WithdrawalCause.PAYMENT, event.paidOn(), "WITHDRAWN_BY_PAYMENT");
        store.audit(event.tenantId(), "PAYMENT_ATTRIBUTED",
                "event=" + event.eventId() + " remuneration=" + entry.remunerationAmount());
        return new AttributionResult(true, "ATTRIBUTED", entry);
    }

    // ---- E8: fim da janela por varredura explicita ----
    /**
     * Varredura invocavel do fim da janela: retira (causa WINDOW_END, data
     * = data da varredura declarada) todas as cessoes ACTIVAS cujo
     * contrato ja terminou a janela antes de {@code asOf}. Cessao de
     * contrato ainda dentro da janela nao e tocada. Devolve quantas foram
     * retiradas.
     */
    public int closeExpiredWindows(UUID tenantId, LocalDate asOf) {
        if (asOf == null)
            throw new ThirdPartyCollectionException("SWEEP_DATE_REQUIRED", "varredura exige a data declarada");
        int count = 0;
        for (DebtAssignment a : store.assignments(tenantId)) {
            if (a.status() != DebtAssignment.Status.ACTIVE) continue;
            ThirdPartyContract contract = requireContract(tenantId, a.contractId());
            if (contract.windowEnd().isBefore(asOf)) {
                withdraw(a, DebtAssignment.WithdrawalCause.WINDOW_END, asOf, "WITHDRAWN_BY_WINDOW_END");
                count++;
            }
        }
        store.audit(tenantId, "WINDOWS_CLOSED", "asOf=" + asOf + " count=" + count);
        return count;
    }

    // ---- E10: relatorio por empresa ----
    public CompanyCollectionReport report(UUID tenantId, UUID companySupplierId,
                                          LocalDate periodStart, LocalDate periodEnd) {
        if (periodStart == null || periodEnd == null)
            throw new ThirdPartyCollectionException("REPORT_PERIOD_REQUIRED", "relatorio exige o periodo declarado");
        return CompanyCollectionReport.derive(tenantId, companySupplierId, periodStart, periodEnd,
                store.remunerations(tenantId),
                eventId -> store.paymentDateOf(tenantId, eventId));
    }

    // ---- Leituras ----
    public List<DebtAssignment> assignments(UUID tenantId) { return store.assignments(tenantId); }
    public List<RemunerationEntry> remunerations(UUID tenantId) { return store.remunerations(tenantId); }
}
