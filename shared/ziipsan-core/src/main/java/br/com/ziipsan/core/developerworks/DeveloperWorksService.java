package br.com.ziipsan.core.developerworks;

import br.com.ziipsan.core.developerworks.DeveloperWorks.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Servico do fluxo ED-84 (ver javadoc de {@link DeveloperWorks}).
 *
 * Correcoes da revisao independente (REVISAO-ED-84.md):
 *  D1 — o caso devolvido e encapsulado: transicoes so por estes metodos;
 *  D2 — closeCase reconfere o pagamento integral de TODAS as cobrancas
 *       (inclusive a DEVELOPMENT_INTERCONNECTION, lancada na liberacao);
 *  D3 — releaseInterconnection tem guarda de etapa e ocorre uma unica vez;
 *  D4 — submitAsBuilt/vistoria exigem o as-built contendo as feicoes aprovadas;
 *  D5 — protocolo P1.36, medicao ED-37 e OS de reparo conferidos por portas;
 *  D7 — developer.deadline_days.&lt;etapa&gt; aplicado a cada transicao;
 *  D9 — startFeasibilityStudy confere a divergencia ANTES de gravar a cobranca.
 */
public final class DeveloperWorksService {
    private final Parameters params;
    private final ServiceFeeCatalogPort fees;
    private final AsBuiltPort asBuiltPort;
    private final ProtocolPort protocolPort;
    private final ContractorMeasurementPort measurementPort;
    private final RepairOrderPort repairOrderPort;
    private final Map<UUID, DevelopmentCase> cases = new LinkedHashMap<>();
    private final Map<UUID, ProviderWork> works = new LinkedHashMap<>();
    private final Map<UUID, PavementRestoration> restorations = new LinkedHashMap<>();
    private final Map<UUID, FieldOrderAttachments> attachments = new LinkedHashMap<>();
    private final List<String> audit = new ArrayList<>();

    public DeveloperWorksService(Parameters params, ServiceFeeCatalogPort fees, AsBuiltPort asBuiltPort,
                                 ProtocolPort protocolPort, ContractorMeasurementPort measurementPort,
                                 RepairOrderPort repairOrderPort) {
        this.params = params; this.fees = fees; this.asBuiltPort = asBuiltPort;
        this.protocolPort = protocolPort; this.measurementPort = measurementPort;
        this.repairOrderPort = repairOrderPort;
    }

    public List<String> audit() { return List.copyOf(audit); }
    private void trail(UUID t, String a, String d) { audit.add(t + "|" + a + "|" + d); }
    private DevelopmentCase require(UUID tenantId, UUID id) {
        DevelopmentCase c = cases.get(id);
        if (c == null || !c.tenantId.equals(tenantId)) throw err("CASE_NOT_FOUND", "caso do empreendedor inexistente neste tenant");
        return c;
    }
    /** Le a taxa do catalogo ED-34 SEM gravar cobranca (validacao previo — D9). */
    private BigDecimal peekFee(DevelopmentCase c, ServiceType type, LocalDate on) {
        BigDecimal amount = fees.fee(c.tenantId, type.name(), on);
        if (amount == null) throw err("FEE_NOT_IN_CATALOG", "tipo " + type + " sem taxa no catalogo da ED-34");
        return amount;
    }
    private FeeCharge addCharge(DevelopmentCase c, ServiceType type, BigDecimal amount) {
        FeeCharge f = new FeeCharge(UUID.randomUUID(), type, amount, false, null);
        c.addCharge(f); return f;
    }
    private FeeCharge charge(DevelopmentCase c, ServiceType type, LocalDate on) {
        return addCharge(c, type, peekFee(c, type, on));
    }
    /** D7: agenda o vencimento da etapa que se inicia; parametro ausente = recusa ANTES de mutar. */
    private LocalDate stageDue(UUID tenantId, Stage entered, LocalDate enteredOn) {
        return enteredOn.plusDays(params.stageDeadlineDays(tenantId, entered.name()));
    }

    // ---- Nascimento: protocolo da P1.36 -> OS do empreendedor (ADR-0039) ----
    public DevelopmentCase openFromProtocol(UUID tenantId, UUID protocolId, String developerName,
                                            DevelopmentKind kind, int plannedUnits) {
        req(protocolId != null, "PROTOCOL_REQUIRED", "solicitacao do empreendedor nasce como protocolo da P1.36: protocolo obrigatorio");
        req(protocolPort.exists(tenantId, protocolId), "PROTOCOL_NOT_FOUND",
                "protocolo nao encontrado na P1.36 (D5): " + protocolId);
        req(developerName != null && !developerName.isBlank(), "DEVELOPER_REQUIRED", "empreendedor obrigatorio");
        req(kind != null, "KIND_REQUIRED", "tipo do empreendimento obrigatorio (loteamento/condominio)");
        req(plannedUnits > 0, "UNITS_INVALID", "empreendimento exige numero de unidades positivo");
        DevelopmentCase c = new DevelopmentCase(UUID.randomUUID(), tenantId, protocolId, developerName, kind, plannedUnits);
        cases.put(c.id, c);
        trail(tenantId, "DEVELOPMENT_OPENED_FROM_PROTOCOL", "protocol=" + protocolId + " units=PROVISIONAL:" + plannedUnits);
        return c;
    }

    // ---- Estudo de viabilidade e diretrizes ----
    public FeeCharge startFeasibilityStudy(UUID tenantId, UUID caseId, LocalDate requestedOn) {
        DevelopmentCase c = require(tenantId, caseId);
        req(c.stage() == Stage.PROTOCOL_OPENED, "STAGE_INVALID", "estudo exige caso recem-protocolado");
        req(requestedOn != null, "STUDY_DATE_REQUIRED", "estudo de viabilidade exige a data da solicitacao");
        // D9 (atomico): confere parametro x catalogo e agenda os prazos ANTES de gravar qualquer cobranca.
        BigDecimal paramFee = params.studyFee(tenantId);
        BigDecimal catalogFee = peekFee(c, ServiceType.DEVELOPMENT_FEASIBILITY_STUDY, requestedOn);
        req(catalogFee.compareTo(paramFee) == 0, "STUDY_FEE_DIVERGES_FROM_PARAMETER",
                "taxa do catalogo ED-34 (" + catalogFee + ") diverge do parametro developer.study_fee (" + paramFee + ")");
        LocalDate feasibilityDue = requestedOn.plusDays(params.feasibilityDeadlineDays(tenantId));
        LocalDate due = stageDue(tenantId, Stage.FEASIBILITY_STUDY, requestedOn);
        FeeCharge f = addCharge(c, ServiceType.DEVELOPMENT_FEASIBILITY_STUDY, catalogFee);
        c.setFeasibilityDueDate(feasibilityDue);
        c.enterStage(Stage.FEASIBILITY_STUDY, due);
        trail(tenantId, "FEASIBILITY_STARTED", "case=" + caseId + " due=" + feasibilityDue + " stageDue=" + due);
        return f;
    }

    public void issueGuidelines(UUID tenantId, UUID caseId, TechnicalOpinion opinion, LocalDate concludedOn) {
        DevelopmentCase c = require(tenantId, caseId);
        req(c.stage() == Stage.FEASIBILITY_STUDY, "STAGE_INVALID", "diretrizes exigem estudo em curso");
        req(opinion != null, "OPINION_REQUIRED", "diretrizes exigem parecer tecnico (rede, pressao, diametro)");
        req(concludedOn != null, "GUIDELINES_DATE_REQUIRED", "diretrizes exigem a data de conclusao");
        LocalDate due = stageDue(tenantId, Stage.GUIDELINES_ISSUED, concludedOn);
        boolean late = concludedOn.isAfter(c.feasibilityDueDate());
        c.setFeasibility(opinion, c.feasibilityDueDate(), concludedOn, late);
        c.enterStage(Stage.GUIDELINES_ISSUED, due);
        trail(tenantId, "GUIDELINES_ISSUED", "case=" + caseId + " late=" + late);
    }

    // ---- Analise e aprovacao do projeto de rede ----
    public FeeCharge approveProject(UUID tenantId, UUID caseId, String projectRef, Set<String> featureIds, LocalDate on) {
        DevelopmentCase c = require(tenantId, caseId);
        req(c.stage() == Stage.GUIDELINES_ISSUED, "STAGE_INVALID", "aprovacao de projeto exige diretrizes emitidas");
        req(projectRef != null && !projectRef.isBlank(), "PROJECT_REF_REQUIRED", "projeto de rede exige referencia/versao");
        req(featureIds != null && !featureIds.isEmpty(), "PROJECT_FEATURES_REQUIRED", "projeto aprovado exige as feicoes da rede");
        LocalDate due = stageDue(tenantId, Stage.PROJECT_APPROVED, on);
        FeeCharge f = charge(c, ServiceType.DEVELOPMENT_PROJECT_APPROVAL, on);
        c.setProject(projectRef, featureIds);
        c.enterStage(Stage.PROJECT_APPROVED, due);
        trail(tenantId, "PROJECT_APPROVED", "case=" + caseId + " features=" + featureIds.size());
        return f;
    }

    // ---- Obra do empreendedor: acompanhamento e fiscalizacao ----
    public void startWorks(UUID tenantId, UUID caseId, LocalDate on) {
        DevelopmentCase c = require(tenantId, caseId);
        req(c.stage() == Stage.PROJECT_APPROVED, "STAGE_INVALID", "obra do empreendedor exige projeto aprovado");
        req(on != null, "WORKS_DATE_REQUIRED", "inicio da obra exige a data (prazo da etapa — D7)");
        LocalDate due = stageDue(tenantId, Stage.WORKS_IN_PROGRESS, on);
        c.enterStage(Stage.WORKS_IN_PROGRESS, due);
        trail(tenantId, "DEVELOPER_WORKS_STARTED", "case=" + caseId);
    }

    public void registerSupervision(UUID tenantId, UUID caseId, SupervisionVisit visit, LocalDate on) {
        DevelopmentCase c = require(tenantId, caseId);
        req(c.stage() == Stage.WORKS_IN_PROGRESS, "STAGE_INVALID", "fiscalizacao exige obra em andamento");
        req(visit != null, "VISIT_REQUIRED", "fiscalizacao exige a visita registrada");
        if (c.visits().isEmpty()) charge(c, ServiceType.DEVELOPMENT_WORKS_SUPERVISION, on);
        c.addVisit(visit);
        trail(tenantId, "SUPERVISION_VISIT", "case=" + caseId + " conforming=" + visit.conforming());
    }

    // ---- As-built (ED-53), vistoria e recebimento ----
    public void submitAsBuilt(UUID tenantId, UUID caseId, UUID packageId, LocalDate on) {
        DevelopmentCase c = require(tenantId, caseId);
        req(c.stage() == Stage.WORKS_IN_PROGRESS, "STAGE_INVALID", "as-built exige obra em andamento");
        req(!c.visits().isEmpty(), "SUPERVISION_REQUIRED", "recebimento exige ao menos uma fiscalizacao registrada");
        req(on != null, "ASBUILT_DATE_REQUIRED", "submissao do as-built exige a data (prazo da etapa — D7)");
        AsBuiltView v = asBuiltPort.find(tenantId, packageId)
                .orElseThrow(() -> err("ASBUILT_NOT_FOUND", "pacote as-built nao encontrado na ED-53: " + packageId));
        req("SUBMITTED".equals(v.status()) || "APPROVED".equals(v.status()),
                "ASBUILT_NOT_SUBMITTED", "pacote as-built da ED-53 nao submetido (status=" + v.status() + ")");
        // D4: o aceite nasce AMARRADO as feicoes aprovadas — pacote alheio/disjunto e recusado ja aqui.
        req(v.featureIds().containsAll(c.approvedFeatureIds()), "ASBUILT_FEATURES_DIVERGE",
                "pacote as-built da ED-53 nao contem todas as feicoes APROVADAS do projeto: aceite recusado sobre pacote alheio");
        LocalDate due = stageDue(tenantId, Stage.AS_BUILT_SUBMITTED, on);
        c.setAsBuilt(v);
        c.enterStage(Stage.AS_BUILT_SUBMITTED, due);
        trail(tenantId, "AS_BUILT_SUBMITTED", "case=" + caseId + " package=" + packageId + " v" + v.version());
    }

    /** Vistoria de recebimento: TODOS os itens do checklist CAD-71 conformes. */
    public FeeCharge performAcceptanceInspection(UUID tenantId, UUID caseId, Map<String, Boolean> results, LocalDate on) {
        DevelopmentCase c = require(tenantId, caseId);
        req(c.stage() == Stage.AS_BUILT_SUBMITTED, "STAGE_INVALID", "vistoria de recebimento exige as-built submetido");
        // D4 (defesa em profundidade): a vistoria reconfere o vinculo as-built x feicoes aprovadas.
        req(c.asBuilt() != null && c.asBuilt().featureIds().containsAll(c.approvedFeatureIds()),
                "ASBUILT_FEATURES_DIVERGE", "as-built do caso nao contem as feicoes aprovadas: aceite recusado");
        List<String> required = params.acceptanceChecklist(tenantId);
        for (String item : required) {
            Boolean r = results == null ? null : results.get(item);
            req(r != null, "CHECKLIST_ITEM_MISSING", "checklist de recebimento (CAD-71) sem resultado para: " + item);
            req(r, "CHECKLIST_ITEM_FAILED", "checklist de recebimento (CAD-71) reprovou: " + item);
        }
        LocalDate due = stageDue(tenantId, Stage.ACCEPTANCE_APPROVED, on);
        FeeCharge f = charge(c, ServiceType.DEVELOPMENT_NETWORK_ACCEPTANCE, on);
        c.approveAcceptance(results);
        c.enterStage(Stage.ACCEPTANCE_APPROVED, due);
        trail(tenantId, "NETWORK_ACCEPTED", "case=" + caseId + " checklist=" + required.size() + " itens");
        return f;
    }

    // ---- Termo de transferencia/cessao + incorporacao ao patrimonio ----
    public TransferTerm signTransfer(UUID tenantId, UUID caseId, int version, Set<String> featureIds,
                                     String signedBy, BigDecimal eligibleNetworkCost) {
        DevelopmentCase c = require(tenantId, caseId);
        req(c.stage() == Stage.ACCEPTANCE_APPROVED, "STAGE_INVALID", "termo de transferencia exige aceite da rede");
        req(version > 0, "TRANSFER_VERSION_REQUIRED", "termo de transferencia exige versao positiva");
        // D6: o termo tem autor — assinante nulo/em branco e recusado (o record tambem valida).
        req(signedBy != null && !signedBy.isBlank(), "TRANSFER_SIGNER_REQUIRED", "termo de transferencia exige o assinante (signedBy)");
        req(featureIds != null && featureIds.equals(c.approvedFeatureIds()), "TRANSFER_FEATURES_DIVERGE",
                "termo vinculado as feicoes APROVADAS: feicoes do termo devem ser exatamente as do projeto aprovado");
        req(c.asBuilt().featureIds().containsAll(featureIds), "TRANSFER_FEATURE_NOT_IN_ASBUILT",
                "feicao do termo ausente no pacote as-built da ED-53");
        req(eligibleNetworkCost != null && eligibleNetworkCost.signum() > 0, "NETWORK_COST_REQUIRED",
                "incorporacao/ressarcimento exigem o custo elegivel da rede declarado");
        TransferTerm term = new TransferTerm(UUID.randomUUID(), version, Set.copyOf(featureIds), signedBy, LocalDateTime.now());
        LocalDate due = stageDue(tenantId, Stage.TRANSFER_SIGNED, term.signedAt().toLocalDate());
        c.setTransfer(term, eligibleNetworkCost);
        c.enterStage(Stage.TRANSFER_SIGNED, due);
        trail(tenantId, "TRANSFER_SIGNED_AND_ASSETS_INCORPORATED", "case=" + caseId + " v" + version + " assets=" + featureIds.size());
        return term;
    }

    /** Ressarcimento conforme a agencia (Lei 11.445, art. 18-A): sem regra, recusa. */
    public BigDecimal reimbursement(UUID tenantId, UUID caseId) {
        DevelopmentCase c = require(tenantId, caseId);
        req(c.transfer() != null, "TRANSFER_REQUIRED", "ressarcimento exige o termo de transferencia assinado");
        return params.reimbursementRule(tenantId).amountFor(c.eligibleNetworkCost());
    }

    // ---- Pagamento e a TRAVA da interligacao (NR 11/ANA, arts. 25 a 30) ----
    public void registerPayment(UUID tenantId, UUID caseId, UUID chargeId) {
        DevelopmentCase c = require(tenantId, caseId);
        req(c.markChargePaid(chargeId, LocalDateTime.now()), "CHARGE_NOT_FOUND", "cobranca inexistente neste caso: " + chargeId);
    }
    public void payAllCharges(UUID tenantId, UUID caseId) {
        DevelopmentCase c = require(tenantId, caseId);
        c.markAllChargesPaid(LocalDateTime.now());
    }

    public FeeCharge releaseInterconnection(UUID tenantId, UUID caseId, LocalDate on) {
        DevelopmentCase c = require(tenantId, caseId);
        // D3: guarda de etapa — liberacao e ato unico; repetir e recusado e NAO duplica a cobranca.
        req(c.stage() != Stage.INTERCONNECTION_RELEASED && c.stage() != Stage.CLOSED,
                "STAGE_INVALID", "interligacao ja liberada para este caso: liberacao duplicada recusada");
        List<String> missing = new ArrayList<>();
        if (!c.acceptanceApproved()) missing.add("aceite da rede (vistoria/checklist CAD-71)");
        if (!c.allFeesPaid()) missing.add("pagamento integral das taxas DEVELOPMENT_*");
        if (c.transfer() == null) missing.add("cessao (termo de transferencia assinado)");
        req(missing.isEmpty(), "INTERCONNECTION_BLOCKED",
                "interligacao bloqueada (NR 11/ANA, arts. 25 a 30) — pendente: " + String.join("; ", missing));
        req(c.stage() == Stage.TRANSFER_SIGNED, "STAGE_INVALID", "liberacao da interligacao exige o termo de transferencia assinado");
        LocalDate due = stageDue(tenantId, Stage.INTERCONNECTION_RELEASED, on);
        // D2: a taxa DEVELOPMENT_INTERCONNECTION nasce JUNTO da liberacao; o encerramento a exige paga.
        FeeCharge f = charge(c, ServiceType.DEVELOPMENT_INTERCONNECTION, on);
        c.enterStage(Stage.INTERCONNECTION_RELEASED, due);
        trail(tenantId, "INTERCONNECTION_RELEASED", "case=" + caseId);
        return f;
    }

    public void closeCase(UUID tenantId, UUID caseId) {
        DevelopmentCase c = require(tenantId, caseId);
        req(c.stage() == Stage.INTERCONNECTION_RELEASED, "STAGE_INVALID", "encerramento exige interligacao liberada");
        // D2: reconferencia final — NENHUMA cobranca DEVELOPMENT_* pode restar nao paga ao fechar.
        req(c.allFeesPaid(), "CLOSE_BLOCKED_UNPAID_FEES",
                "encerramento bloqueado: pagamento integral de TODAS as taxas DEVELOPMENT_* (inclusive a de interligacao) e pre-condicao para ativar as unidades");
        c.activateUnitLot();
        c.enterStage(Stage.CLOSED, null);
        trail(tenantId, "DEVELOPMENT_CLOSED", "case=" + caseId + " units=ACTIVE:" + c.plannedUnits);
    }

    public DevelopmentCase caseOf(UUID tenantId, UUID caseId) { return require(tenantId, caseId); }

    // ---- Obras do prestador ----
    public ProviderWork createProviderWork(ProviderWork w) {
        if (w.contractorMeasurementIdOrNull() != null)
            req(measurementPort.exists(w.tenantId(), w.contractorMeasurementIdOrNull()), "MEASUREMENT_NOT_FOUND",
                    "medicao nao encontrada na ED-37 (D5): " + w.contractorMeasurementIdOrNull());
        works.put(w.id(), w); trail(w.tenantId(), "PROVIDER_WORK_CREATED", w.title()); return w;
    }
    public ProviderWork linkContractorMeasurement(UUID tenantId, UUID workId, UUID measurementId, BigDecimal amount) {
        ProviderWork w = works.get(workId);
        req(w != null && w.tenantId().equals(tenantId), "WORK_NOT_FOUND", "obra do prestador inexistente neste tenant");
        req(measurementId != null, "MEASUREMENT_REQUIRED", "medicao da ED-37 exige id");
        req(measurementPort.exists(tenantId, measurementId), "MEASUREMENT_NOT_FOUND",
                "medicao nao encontrada na ED-37 (D5): " + measurementId);
        ProviderWork u = w.withMeasurement(measurementId, amount); works.put(workId, u); return u;
    }
    public ProviderWork superviseProviderWork(UUID tenantId, UUID workId, SupervisionVisit v) {
        ProviderWork w = works.get(workId);
        req(w != null && w.tenantId().equals(tenantId), "WORK_NOT_FOUND", "obra do prestador inexistente neste tenant");
        ProviderWork u = w.withSupervision(v); works.put(workId, u); return u;
    }

    // ---- Recomposicao de pavimento derivada da OS de reparo ----
    public PavementRestoration derivePavementRestoration(UUID tenantId, UUID repairOrderId, PavementType type, BigDecimal areaM2) {
        req(repairOrderId != null, "REPAIR_ORDER_REQUIRED", "recomposicao deriva de uma OS de reparo: id obrigatorio");
        req(repairOrderPort.exists(tenantId, repairOrderId), "REPAIR_ORDER_NOT_FOUND",
                "OS de reparo nao encontrada (D5): " + repairOrderId);
        PavementRestoration r = new PavementRestoration(UUID.randomUUID(), tenantId, repairOrderId, type, areaM2, null);
        restorations.put(r.id(), r);
        trail(tenantId, "PAVEMENT_RESTORATION_DERIVED", "repair=" + repairOrderId + " type=" + type);
        return r;
    }
    public PavementRestoration linkRestorationMeasurement(UUID tenantId, UUID restorationId, UUID measurementId) {
        PavementRestoration r = restorations.get(restorationId);
        req(r != null && r.tenantId().equals(tenantId), "RESTORATION_NOT_FOUND", "recomposicao inexistente neste tenant");
        req(measurementId != null, "MEASUREMENT_REQUIRED", "medicao da empreiteira (ED-37) exige id");
        req(measurementPort.exists(tenantId, measurementId), "MEASUREMENT_NOT_FOUND",
                "medicao nao encontrada na ED-37 (D5): " + measurementId);
        PavementRestoration u = r.withMeasurement(measurementId); restorations.put(restorationId, u); return u;
    }

    // ---- Licenca e consulta de interferencia registradas na OS (OPE-27) ----
    public FieldOrderAttachments registerAttachments(UUID tenantId, UUID orderId,
                                                     RoadInterventionLicense license, InterferenceConsultation consultation) {
        req(orderId != null, "ORDER_REQUIRED", "anexos exigem a OS");
        // D10: registro vazio nao e registro — licenca E consulta sao obrigatorias neste ato.
        req(license != null, "LICENSE_REQUIRED", "registro na OS exige a licenca de intervencao em via publica");
        req(consultation != null, "CONSULTATION_REQUIRED", "registro na OS exige a consulta de interferencia registrada");
        FieldOrderAttachments a = new FieldOrderAttachments(orderId, license, consultation);
        attachments.put(orderId, a);
        trail(tenantId, "ORDER_ATTACHMENTS_REGISTERED", "order=" + orderId
                + " license=true interferenceConsult=true nationalIntegration=false(OPE-27)");
        return a;
    }
    public FieldOrderAttachments attachmentsOf(UUID orderId) { return attachments.get(orderId); }

    static void req(boolean ok, String code, String msg) { if (!ok) throw err(code, msg); }
    static DeveloperWorksException err(String code, String msg) { return new DeveloperWorksException(code, msg); }
}
