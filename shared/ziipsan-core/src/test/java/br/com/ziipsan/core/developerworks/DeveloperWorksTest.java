package br.com.ziipsan.core.developerworks;

import static br.com.ziipsan.core.developerworks.DeveloperWorks.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DeveloperWorksTest {
    static final UUID T = UUID.randomUUID();
    static final LocalDate D = LocalDate.of(2026, 3, 2);
    static final UUID ASBUILT_ID = UUID.randomUUID();
    static final UUID ASBUILT_DRAFT_ID = UUID.randomUUID();
    static final UUID ASBUILT_ALIEN_ID = UUID.randomUUID();
    static final Set<String> FEATURES = Set.of("rede-trecho-1", "rede-trecho-2", "reservatorio-1");
    static final List<String> CHECKLIST = List.of("ensaio de pressao aprovado", "desinfeccao realizada", "as-built conferido em campo");

    // Dublês fiéis das portas (D5): só existem os ids registrados nestes conjuntos.
    Set<UUID> protocols = new HashSet<>();
    Set<UUID> measurements = new HashSet<>();
    Set<UUID> repairOrders = new HashSet<>();

    static Map<String, String> defaultParams() {
        Map<String, String> m = new HashMap<>();
        m.put(P_STUDY_FEE, "1500.00");
        m.put(P_REIMBURSEMENT_RULE, "PERCENTAGE:30");
        m.put(P_FEASIBILITY_DEADLINE, "30");
        m.put(P_ACCEPTANCE_CHECKLIST, String.join(";", CHECKLIST));
        // Prazos por etapa (D7): valores de agencia sinteticos, distintos entre si.
        m.put(stageDeadlineKey("FEASIBILITY_STUDY"), "7");
        m.put(stageDeadlineKey("GUIDELINES_ISSUED"), "10");
        m.put(stageDeadlineKey("PROJECT_APPROVED"), "12");
        m.put(stageDeadlineKey("WORKS_IN_PROGRESS"), "90");
        m.put(stageDeadlineKey("AS_BUILT_SUBMITTED"), "15");
        m.put(stageDeadlineKey("ACCEPTANCE_APPROVED"), "10");
        m.put(stageDeadlineKey("TRANSFER_SIGNED"), "20");
        m.put(stageDeadlineKey("INTERCONNECTION_RELEASED"), "5");
        return m;
    }
    Map<String, String> params = defaultParams();
    DeveloperWorksService svc = new DeveloperWorksService(
            new Parameters((t, k) -> Optional.ofNullable(params.get(k))),
            (t, code, on) -> switch (code) {
                case "DEVELOPMENT_FEASIBILITY_STUDY" -> new BigDecimal("1500.00");
                case "DEVELOPMENT_PROJECT_APPROVAL" -> new BigDecimal("2000.00");
                case "DEVELOPMENT_WORKS_SUPERVISION" -> new BigDecimal("800.00");
                case "DEVELOPMENT_NETWORK_ACCEPTANCE" -> new BigDecimal("1200.00");
                case "DEVELOPMENT_INTERCONNECTION" -> new BigDecimal("600.00");
                default -> throw new IllegalStateException("sem taxa ED-34 para " + code);
            },
            (t, id) -> {
                if (id.equals(ASBUILT_ID)) return Optional.of(new AsBuiltView(ASBUILT_ID, 3, "SUBMITTED", FEATURES));
                if (id.equals(ASBUILT_DRAFT_ID)) return Optional.of(new AsBuiltView(ASBUILT_DRAFT_ID, 1, "DRAFT", FEATURES));
                if (id.equals(ASBUILT_ALIEN_ID)) return Optional.of(new AsBuiltView(ASBUILT_ALIEN_ID, 2, "APPROVED", Set.of("outra-rede-qualquer")));
                return Optional.empty();
            },
            (t, id) -> protocols.contains(id),
            (t, id) -> measurements.contains(id),
            (t, id) -> repairOrders.contains(id));

    DevelopmentCase open() {
        UUID protocol = UUID.randomUUID(); protocols.add(protocol);
        return svc.openFromProtocol(T, protocol, "Loteadora Sintetica LTDA", DevelopmentKind.LOTEAMENTO, 120);
    }

    DevelopmentCase upToWorks() {
        DevelopmentCase c = open();
        svc.startFeasibilityStudy(T, c.id, D);
        svc.issueGuidelines(T, c.id, new TechnicalOpinion("ramal DN200 — rua projetada A",
                new BigDecimal("320"), new BigDecimal("200"), "VIAVEL com diretrizes", "Eng. Sintetico", D.plusDays(5)), D.plusDays(5));
        svc.approveProject(T, c.id, "PRJ-LOT-2026-001 v2", FEATURES, D.plusDays(10));
        svc.startWorks(T, c.id, D.plusDays(15));
        svc.registerSupervision(T, c.id, new SupervisionVisit(D.plusDays(20), "Fiscal 1", true, "valas e assentamento conformes"), D.plusDays(20));
        return c;
    }
    DevelopmentCase upToWorksNoSupervision() {
        DevelopmentCase c = open();
        svc.startFeasibilityStudy(T, c.id, D);
        svc.issueGuidelines(T, c.id, new TechnicalOpinion("ramal DN200 — rua projetada A",
                new BigDecimal("320"), new BigDecimal("200"), "VIAVEL com diretrizes", "Eng. Sintetico", D.plusDays(5)), D.plusDays(5));
        svc.approveProject(T, c.id, "PRJ-LOT-2026-001 v2", FEATURES, D.plusDays(10));
        svc.startWorks(T, c.id, D.plusDays(15));
        return c;
    }
    DevelopmentCase upToAccepted() {
        DevelopmentCase c = upToWorks();
        svc.submitAsBuilt(T, c.id, ASBUILT_ID, D.plusDays(35));
        svc.performAcceptanceInspection(T, c.id, Map.of(CHECKLIST.get(0), true, CHECKLIST.get(1), true, CHECKLIST.get(2), true), D.plusDays(40));
        return c;
    }
    DevelopmentCase upToTransferSigned() {
        DevelopmentCase c = upToAccepted();
        svc.signTransfer(T, c.id, 1, FEATURES, "Representante do prestador", new BigDecimal("900000.00"));
        return c;
    }

    @Test void pontaAPontaLoteamentoSinteticoDoProtocoloAInterligacao() {
        DevelopmentCase c = upToAccepted();
        assertEquals("PROVISIONAL", c.unitLotStatus());
        assertEquals(120, c.plannedUnits);
        TransferTerm term = svc.signTransfer(T, c.id, 1, FEATURES, "Representante do prestador", new BigDecimal("900000.00"));
        assertEquals(1, term.version());
        assertEquals(3, c.incorporatedAssets().size(), "cada feicao aceita vira bem incorporado ao patrimonio");
        svc.payAllCharges(T, c.id);
        svc.releaseInterconnection(T, c.id, D.plusDays(45));
        assertEquals(Stage.INTERCONNECTION_RELEASED, c.stage());
        // D2: a taxa de interligacao nasceu na liberacao — paga-la e pre-condicao do encerramento.
        svc.payAllCharges(T, c.id);
        svc.closeCase(T, c.id);
        assertEquals("ACTIVE", c.unitLotStatus());
        assertEquals(new BigDecimal("270000.00"), svc.reimbursement(T, c.id), "30% de 900.000 pela regra da agencia");
    }

    @Test void interligacaoBloqueadaSemAceiteSemPagamentoSemCessao() {
        DevelopmentCase c = upToWorks(); // sem aceite, sem pagamento, sem cessao
        DeveloperWorksException e = assertThrows(DeveloperWorksException.class, () -> svc.releaseInterconnection(T, c.id, D));
        assertEquals("INTERCONNECTION_BLOCKED", e.code);
        assertTrue(e.getMessage().contains("aceite") && e.getMessage().contains("pagamento") && e.getMessage().contains("cessao"));
    }

    @Test void interligacaoBloqueadaSoPorPagamentoEPorCessaoSeparadamente() {
        DevelopmentCase c = upToAccepted(); // aceite ok; falta cessao e pagamento
        DeveloperWorksException e1 = assertThrows(DeveloperWorksException.class, () -> svc.releaseInterconnection(T, c.id, D));
        assertTrue(e1.getMessage().contains("pagamento") && e1.getMessage().contains("cessao") && !e1.getMessage().contains("aceite da rede"));
        svc.signTransfer(T, c.id, 1, FEATURES, "Prestador", new BigDecimal("100000.00")); // cessao ok; falta pagamento
        DeveloperWorksException e2 = assertThrows(DeveloperWorksException.class, () -> svc.releaseInterconnection(T, c.id, D));
        assertTrue(e2.getMessage().contains("pagamento") && !e2.getMessage().contains("cessao"));
        svc.payAllCharges(T, c.id);
        assertDoesNotThrow(() -> svc.releaseInterconnection(T, c.id, D));
    }

    @Test void semProtocoloP136NaoNasceCaso() {
        assertEquals("PROTOCOL_REQUIRED", assertThrows(DeveloperWorksException.class,
                () -> svc.openFromProtocol(T, null, "X", DevelopmentKind.CONDOMINIO, 10)).code);
    }

    @Test void semParametroDaAgenciaRecusaOperar() {
        params.remove(P_STUDY_FEE);
        DevelopmentCase c = open();
        assertEquals("PARAMETER_MISSING", assertThrows(DeveloperWorksException.class,
                () -> svc.startFeasibilityStudy(T, c.id, D)).code);
        params.put(P_STUDY_FEE, "1500.00"); params.remove(P_ACCEPTANCE_CHECKLIST);
        DevelopmentCase c2 = upToWorks(); svc.submitAsBuilt(T, c2.id, ASBUILT_ID, D.plusDays(35));
        assertEquals("PARAMETER_MISSING", assertThrows(DeveloperWorksException.class,
                () -> svc.performAcceptanceInspection(T, c2.id, Map.of(), D)).code);
        params.put(P_ACCEPTANCE_CHECKLIST, String.join(";", CHECKLIST)); params.remove(P_REIMBURSEMENT_RULE);
        DevelopmentCase c3 = upToAccepted(); svc.signTransfer(T, c3.id, 1, FEATURES, "P", new BigDecimal("10.00"));
        assertEquals("PARAMETER_MISSING", assertThrows(DeveloperWorksException.class, () -> svc.reimbursement(T, c3.id)).code);
    }

    @Test void termoExigeFeicoesAprovadasEAsBuilt() {
        DevelopmentCase c = upToAccepted();
        assertEquals("TRANSFER_FEATURES_DIVERGE", assertThrows(DeveloperWorksException.class,
                () -> svc.signTransfer(T, c.id, 1, Set.of("rede-trecho-1"), "P", new BigDecimal("10.00"))).code);
        assertEquals("TRANSFER_VERSION_REQUIRED", assertThrows(DeveloperWorksException.class,
                () -> svc.signTransfer(T, c.id, 0, FEATURES, "P", new BigDecimal("10.00"))).code);
    }

    @Test void checklistIncompletoOuReprovadoBloqueiaAceite() {
        DevelopmentCase c = upToWorks(); svc.submitAsBuilt(T, c.id, ASBUILT_ID, D.plusDays(35));
        assertEquals("CHECKLIST_ITEM_MISSING", assertThrows(DeveloperWorksException.class,
                () -> svc.performAcceptanceInspection(T, c.id, Map.of(CHECKLIST.get(0), true), D)).code);
        assertEquals("CHECKLIST_ITEM_FAILED", assertThrows(DeveloperWorksException.class,
                () -> svc.performAcceptanceInspection(T, c.id,
                        Map.of(CHECKLIST.get(0), true, CHECKLIST.get(1), false, CHECKLIST.get(2), true), D)).code);
    }

    @Test void recomposicaoDerivadaComTipoEMedicao() {
        UUID repair = UUID.randomUUID(); repairOrders.add(repair);
        PavementRestoration r = svc.derivePavementRestoration(T, repair, PavementType.ASFALTO, new BigDecimal("42.50"));
        assertEquals(repair, r.repairOrderId());
        UUID m = UUID.randomUUID(); measurements.add(m);
        assertEquals(m, svc.linkRestorationMeasurement(T, r.id(), m).contractorMeasurementIdOrNull());
        assertEquals("PAVEMENT_TYPE_REQUIRED", assertThrows(DeveloperWorksException.class,
                () -> new PavementRestoration(UUID.randomUUID(), T, repair, null, BigDecimal.ONE, null)).code);
    }

    @Test void obraDoPrestadorComMedicaoED37EFiscalizacao() {
        ProviderWork w = svc.createProviderWork(new ProviderWork(UUID.randomUUID(), T, "Ampliacao de adutora — trecho 3",
                D, D.plusDays(90), new BigDecimal("2500000.00"), List.of("terraplenagem", "assentamento", "testes"), null, null, List.of()));
        UUID measurement = UUID.randomUUID(); measurements.add(measurement);
        ProviderWork u = svc.linkContractorMeasurement(T, w.id(), measurement, new BigDecimal("400000.00"));
        assertEquals(measurement, u.contractorMeasurementIdOrNull());
        assertEquals(1, svc.superviseProviderWork(T, w.id(),
                new SupervisionVisit(D.plusDays(10), "Fiscal 2", true, "cronograma aderente")).supervision().size());
    }

    @Test void licencaEConsultaRegistradasSemIntegracaoNacionalOPE27() {
        UUID order = UUID.randomUUID();
        svc.registerAttachments(T, order,
                new RoadInterventionLicense("LIC-VIA-2026-77", "Prefeitura — transito", D, D.plusDays(15)),
                InterferenceConsultation.manual(D, List.of("energia eletrica", "telecom", "gas"), "consulta manual registrada na OS"));
        FieldOrderAttachments a = svc.attachmentsOf(order);
        assertNotNull(a.licenseOrNull()); assertFalse(a.consultationOrNull().nationalIntegrationConfirmed());
        assertEquals("OPE27_NATIONAL_INTEGRATION_UNCONFIRMED", assertThrows(DeveloperWorksException.class,
                () -> new InterferenceConsultation(D, List.of("agua"), "x", true)).code);
    }

    // ------------------------------------------------------------------
    // Correcoes da revisao independente (D1–D10) — regressao e caminhos
    // de recusa que faltavam (D8).
    // ------------------------------------------------------------------

    @Test void d1CasoEncapsuladoSemCampoPublicoMutavelNemReferenciaViva() {
        for (Field f : DevelopmentCase.class.getDeclaredFields()) {
            if (f.isSynthetic()) continue;
            assertTrue(Modifier.isPrivate(f.getModifiers()) || Modifier.isFinal(f.getModifiers()),
                    "campo exposto a mutacao externa: " + f.getName());
        }
        DevelopmentCase c = upToAccepted();
        // As visoes devolvidas sao copias imutaveis: muta-las falha e nao toca o caso.
        assertThrows(UnsupportedOperationException.class, () -> c.charges().add(
                new FeeCharge(UUID.randomUUID(), ServiceType.DEVELOPMENT_INTERCONNECTION, BigDecimal.ONE, true, null)));
        assertThrows(UnsupportedOperationException.class, () -> c.visits().clear());
        assertThrows(UnsupportedOperationException.class, () -> c.incorporatedAssets().clear());
        assertThrows(UnsupportedOperationException.class, () -> c.checklistResults().clear());
        assertThrows(UnsupportedOperationException.class, () -> c.stageDueDates().clear());
        // Estado interno intacto apos as tentativas: a trava continua de pe via servico.
        assertTrue(c.acceptanceApproved());
        assertNull(c.transfer());
        assertFalse(c.allFeesPaid());
        DeveloperWorksException e = assertThrows(DeveloperWorksException.class, () -> svc.releaseInterconnection(T, c.id, D));
        assertEquals("INTERCONNECTION_BLOCKED", e.code);
    }

    @Test void d2FecharDevendoTaxaDeInterligacaoEImpossivel() {
        DevelopmentCase c = upToTransferSigned();
        svc.payAllCharges(T, c.id);
        FeeCharge interconnection = svc.releaseInterconnection(T, c.id, D.plusDays(45));
        assertFalse(interconnection.paid(), "a taxa de interligacao nasce nao paga junto da liberacao");
        assertFalse(c.allFeesPaid());
        assertEquals("CLOSE_BLOCKED_UNPAID_FEES", assertThrows(DeveloperWorksException.class,
                () -> svc.closeCase(T, c.id)).code);
        assertEquals("PROVISIONAL", c.unitLotStatus(), "devendo, o lote NAO pode ativar");
        assertEquals(Stage.INTERCONNECTION_RELEASED, c.stage());
        svc.registerPayment(T, c.id, interconnection.id());
        assertTrue(c.allFeesPaid());
        svc.closeCase(T, c.id);
        assertEquals("ACTIVE", c.unitLotStatus());
        assertEquals(Stage.CLOSED, c.stage());
    }

    @Test void d3LiberarInterligacaoDuasVezesRecusadoSemCobrancaDuplicada() {
        DevelopmentCase c = upToTransferSigned();
        svc.payAllCharges(T, c.id);
        svc.releaseInterconnection(T, c.id, D.plusDays(45));
        svc.payAllCharges(T, c.id);
        assertEquals("STAGE_INVALID", assertThrows(DeveloperWorksException.class,
                () -> svc.releaseInterconnection(T, c.id, D.plusDays(46))).code);
        long interconnectionCharges = c.charges().stream()
                .filter(f -> f.type() == ServiceType.DEVELOPMENT_INTERCONNECTION).count();
        assertEquals(1, interconnectionCharges, "liberacao repetida nao pode duplicar a taxa");
    }

    @Test void d4AceiteRecusaAsBuiltAlheioDisjuntoJaNaSubmissao() {
        DevelopmentCase c = upToWorks();
        assertEquals("ASBUILT_FEATURES_DIVERGE", assertThrows(DeveloperWorksException.class,
                () -> svc.submitAsBuilt(T, c.id, ASBUILT_ALIEN_ID, D.plusDays(35))).code);
        assertEquals(Stage.WORKS_IN_PROGRESS, c.stage(), "aceite nao pode nascer sobre pacote alheio");
        assertFalse(c.acceptanceApproved());
        assertNull(c.asBuilt());
    }

    @Test void d5PortasDeExistenciaProtocoloMedicaoEOReparo() {
        // Protocolo P1.36 inexistente: nao nasce caso.
        assertEquals("PROTOCOL_NOT_FOUND", assertThrows(DeveloperWorksException.class,
                () -> svc.openFromProtocol(T, UUID.randomUUID(), "X", DevelopmentKind.CONDOMINIO, 10)).code);
        // Medicao ED-37 inexistente (obra do prestador).
        ProviderWork w = svc.createProviderWork(new ProviderWork(UUID.randomUUID(), T, "Rede coletora — etapa 1",
                D, D.plusDays(60), new BigDecimal("900000.00"), List.of("escavacao"), null, null, List.of()));
        assertEquals("MEASUREMENT_NOT_FOUND", assertThrows(DeveloperWorksException.class,
                () -> svc.linkContractorMeasurement(T, w.id(), UUID.randomUUID(), new BigDecimal("1000.00"))).code);
        // OS de reparo inexistente: recomposicao nao deriva.
        assertEquals("REPAIR_ORDER_NOT_FOUND", assertThrows(DeveloperWorksException.class,
                () -> svc.derivePavementRestoration(T, UUID.randomUUID(), PavementType.CONCRETO, new BigDecimal("10.00"))).code);
        // Medicao ED-37 inexistente (recomposicao).
        UUID repair = UUID.randomUUID(); repairOrders.add(repair);
        PavementRestoration r = svc.derivePavementRestoration(T, repair, PavementType.CONCRETO, new BigDecimal("10.00"));
        assertEquals("MEASUREMENT_NOT_FOUND", assertThrows(DeveloperWorksException.class,
                () -> svc.linkRestorationMeasurement(T, r.id(), UUID.randomUUID())).code);
    }

    @Test void d6TermoExigeAssinante() {
        DevelopmentCase c = upToAccepted();
        assertEquals("TRANSFER_SIGNER_REQUIRED", assertThrows(DeveloperWorksException.class,
                () -> svc.signTransfer(T, c.id, 1, FEATURES, null, new BigDecimal("10.00"))).code);
        assertEquals("TRANSFER_SIGNER_REQUIRED", assertThrows(DeveloperWorksException.class,
                () -> svc.signTransfer(T, c.id, 1, FEATURES, "   ", new BigDecimal("10.00"))).code);
        assertEquals(Stage.ACCEPTANCE_APPROVED, c.stage(), "termo sem autor nao pode ser lavrado");
        assertNull(c.transfer());
        assertEquals(0, c.incorporatedAssets().size());
    }

    @Test void d7PrazosPorEtapaAplicadosNasTransicoes() {
        DevelopmentCase c = open();
        svc.startFeasibilityStudy(T, c.id, D);
        assertEquals(D.plusDays(7), c.stageDueDates().get("FEASIBILITY_STUDY"));
        svc.issueGuidelines(T, c.id, new TechnicalOpinion("ramal DN200 — rua projetada A",
                new BigDecimal("320"), new BigDecimal("200"), "VIAVEL com diretrizes", "Eng. Sintetico", D.plusDays(5)), D.plusDays(5));
        assertEquals(D.plusDays(15), c.stageDueDates().get("GUIDELINES_ISSUED"));
        svc.approveProject(T, c.id, "PRJ-LOT-2026-001 v2", FEATURES, D.plusDays(10));
        assertEquals(D.plusDays(22), c.stageDueDates().get("PROJECT_APPROVED"));
        svc.startWorks(T, c.id, D.plusDays(15));
        assertEquals(D.plusDays(105), c.stageDueDates().get("WORKS_IN_PROGRESS"));
    }

    @Test void d7SemPrazoDaEtapaRecusaAntesDeCobrar() {
        params.remove(stageDeadlineKey("PROJECT_APPROVED"));
        DevelopmentCase c = open();
        svc.startFeasibilityStudy(T, c.id, D);
        svc.issueGuidelines(T, c.id, new TechnicalOpinion("ramal DN200 — rua projetada A",
                new BigDecimal("320"), new BigDecimal("200"), "VIAVEL com diretrizes", "Eng. Sintetico", D.plusDays(5)), D.plusDays(5));
        int chargesBefore = c.charges().size();
        assertEquals("PARAMETER_MISSING", assertThrows(DeveloperWorksException.class,
                () -> svc.approveProject(T, c.id, "PRJ-LOT-2026-001 v2", FEATURES, D.plusDays(10))).code);
        assertEquals(Stage.GUIDELINES_ISSUED, c.stage());
        assertEquals(chargesBefore, c.charges().size(), "recusa por prazo ausente nao pode deixar cobranca orfa");
    }

    @Test void d8ViabilidadeConcluidaComAtrasoEMarcada() {
        DevelopmentCase late = open();
        svc.startFeasibilityStudy(T, late.id, D);
        assertEquals(D.plusDays(30), late.feasibilityDueDate(), "vencimento = solicitacao + works.feasibility_deadline_days");
        svc.issueGuidelines(T, late.id, new TechnicalOpinion("ramal DN200 — rua projetada A",
                new BigDecimal("320"), new BigDecimal("200"), "VIAVEL com diretrizes", "Eng. Sintetico", D.plusDays(40)), D.plusDays(40));
        assertTrue(late.feasibilityLate());
        DevelopmentCase onTime = open();
        svc.startFeasibilityStudy(T, onTime.id, D);
        svc.issueGuidelines(T, onTime.id, new TechnicalOpinion("ramal DN200 — rua projetada A",
                new BigDecimal("320"), new BigDecimal("200"), "VIAVEL com diretrizes", "Eng. Sintetico", D.plusDays(5)), D.plusDays(5));
        assertFalse(onTime.feasibilityLate());
    }

    @Test void d8d9DivergenciaCatalogoParametroSemCobrancaOrfa() {
        params.put(P_STUDY_FEE, "999.99"); // catalogo ED-34 (dublê) cobra 1500.00
        DevelopmentCase c = open();
        assertEquals("STUDY_FEE_DIVERGES_FROM_PARAMETER", assertThrows(DeveloperWorksException.class,
                () -> svc.startFeasibilityStudy(T, c.id, D)).code);
        assertTrue(c.charges().isEmpty(), "divergencia recusada nao pode deixar cobranca orfa (D9)");
        assertEquals(Stage.PROTOCOL_OPENED, c.stage(), "retentativa nao pode acumular cobrancas nem avancar a etapa");
    }

    @Test void d8ParecerTecnicoInvalidoRecusado() {
        assertEquals("OPINION_PRESSURE_REQUIRED", assertThrows(DeveloperWorksException.class,
                () -> new TechnicalOpinion("ramal DN200", BigDecimal.ZERO, new BigDecimal("200"), "VIAVEL", "Eng.", D)).code);
        assertEquals("OPINION_DIAMETER_REQUIRED", assertThrows(DeveloperWorksException.class,
                () -> new TechnicalOpinion("ramal DN200", new BigDecimal("320"), BigDecimal.ZERO, "VIAVEL", "Eng.", D)).code);
        assertEquals("OPINION_PRESSURE_REQUIRED", assertThrows(DeveloperWorksException.class,
                () -> new TechnicalOpinion("ramal DN200", null, new BigDecimal("200"), "VIAVEL", "Eng.", D)).code);
    }

    @Test void d8AsBuiltNaoSubmetidoRecusado() {
        DevelopmentCase c = upToWorks();
        assertEquals("ASBUILT_NOT_SUBMITTED", assertThrows(DeveloperWorksException.class,
                () -> svc.submitAsBuilt(T, c.id, ASBUILT_DRAFT_ID, D.plusDays(35))).code);
        assertEquals(Stage.WORKS_IN_PROGRESS, c.stage());
    }

    @Test void d8RecebimentoSemFiscalizacaoRecusado() {
        DevelopmentCase c = upToWorksNoSupervision();
        assertEquals("SUPERVISION_REQUIRED", assertThrows(DeveloperWorksException.class,
                () -> svc.submitAsBuilt(T, c.id, ASBUILT_ID, D.plusDays(35))).code);
    }

    @Test void d8TransicoesDeEtapaInvalidasRecusadas() {
        DevelopmentCase c = open();
        assertEquals("STAGE_INVALID", assertThrows(DeveloperWorksException.class,
                () -> svc.approveProject(T, c.id, "PRJ-X", FEATURES, D)).code);
        assertEquals("STAGE_INVALID", assertThrows(DeveloperWorksException.class,
                () -> svc.issueGuidelines(T, c.id, new TechnicalOpinion("ramal DN200",
                        new BigDecimal("320"), new BigDecimal("200"), "VIAVEL", "Eng.", D), D)).code);
        assertEquals("STAGE_INVALID", assertThrows(DeveloperWorksException.class,
                () -> svc.startWorks(T, c.id, D)).code);
        assertEquals("STAGE_INVALID", assertThrows(DeveloperWorksException.class,
                () -> svc.closeCase(T, c.id)).code);
        assertEquals(Stage.PROTOCOL_OPENED, c.stage());
    }

    @Test void d10RegistroDeAnexosExigeLicencaEConsulta() {
        UUID order = UUID.randomUUID();
        RoadInterventionLicense license = new RoadInterventionLicense("LIC-VIA-2026-77", "Prefeitura — transito", D, D.plusDays(15));
        InterferenceConsultation consultation = InterferenceConsultation.manual(D, List.of("energia eletrica"), "consulta manual registrada na OS");
        assertEquals("LICENSE_REQUIRED", assertThrows(DeveloperWorksException.class,
                () -> svc.registerAttachments(T, order, null, consultation)).code);
        assertEquals("CONSULTATION_REQUIRED", assertThrows(DeveloperWorksException.class,
                () -> svc.registerAttachments(T, order, license, null)).code);
        assertEquals("LICENSE_REQUIRED", assertThrows(DeveloperWorksException.class,
                () -> svc.registerAttachments(T, order, null, null)).code);
        assertNull(svc.attachmentsOf(order), "registro recusado nao pode gravar anexo vazio");
    }
}
