package br.com.ziipsan.core.contractor;

import br.com.ziipsan.core.certification.governance.GovernanceAccess;
import br.com.ziipsan.core.certification.governance.GovernanceAccessDeniedException;
import br.com.ziipsan.core.certification.governance.GovernanceValidationException;
import br.com.ziipsan.core.certification.governance.InMemoryGovernanceStore;
import br.com.ziipsan.core.certification.governance.SupplierRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractorServiceTest {
    static final UUID T = UUID.randomUUID();
    static final UUID T2 = UUID.randomUUID();
    static final UUID A = UUID.randomUUID();
    static final LocalDate JAN1 = LocalDate.of(2026, 1, 1);
    static final LocalDate DEC31 = LocalDate.of(2026, 12, 31);

    InMemoryGovernanceStore gov = new InMemoryGovernanceStore();
    InMemoryContractorStore store = new InMemoryContractorStore();
    List<Measurement.ExecutedOrder> executed = List.of();
    ContractorService svc = new ContractorService(gov, store, (t, s, f, to) -> executed);

    SupplierRecord contractorSupplier() {
        SupplierRecord s = new SupplierRecord(UUID.randomUUID(), T, "Empreiteira Alfa Ltda", "doc-alfa",
                SupplierRecord.Type.CONTRACTOR, "contato-alfa");
        gov.saveSupplier(s);
        return s;
    }

    ContractorProfile profileFor(UUID supplierId) {
        return new ContractorProfile(UUID.randomUUID(), T, supplierId, Set.of("REDE_AGUA"), "municipio", "resp-tecnico");
    }

    ContractorContract activeContract(UUID supplierId) {
        return new ContractorContract(UUID.randomUUID(), T, supplierId, "CT-001", "Manutencao de rede",
                LocalDate.of(2025, 12, 20), JAN1, DEC31, new BigDecimal("100000.00"), ContractorContract.Status.ACTIVE);
    }

    UUID registeredContractorWithContract() {
        SupplierRecord s = contractorSupplier();
        svc.registerContractor(GovernanceAccess.EDITOR, profileFor(s.id()), A);
        svc.registerContract(GovernanceAccess.EDITOR, activeContract(s.id()), A);
        return s.id();
    }

    // ---------- SG-492: extensao da P3.31, sem cadastro paralelo ----------
    @Test void contractorExtendsP331ContractorSupplier() {
        SupplierRecord s = contractorSupplier();
        ContractorProfile p = svc.registerContractor(GovernanceAccess.EDITOR, profileFor(s.id()), A);
        assertEquals(s.id(), p.supplierId());
        assertEquals(1, svc.contractors(T).size());
        // identidade continua na P3.31: o perfil nao carrega razao/documento
        assertEquals("Empreiteira Alfa Ltda", gov.suppliers(T).get(0).legalName());
    }

    @Test void nonContractorSupplierTypeIsRefused() {
        SupplierRecord material = new SupplierRecord(UUID.randomUUID(), T, "Material SA", "doc",
                SupplierRecord.Type.MATERIAL, "c");
        gov.saveSupplier(material);
        GovernanceValidationException ex = assertThrows(GovernanceValidationException.class,
                () -> svc.registerContractor(GovernanceAccess.EDITOR, profileFor(material.id()), A));
        assertEquals("SUPPLIER_NOT_CONTRACTOR", ex.code());
    }

    @Test void unknownSupplierIsRefusedNoParallelRegistry() {
        GovernanceValidationException ex = assertThrows(GovernanceValidationException.class,
                () -> svc.registerContractor(GovernanceAccess.EDITOR, profileFor(UUID.randomUUID()), A));
        assertEquals("CONTRACTOR_SUPPLIER_NOT_FOUND", ex.code());
    }

    @Test void supplierOfAnotherTenantIsRefused() {
        SupplierRecord other = new SupplierRecord(UUID.randomUUID(), T2, "Outra", "doc",
                SupplierRecord.Type.CONTRACTOR, "c");
        gov.saveSupplier(other);
        GovernanceValidationException ex = assertThrows(GovernanceValidationException.class,
                () -> svc.registerContractor(GovernanceAccess.EDITOR, profileFor(other.id()), A));
        assertEquals("CONTRACTOR_SUPPLIER_NOT_FOUND", ex.code());
    }

    @Test void duplicateProfileIsRefused() {
        SupplierRecord s = contractorSupplier();
        svc.registerContractor(GovernanceAccess.EDITOR, profileFor(s.id()), A);
        GovernanceValidationException ex = assertThrows(GovernanceValidationException.class,
                () -> svc.registerContractor(GovernanceAccess.EDITOR, profileFor(s.id()), A));
        assertEquals("CONTRACTOR_PROFILE_DUPLICATE", ex.code());
    }

    @Test void readerCannotWrite() {
        SupplierRecord s = contractorSupplier();
        assertThrows(GovernanceAccessDeniedException.class,
                () -> svc.registerContractor(GovernanceAccess.READER, profileFor(s.id()), A));
    }

    // ---------- SG-493: contratos ----------
    @Test void contractRequiresProfileAndUniqueNumber() {
        SupplierRecord s = contractorSupplier();
        GovernanceValidationException ex = assertThrows(GovernanceValidationException.class,
                () -> svc.registerContract(GovernanceAccess.EDITOR, activeContract(s.id()), A));
        assertEquals("CONTRACTOR_PROFILE_REQUIRED", ex.code());
        svc.registerContractor(GovernanceAccess.EDITOR, profileFor(s.id()), A);
        svc.registerContract(GovernanceAccess.EDITOR, activeContract(s.id()), A);
        GovernanceValidationException dup = assertThrows(GovernanceValidationException.class,
                () -> svc.registerContract(GovernanceAccess.EDITOR, activeContract(s.id()), A));
        assertEquals("CONTRACT_NUMBER_DUPLICATE", dup.code());
    }

    @Test void invertedContractValidityIsRefused() {
        assertThrows(GovernanceValidationException.class, () -> new ContractorContract(UUID.randomUUID(), T,
                UUID.randomUUID(), "CT-X", "objeto", null, DEC31, JAN1, BigDecimal.TEN,
                ContractorContract.Status.DRAFT));
    }

    // ---------- SG-494: penalidades parametrizadas, sem padrao de fabrica ----------
    @Test void noFactoryPenaltyExistsByDefault() {
        registeredContractorWithContract();
        UUID contractId = store.contracts(T).get(0).id();
        assertTrue(svc.clauses(contractId).isEmpty(), "nenhuma clausula pode nascer de fabrica (catalogo P3.31 vazio por desenho)");
        assertTrue(svc.applications(contractId).isEmpty());
    }

    @Test void percentOfContractPenaltyIsComputedFromClause() {
        registeredContractorWithContract();
        UUID contractId = store.contracts(T).get(0).id();
        Penalties.PenaltyClause clause = new Penalties.PenaltyClause(UUID.randomUUID(), T, contractId, "MULTA-ATRASO",
                "descumprimento de prazo", Penalties.Basis.PERCENT_OF_CONTRACT, new BigDecimal("2.00"), null);
        svc.registerClause(GovernanceAccess.EDITOR, clause, A);
        Penalties.PenaltyApplication app = svc.applyPenalty(GovernanceAccess.EDITOR, T, contractId, clause.id(),
                null, LocalDate.of(2026, 3, 10), "atraso na OS", 0, A);
        assertEquals(new BigDecimal("2000.00"), app.amount()); // 2% de 100000.00
    }

    @Test void perDayPenaltyRespectsDeclaredCap() {
        registeredContractorWithContract();
        UUID contractId = store.contracts(T).get(0).id();
        Penalties.PenaltyClause clause = new Penalties.PenaltyClause(UUID.randomUUID(), T, contractId, "DIARIA",
                "multa diaria", Penalties.Basis.PER_DAY, new BigDecimal("500.00"), new BigDecimal("1200.00"));
        svc.registerClause(GovernanceAccess.EDITOR, clause, A);
        Penalties.PenaltyApplication app = svc.applyPenalty(GovernanceAccess.EDITOR, T, contractId, clause.id(),
                null, LocalDate.of(2026, 3, 10), "3 dias de atraso", 3, A);
        assertEquals(new BigDecimal("1200.00"), app.amount()); // 3 x 500 = 1500, teto 1200
    }

    @Test void percentPenaltyAbove100IsRefused() {
        assertThrows(GovernanceValidationException.class, () -> new Penalties.PenaltyClause(UUID.randomUUID(), T,
                UUID.randomUUID(), "X", "desc", Penalties.Basis.PERCENT_OF_CONTRACT, new BigDecimal("150"), null));
    }

    @Test void waivePenaltyRequiresApproverAndReason() {
        registeredContractorWithContract();
        UUID contractId = store.contracts(T).get(0).id();
        Penalties.PenaltyClause clause = new Penalties.PenaltyClause(UUID.randomUUID(), T, contractId, "FIXA",
                "multa fixa", Penalties.Basis.FIXED_AMOUNT, new BigDecimal("800.00"), null);
        svc.registerClause(GovernanceAccess.EDITOR, clause, A);
        Penalties.PenaltyApplication app = svc.applyPenalty(GovernanceAccess.EDITOR, T, contractId, clause.id(),
                null, LocalDate.of(2026, 4, 1), "ocorrencia", 0, A);
        assertThrows(GovernanceAccessDeniedException.class,
                () -> svc.waivePenalty(GovernanceAccess.EDITOR, T, app.id(), "motivo", A));
        assertThrows(GovernanceValidationException.class,
                () -> svc.waivePenalty(GovernanceAccess.APPROVER, T, app.id(), " ", A));
        Penalties.PenaltyApplication waived = svc.waivePenalty(GovernanceAccess.APPROVER, T, app.id(), "motivo declarado", A);
        assertEquals(Penalties.PenaltyApplication.Status.WAIVED, waived.status());
    }

    // ---------- SG-495/SG-496: medicao ligada as OS executadas (ED-36) ----------
    Measurement.ExecutedOrder order(UUID supplierId, String day) {
        return new Measurement.ExecutedOrder(UUID.randomUUID(), T, supplierId,
                LocalDateTime.of(2026, 6, Integer.parseInt(day), 10, 0), "OS executada");
    }

    Map<UUID, Measurement.MeasurementItem> pricing(Measurement.ExecutedOrder... orders) {
        Map<UUID, Measurement.MeasurementItem> map = new java.util.HashMap<>();
        for (Measurement.ExecutedOrder o : orders)
            map.put(o.orderId(), new Measurement.MeasurementItem(o.orderId(), "item da OS", new BigDecimal("1"), new BigDecimal("1500.00")));
        return map;
    }

    @Test void measurementIsBuiltFromExecutedOrders() {
        UUID supplierId = registeredContractorWithContract();
        UUID contractId = store.contracts(T).get(0).id();
        Measurement.ExecutedOrder o1 = order(supplierId, "10"), o2 = order(supplierId, "12");
        executed = List.of(o1, o2);
        Measurement.ContractorMeasurement m = svc.buildMeasurement(GovernanceAccess.EDITOR, T, contractId,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), pricing(o1, o2), A);
        assertEquals(new BigDecimal("3000.00"), m.grossAmount());
        assertEquals(2, m.items().size());
    }

    @Test void doubleMeasurementOfSameOrderIsRefused() {
        UUID supplierId = registeredContractorWithContract();
        UUID contractId = store.contracts(T).get(0).id();
        Measurement.ExecutedOrder o1 = order(supplierId, "10");
        executed = List.of(o1);
        svc.buildMeasurement(GovernanceAccess.EDITOR, T, contractId,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), pricing(o1), A);
        GovernanceValidationException ex = assertThrows(GovernanceValidationException.class,
                () -> svc.buildMeasurement(GovernanceAccess.EDITOR, T, contractId,
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), pricing(o1), A));
        assertEquals("ORDER_ALREADY_MEASURED", ex.code());
    }

    @Test void orderExecutedOutsideContractValidityIsRefused() {
        UUID supplierId = registeredContractorWithContract();
        UUID contractId = store.contracts(T).get(0).id();
        Measurement.ExecutedOrder old = new Measurement.ExecutedOrder(UUID.randomUUID(), T, supplierId,
                LocalDateTime.of(2025, 6, 10, 10, 0), "OS antiga");
        executed = List.of(old);
        GovernanceValidationException ex = assertThrows(GovernanceValidationException.class,
                () -> svc.buildMeasurement(GovernanceAccess.EDITOR, T, contractId,
                        LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 30), pricing(old), A));
        assertEquals("EXECUTED_ORDER_OUTSIDE_CONTRACT", ex.code());
    }

    @Test void approvalNetsAppliedPenaltiesAndRequiresApprover() {
        UUID supplierId = registeredContractorWithContract();
        UUID contractId = store.contracts(T).get(0).id();
        Measurement.ExecutedOrder o1 = order(supplierId, "10");
        executed = List.of(o1);
        Measurement.ContractorMeasurement m = svc.buildMeasurement(GovernanceAccess.EDITOR, T, contractId,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), pricing(o1), A);
        Penalties.PenaltyClause clause = new Penalties.PenaltyClause(UUID.randomUUID(), T, contractId, "PCT-MED",
                "percentual da medicao", Penalties.Basis.PERCENT_OF_MEASUREMENT, new BigDecimal("10.00"), null);
        svc.registerClause(GovernanceAccess.EDITOR, clause, A);
        svc.applyPenalty(GovernanceAccess.EDITOR, T, contractId, clause.id(), m.id(),
                LocalDate.of(2026, 6, 20), "desconto por nao conformidade", 0, A);
        svc.submitMeasurement(GovernanceAccess.EDITOR, T, m.id(), A);
        assertThrows(GovernanceAccessDeniedException.class,
                () -> svc.approveMeasurement(GovernanceAccess.EDITOR, T, m.id(), A));
        ContractorService.ApprovalResult result = svc.approveMeasurement(GovernanceAccess.APPROVER, T, m.id(), A);
        assertEquals(new BigDecimal("1500.00"), result.gross());
        assertEquals(new BigDecimal("150.00"), result.deductions()); // 10% da medicao
        assertEquals(new BigDecimal("1350.00"), result.net());
        assertEquals(Measurement.Status.APPROVED, store.measurement(m.id()).orElseThrow().status());
    }

    @Test void approvalRefusedWhenPenaltiesExceedGross() {
        UUID supplierId = registeredContractorWithContract();
        UUID contractId = store.contracts(T).get(0).id();
        Measurement.ExecutedOrder o1 = order(supplierId, "10");
        executed = List.of(o1);
        Measurement.ContractorMeasurement m = svc.buildMeasurement(GovernanceAccess.EDITOR, T, contractId,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), pricing(o1), A);
        Penalties.PenaltyClause clause = new Penalties.PenaltyClause(UUID.randomUUID(), T, contractId, "FIXA-GRANDE",
                "multa fixa", Penalties.Basis.FIXED_AMOUNT, new BigDecimal("9999.00"), null);
        svc.registerClause(GovernanceAccess.EDITOR, clause, A);
        svc.applyPenalty(GovernanceAccess.EDITOR, T, contractId, clause.id(), m.id(),
                LocalDate.of(2026, 6, 20), "ocorrencia grave", 0, A);
        svc.submitMeasurement(GovernanceAccess.EDITOR, T, m.id(), A);
        GovernanceValidationException ex = assertThrows(GovernanceValidationException.class,
                () -> svc.approveMeasurement(GovernanceAccess.APPROVER, T, m.id(), A));
        assertEquals("MEASUREMENT_NET_NEGATIVE", ex.code());
    }

    @Test void measurementOnNonActiveContractIsRefused() {
        SupplierRecord s = contractorSupplier();
        svc.registerContractor(GovernanceAccess.EDITOR, profileFor(s.id()), A);
        ContractorContract draft = new ContractorContract(UUID.randomUUID(), T, s.id(), "CT-D", "objeto", null,
                JAN1, DEC31, BigDecimal.TEN, ContractorContract.Status.DRAFT);
        svc.registerContract(GovernanceAccess.EDITOR, draft, A);
        Measurement.ExecutedOrder o1 = order(s.id(), "10");
        executed = List.of(o1);
        GovernanceValidationException ex = assertThrows(GovernanceValidationException.class,
                () -> svc.buildMeasurement(GovernanceAccess.EDITOR, T, draft.id(),
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), pricing(o1), A));
        assertEquals("CONTRACT_NOT_ACTIVE", ex.code());
    }

    // ---------- Correcao D2: maquina de transicoes de estado do contrato ----------
    ContractorContract draftContract(UUID supplierId, String number) {
        return new ContractorContract(UUID.randomUUID(), T, supplierId, number, "Manutencao de rede",
                LocalDate.of(2025, 12, 20), JAN1, DEC31, new BigDecimal("100000.00"), ContractorContract.Status.DRAFT);
    }

    @Test void contractStatusValidTransitionsAreAccepted() {
        SupplierRecord s = contractorSupplier();
        svc.registerContractor(GovernanceAccess.EDITOR, profileFor(s.id()), A);
        ContractorContract draft = draftContract(s.id(), "CT-T1");
        svc.registerContract(GovernanceAccess.EDITOR, draft, A);
        assertEquals(ContractorContract.Status.ACTIVE,
                svc.changeContractStatus(GovernanceAccess.EDITOR, T, draft.id(), ContractorContract.Status.ACTIVE, A).status());
        assertEquals(ContractorContract.Status.SUSPENDED,
                svc.changeContractStatus(GovernanceAccess.EDITOR, T, draft.id(), ContractorContract.Status.SUSPENDED, A).status());
        assertEquals(ContractorContract.Status.ACTIVE,
                svc.changeContractStatus(GovernanceAccess.EDITOR, T, draft.id(), ContractorContract.Status.ACTIVE, A).status());
        assertEquals(ContractorContract.Status.CLOSED,
                svc.changeContractStatus(GovernanceAccess.EDITOR, T, draft.id(), ContractorContract.Status.CLOSED, A).status());
        // DRAFT -> CLOSED direto tambem e valido (encerramento sem ativacao)
        ContractorContract draft2 = draftContract(s.id(), "CT-T2");
        svc.registerContract(GovernanceAccess.EDITOR, draft2, A);
        assertEquals(ContractorContract.Status.CLOSED,
                svc.changeContractStatus(GovernanceAccess.EDITOR, T, draft2.id(), ContractorContract.Status.CLOSED, A).status());
        // SUSPENDED -> CLOSED: via contrato ACTIVE suspenso
        ContractorContract active = activeContract(s.id());
        svc.registerContract(GovernanceAccess.EDITOR, new ContractorContract(active.id(), T, s.id(), "CT-T3",
                active.objectDescription(), active.signedOn(), JAN1, DEC31, active.totalValue(), ContractorContract.Status.ACTIVE), A);
        svc.changeContractStatus(GovernanceAccess.EDITOR, T, active.id(), ContractorContract.Status.SUSPENDED, A);
        assertEquals(ContractorContract.Status.CLOSED,
                svc.changeContractStatus(GovernanceAccess.EDITOR, T, active.id(), ContractorContract.Status.CLOSED, A).status());
    }

    @Test void contractStatusInvalidTransitionsAreRefused() {
        SupplierRecord s = contractorSupplier();
        svc.registerContractor(GovernanceAccess.EDITOR, profileFor(s.id()), A);
        ContractorContract draft = draftContract(s.id(), "CT-I1");
        svc.registerContract(GovernanceAccess.EDITOR, draft, A);
        // DRAFT -> SUSPENDED nao existe
        GovernanceValidationException ex = assertThrows(GovernanceValidationException.class,
                () -> svc.changeContractStatus(GovernanceAccess.EDITOR, T, draft.id(), ContractorContract.Status.SUSPENDED, A));
        assertEquals("CONTRACT_STATUS_TRANSITION_INVALID", ex.code());
        // "transicao" para o proprio estado tambem nao existe
        assertThrows(GovernanceValidationException.class,
                () -> svc.changeContractStatus(GovernanceAccess.EDITOR, T, draft.id(), ContractorContract.Status.DRAFT, A));
        // ACTIVE -> DRAFT nao existe
        svc.changeContractStatus(GovernanceAccess.EDITOR, T, draft.id(), ContractorContract.Status.ACTIVE, A);
        assertThrows(GovernanceValidationException.class,
                () -> svc.changeContractStatus(GovernanceAccess.EDITOR, T, draft.id(), ContractorContract.Status.DRAFT, A));
        // CLOSED e terminal: CLOSED -> ACTIVE recusado e o estado gravado nao muda
        svc.changeContractStatus(GovernanceAccess.EDITOR, T, draft.id(), ContractorContract.Status.CLOSED, A);
        GovernanceValidationException closed = assertThrows(GovernanceValidationException.class,
                () -> svc.changeContractStatus(GovernanceAccess.EDITOR, T, draft.id(), ContractorContract.Status.ACTIVE, A));
        assertEquals("CONTRACT_STATUS_TRANSITION_INVALID", closed.code());
        assertEquals(ContractorContract.Status.CLOSED, store.contract(draft.id()).orElseThrow().status());
    }

    // ---------- Correcao D3: duplicidade DENTRO do mesmo lote da porta ----------
    @Test void duplicateOrderWithinSameExecutedBatchIsRefused() {
        UUID supplierId = registeredContractorWithContract();
        UUID contractId = store.contracts(T).get(0).id();
        Measurement.ExecutedOrder o1 = order(supplierId, "10");
        executed = List.of(o1, o1); // a porta devolve a MESMA OS duas vezes na mesma chamada
        GovernanceValidationException ex = assertThrows(GovernanceValidationException.class,
                () -> svc.buildMeasurement(GovernanceAccess.EDITOR, T, contractId,
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), pricing(o1), A));
        assertEquals("ORDER_ALREADY_MEASURED", ex.code());
        assertTrue(svc.measurements(T).isEmpty(), "medicao com OS duplicada no lote nao pode ser gravada");
    }

    // ---------- Correcao D4: guardas de relevamento e de measurementId ----------
    @Test void waiveAlreadyWaivedPenaltyIsRefused() {
        registeredContractorWithContract();
        UUID contractId = store.contracts(T).get(0).id();
        Penalties.PenaltyClause clause = new Penalties.PenaltyClause(UUID.randomUUID(), T, contractId, "FIXA-W",
                "multa fixa", Penalties.Basis.FIXED_AMOUNT, new BigDecimal("800.00"), null);
        svc.registerClause(GovernanceAccess.EDITOR, clause, A);
        Penalties.PenaltyApplication app = svc.applyPenalty(GovernanceAccess.EDITOR, T, contractId, clause.id(),
                null, LocalDate.of(2026, 4, 1), "ocorrencia", 0, A);
        svc.waivePenalty(GovernanceAccess.APPROVER, T, app.id(), "motivo original", A);
        GovernanceValidationException ex = assertThrows(GovernanceValidationException.class,
                () -> svc.waivePenalty(GovernanceAccess.APPROVER, T, app.id(), "segundo motivo", A));
        assertEquals("PENALTY_ALREADY_WAIVED", ex.code());
        // o motivo gravado continua o do primeiro (unico) relevamento
        assertEquals("motivo original", store.application(app.id()).orElseThrow().waiveReasonOrNull());
        // a guarda tambem vale no record, abaixo do servico
        assertThrows(GovernanceValidationException.class,
                () -> store.application(app.id()).orElseThrow().waived("terceiro motivo"));
    }

    @Test void applyPenaltyValidatesMeasurementLinkOnNonPercentBases() {
        UUID supplierId = registeredContractorWithContract();
        UUID contractId = store.contracts(T).get(0).id();
        Penalties.PenaltyClause fixed = new Penalties.PenaltyClause(UUID.randomUUID(), T, contractId, "FIXA-M",
                "multa fixa", Penalties.Basis.FIXED_AMOUNT, new BigDecimal("800.00"), null);
        svc.registerClause(GovernanceAccess.EDITOR, fixed, A);
        // base NAO percentual com measurementId inexistente: recusado (antes era gravado sem conferencia)
        GovernanceValidationException ex = assertThrows(GovernanceValidationException.class,
                () -> svc.applyPenalty(GovernanceAccess.EDITOR, T, contractId, fixed.id(),
                        UUID.randomUUID(), LocalDate.of(2026, 4, 1), "ocorrencia", 0, A));
        assertEquals("MEASUREMENT_NOT_FOUND", ex.code());
        // idem para base PERCENT_OF_CONTRACT
        Penalties.PenaltyClause pctContract = new Penalties.PenaltyClause(UUID.randomUUID(), T, contractId, "PCT-C",
                "percentual do contrato", Penalties.Basis.PERCENT_OF_CONTRACT, new BigDecimal("1.00"), null);
        svc.registerClause(GovernanceAccess.EDITOR, pctContract, A);
        assertThrows(GovernanceValidationException.class,
                () -> svc.applyPenalty(GovernanceAccess.EDITOR, T, contractId, pctContract.id(),
                        UUID.randomUUID(), LocalDate.of(2026, 4, 1), "ocorrencia", 0, A));
        // controle positivo: measurementId VALIDO em base nao percentual e aceito e fica vinculado
        Measurement.ExecutedOrder o1 = order(supplierId, "10");
        executed = List.of(o1);
        Measurement.ContractorMeasurement m = svc.buildMeasurement(GovernanceAccess.EDITOR, T, contractId,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), pricing(o1), A);
        Penalties.PenaltyApplication app = svc.applyPenalty(GovernanceAccess.EDITOR, T, contractId, fixed.id(),
                m.id(), LocalDate.of(2026, 6, 20), "ocorrencia vinculada", 0, A);
        assertEquals(m.id(), app.measurementIdOrNull());
        assertEquals(new BigDecimal("800.00"), app.amount());
    }

    // ---------- Fora de escopo declarado: ED-62 / ED-84 ----------
    @Test void contractHasNoBudgetIntegrationNorWorksFields() {
        Set<String> forbidden = Set.of("empenho", "budget", "orcament", "pncp", "compraexterna", "obra", "projeto", "works", "project");
        for (java.lang.reflect.RecordComponent c : ContractorContract.class.getRecordComponents()) {
            String name = c.getName().toLowerCase();
            assertTrue(forbidden.stream().noneMatch(name::contains),
                    "campo fora de escopo (ED-62/ED-84) no contrato: " + c.getName());
        }
    }
}
