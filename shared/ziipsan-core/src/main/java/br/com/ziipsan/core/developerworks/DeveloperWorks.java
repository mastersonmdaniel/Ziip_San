package br.com.ziipsan.core.developerworks;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * ED-84 — Fluxo do empreendedor e obras (ADR-0039; CAD-71; OPE-27).
 *
 * Regras centrais (spec integra, tarefas-onda-B.json, id ED-84):
 *  - o fluxo do empreendedor e INTEIRO como OS (ADR-0039) e nasce de um
 *    PROTOCOLO externo da P1.36 — sem protocolo, nao ha caso; a EXISTENCIA
 *    do protocolo e conferida pela porta ProtocolPort (correcao D5);
 *  - tipos de servico DEVELOPMENT_* cobrados pelo catalogo de precos da
 *    ED-34 (porta ServiceFeeCatalogPort); o parametro developer.study_fee
 *    (classe d, valor da agencia) e OBRIGATORIO para orcar o estudo —
 *    sem ele o servico RECUSA operar (nenhum valor de fabrica inventado);
 *  - lote de unidades do empreendimento nasce PROVISIONAL (ADR-0039, item 3);
 *  - recebimento da rede exige o pacote as-built da ED-53 (porta AsBuiltPort)
 *    CONTENDO as feicoes aprovadas do projeto (correcao D4), checklist
 *    works.network_acceptance_checklist (CAD-71) integralmente conforme e
 *    termo de transferencia COM VERSAO e ASSINANTE (correcao D6), vinculado
 *    as feicoes APROVADAS do projeto e contidas no as-built; assinar o termo
 *    incorpora os bens ao patrimonio do prestador;
 *  - INTERLIGACAO so e liberada com aceite + pagamento + cessao
 *    (NR 11/ANA, arts. 25 a 30) — os tres requisitos sao conferidos um a um
 *    e a recusa nomeia cada pendencia; a liberacao tem guarda de etapa e
 *    ocorre UMA unica vez (correcao D3), a taxa de interligacao nasce junto
 *    da liberacao e o ENCERRAMENTO reconfere o pagamento integral de TODAS
 *    as cobrancas — fechar devendo e impossivel (correcao D2);
 *  - {@link DevelopmentCase} e ENCAPSULADO (correcao D1): nenhum campo
 *    publico mutavel, nenhuma referencia viva devolvida; aceite, pagamento,
 *    cessao e etapa so mudam pelos metodos guardados do servico;
 *  - ressarcimento SOMENTE pela regra parametrizada da agencia
 *    (developer.reimbursement_rule; Lei 11.445, art. 18-A) — sem parametro,
 *    recusa; prazos por etapa (developer.deadline_days.&lt;etapa&gt;, correcao
 *    D7) e works.feasibility_deadline_days sao parametros da agencia
 *    (classe d), sem padrao inventado, e os prazos por etapa sao APLICADOS
 *    a cada transicao (vencimento registrado em stageDueDates);
 *  - obras do prestador: cronograma, orcamento, medicao de empreiteira
 *    vinculada a ED-37 PELA PORTA ContractorMeasurementPort (correcao D5)
 *    e fiscalizacao;
 *  - OS de recomposicao de pavimento DERIVADA da OS de reparo — existencia
 *    conferida pela porta RepairOrderPort (correcao D5) —, com tipo de
 *    pavimento (SG-138, SG-139) e medicao da empreiteira;
 *  - licenca de intervencao em via publica e consulta de interferencia
 *    REGISTRADAS na OS — ambas obrigatorias no registro (correcao D10) —
 *    SEM integracao nacional de consulta antes de escavar: a integracao
 *    NAO foi confirmada (OPE-27) e o registro guarda
 *    nationalIntegrationConfirmed = false, sempre.
 */
public final class DeveloperWorks {
    private DeveloperWorks() {}

    public static final class DeveloperWorksException extends RuntimeException {
        public final String code;
        public DeveloperWorksException(String code, String msg) { super(msg); this.code = code; }
    }

    static DeveloperWorksException err(String code, String msg) { return new DeveloperWorksException(code, msg); }
    static void req(boolean ok, String code, String msg) { if (!ok) throw err(code, msg); }

    // ------------------------------------------------------------------
    // Parametros da agencia (classe d): ausencia = recusa, nunca padrao.
    // ------------------------------------------------------------------
    public interface ParameterPort {
        Optional<String> raw(UUID tenantId, String key);
    }

    public static final String P_STUDY_FEE = "developer.study_fee";
    public static final String P_REIMBURSEMENT_RULE = "developer.reimbursement_rule";
    public static final String P_FEASIBILITY_DEADLINE = "works.feasibility_deadline_days";
    public static final String P_ACCEPTANCE_CHECKLIST = "works.network_acceptance_checklist";
    public static String stageDeadlineKey(String stage) { return "developer.deadline_days." + stage; }

    /** Regra de ressarcimento da agencia: NONE | FULL | PERCENTAGE:&lt;pct&gt;. */
    public record ReimbursementRule(Mode mode, BigDecimal percentageOrNull) {
        public enum Mode { NONE, FULL, PERCENTAGE }
        public static ReimbursementRule parse(String raw) {
            req(raw != null && !raw.isBlank(), "REIMBURSEMENT_RULE_INVALID", "regra de ressarcimento vazia");
            String s = raw.trim().toUpperCase();
            if (s.equals("NONE")) return new ReimbursementRule(Mode.NONE, null);
            if (s.equals("FULL")) return new ReimbursementRule(Mode.FULL, null);
            if (s.startsWith("PERCENTAGE:")) {
                BigDecimal pct = new BigDecimal(s.substring("PERCENTAGE:".length()).trim());
                req(pct.signum() > 0 && pct.compareTo(new BigDecimal("100")) <= 0,
                        "REIMBURSEMENT_RULE_INVALID", "percentual de ressarcimento fora de (0,100]: " + pct);
                return new ReimbursementRule(Mode.PERCENTAGE, pct);
            }
            throw err("REIMBURSEMENT_RULE_INVALID", "regra de ressarcimento nao reconhecida: " + raw);
        }
        public BigDecimal amountFor(BigDecimal eligibleCost) {
            return switch (mode) {
                case NONE -> BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                case FULL -> eligibleCost.setScale(2, RoundingMode.HALF_UP);
                case PERCENTAGE -> eligibleCost.multiply(percentageOrNull)
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            };
        }
    }

    public static final class Parameters {
        private final ParameterPort port;
        public Parameters(ParameterPort port) { this.port = port; }

        private String required(UUID t, String key) {
            return port.raw(t, key).filter(v -> !v.isBlank()).orElseThrow(() ->
                    err("PARAMETER_MISSING", "parametro da agencia ausente (sem valor de fabrica — ED-84 nao inventa): " + key));
        }
        public BigDecimal studyFee(UUID t) {
            BigDecimal v = new BigDecimal(required(t, P_STUDY_FEE).trim());
            req(v.signum() >= 0, "PARAMETER_INVALID", P_STUDY_FEE + " negativo");
            return v;
        }
        public ReimbursementRule reimbursementRule(UUID t) { return ReimbursementRule.parse(required(t, P_REIMBURSEMENT_RULE)); }
        public int feasibilityDeadlineDays(UUID t) {
            int v = Integer.parseInt(required(t, P_FEASIBILITY_DEADLINE).trim());
            req(v > 0, "PARAMETER_INVALID", P_FEASIBILITY_DEADLINE + " deve ser positivo");
            return v;
        }
        public List<String> acceptanceChecklist(UUID t) {
            List<String> items = new ArrayList<>();
            for (String p : required(t, P_ACCEPTANCE_CHECKLIST).split(";")) if (!p.isBlank()) items.add(p.trim());
            req(!items.isEmpty(), "PARAMETER_INVALID", P_ACCEPTANCE_CHECKLIST + " vazio (CAD-71)");
            return List.copyOf(items);
        }
        /** Prazo por etapa (classe d): ausente = recusa ao agendar a etapa (D7). */
        public int stageDeadlineDays(UUID t, String stage) {
            int v = Integer.parseInt(required(t, stageDeadlineKey(stage)).trim());
            req(v > 0, "PARAMETER_INVALID", stageDeadlineKey(stage) + " deve ser positivo");
            return v;
        }
    }

    // ------------------------------------------------------------------
    // Portas das dependencias (ED-34, ED-53, P1.36, ED-37, OS de reparo)
    // ------------------------------------------------------------------
    /** ED-34: preco vigente do tipo de servico no catalogo do tenant. */
    public interface ServiceFeeCatalogPort {
        BigDecimal fee(UUID tenantId, String serviceTypeCode, LocalDate on);
    }

    /** ED-53: pacote as-built (GIS-09). Status minimo: SUBMITTED. */
    public interface AsBuiltPort {
        Optional<AsBuiltView> find(UUID tenantId, UUID packageId);
    }
    public record AsBuiltView(UUID packageId, int version, String status, Set<String> featureIds) {
        public AsBuiltView {
            req(packageId != null, "ASBUILT_REQUIRED", "pacote as-built exige id (ED-53)");
            req(status != null && !status.isBlank(), "ASBUILT_STATUS_REQUIRED", "pacote as-built exige status (ED-53)");
            featureIds = featureIds == null ? Set.of() : Set.copyOf(featureIds);
        }
    }

    /** P1.36: existencia do protocolo externo que faz nascer o caso (D5). */
    public interface ProtocolPort {
        boolean exists(UUID tenantId, UUID protocolId);
    }

    /** ED-37: existencia da medicao de empreiteira vinculada (D5). */
    public interface ContractorMeasurementPort {
        boolean exists(UUID tenantId, UUID measurementId);
    }

    /** OS de reparo: existencia da ordem da qual a recomposicao deriva (D5). */
    public interface RepairOrderPort {
        boolean exists(UUID tenantId, UUID repairOrderId);
    }

    // ------------------------------------------------------------------
    // Dominio do fluxo do empreendedor
    // ------------------------------------------------------------------
    public enum ServiceType {
        DEVELOPMENT_FEASIBILITY_STUDY, DEVELOPMENT_PROJECT_APPROVAL,
        DEVELOPMENT_WORKS_SUPERVISION, DEVELOPMENT_NETWORK_ACCEPTANCE,
        DEVELOPMENT_INTERCONNECTION
    }
    public enum DevelopmentKind { LOTEAMENTO, CONDOMINIO }
    public enum Stage {
        PROTOCOL_OPENED, FEASIBILITY_STUDY, GUIDELINES_ISSUED, PROJECT_APPROVED,
        WORKS_IN_PROGRESS, AS_BUILT_SUBMITTED, ACCEPTANCE_APPROVED,
        TRANSFER_SIGNED, INTERCONNECTION_RELEASED, CLOSED, REJECTED
    }

    /** Parecer tecnico do estudo de viabilidade/diretrizes: rede, pressao, diametro. */
    public record TechnicalOpinion(String networkPoint, BigDecimal pressureKpa, BigDecimal diameterMm,
                                   String conclusion, String author, LocalDate issuedOn) {
        public TechnicalOpinion {
            req(networkPoint != null && !networkPoint.isBlank(), "OPINION_NETWORK_REQUIRED", "parecer exige o ponto/dados de rede");
            req(pressureKpa != null && pressureKpa.signum() > 0, "OPINION_PRESSURE_REQUIRED", "parecer exige pressao positiva");
            req(diameterMm != null && diameterMm.signum() > 0, "OPINION_DIAMETER_REQUIRED", "parecer exige diametro positivo");
            req(conclusion != null && !conclusion.isBlank(), "OPINION_CONCLUSION_REQUIRED", "parecer exige conclusao");
            req(author != null && !author.isBlank(), "OPINION_AUTHOR_REQUIRED", "parecer exige o responsavel tecnico");
            req(issuedOn != null, "OPINION_DATE_REQUIRED", "parecer exige data");
        }
    }

    public record FeeCharge(UUID id, ServiceType type, BigDecimal amount, boolean paid, LocalDateTime paidAtOrNull) {
        public FeeCharge withPaid(LocalDateTime at) { return new FeeCharge(id, type, amount, true, at); }
    }

    public record SupervisionVisit(LocalDate date, String inspector, boolean conforming, String findings) {
        public SupervisionVisit {
            req(date != null, "VISIT_DATE_REQUIRED", "fiscalizacao exige data");
            req(inspector != null && !inspector.isBlank(), "VISIT_INSPECTOR_REQUIRED", "fiscalizacao exige o fiscal");
            req(findings != null && !findings.isBlank(), "VISIT_FINDINGS_REQUIRED", "fiscalizacao exige o relato");
        }
    }

    /** Termo de transferencia/cessao com versao, vinculado as feicoes aprovadas. Assinante obrigatorio (D6). */
    public record TransferTerm(UUID id, int version, Set<String> featureIds, String signedBy, LocalDateTime signedAt) {
        public TransferTerm {
            req(id != null, "TRANSFER_ID_REQUIRED", "termo de transferencia exige id");
            req(version > 0, "TRANSFER_VERSION_REQUIRED", "termo de transferencia exige versao positiva");
            req(featureIds != null && !featureIds.isEmpty(), "TRANSFER_FEATURES_REQUIRED", "termo exige as feicoes transferidas");
            req(signedBy != null && !signedBy.isBlank(), "TRANSFER_SIGNER_REQUIRED", "termo de transferencia exige o assinante (signedBy)");
            req(signedAt != null, "TRANSFER_DATE_REQUIRED", "termo de transferencia exige a data de assinatura");
            featureIds = Set.copyOf(featureIds);
        }
    }

    /** Bem incorporado ao patrimonio do prestador a partir do termo. */
    public record IncorporatedAsset(String featureId, UUID transferTermId, LocalDateTime incorporatedAt) {}

    /**
     * Caso do empreendedor — ENCAPSULADO (D1). Identidade e imutaveis em
     * campos publicos finais; TODO o estado mutavel e privado e so muda
     * pelos metodos package-private abaixo, invocados exclusivamente pelo
     * DeveloperWorksService apos as suas guardas. Leituras publicas
     * devolvem copias imutaveis — nunca a referencia viva das colecoes.
     */
    public static final class DevelopmentCase {
        public final UUID id, tenantId, protocolId;
        public final String developerName;
        public final DevelopmentKind kind;
        public final int plannedUnits;
        /** Lote de unidades PROVISIONAL do empreendimento (ADR-0039, item 3). */
        private String unitLotStatus = "PROVISIONAL";
        private Stage stage = Stage.PROTOCOL_OPENED;
        private TechnicalOpinion opinion;
        private LocalDate feasibilityDueDate, feasibilityDoneDate;
        private boolean feasibilityLate;
        private Set<String> approvedFeatureIds = Set.of();
        private String projectRef;
        private final List<SupervisionVisit> visits = new ArrayList<>();
        private AsBuiltView asBuilt;
        private final Map<String, Boolean> checklistResults = new LinkedHashMap<>();
        private boolean acceptanceApproved;
        private TransferTerm transfer;
        private final List<IncorporatedAsset> incorporatedAssets = new ArrayList<>();
        private final List<FeeCharge> charges = new ArrayList<>();
        private BigDecimal eligibleNetworkCost;
        /** Vencimento por etapa, agendado a cada transicao (D7). Chave: nome da etapa. */
        private final Map<String, LocalDate> stageDueDates = new LinkedHashMap<>();
        DevelopmentCase(UUID id, UUID tenantId, UUID protocolId, String developerName, DevelopmentKind kind, int plannedUnits) {
            this.id = id; this.tenantId = tenantId; this.protocolId = protocolId;
            this.developerName = developerName; this.kind = kind; this.plannedUnits = plannedUnits;
        }

        // ---- leituras publicas (copias/visoes imutaveis) ----
        public String unitLotStatus() { return unitLotStatus; }
        public Stage stage() { return stage; }
        public TechnicalOpinion opinion() { return opinion; }
        public LocalDate feasibilityDueDate() { return feasibilityDueDate; }
        public LocalDate feasibilityDoneDate() { return feasibilityDoneDate; }
        public boolean feasibilityLate() { return feasibilityLate; }
        public Set<String> approvedFeatureIds() { return approvedFeatureIds; }
        public String projectRef() { return projectRef; }
        public List<SupervisionVisit> visits() { return List.copyOf(visits); }
        public AsBuiltView asBuilt() { return asBuilt; }
        public Map<String, Boolean> checklistResults() { return Map.copyOf(checklistResults); }
        public boolean acceptanceApproved() { return acceptanceApproved; }
        public TransferTerm transfer() { return transfer; }
        public List<IncorporatedAsset> incorporatedAssets() { return List.copyOf(incorporatedAssets); }
        public List<FeeCharge> charges() { return List.copyOf(charges); }
        public BigDecimal eligibleNetworkCost() { return eligibleNetworkCost; }
        public Map<String, LocalDate> stageDueDates() { return Map.copyOf(stageDueDates); }
        public boolean allFeesPaid() { return !charges.isEmpty() && charges.stream().allMatch(FeeCharge::paid); }

        // ---- mutacoes internas: SOMENTE o DeveloperWorksService, apos as guardas ----
        void enterStage(Stage newStage, LocalDate dueOrNull) {
            this.stage = newStage;
            if (dueOrNull != null) this.stageDueDates.put(newStage.name(), dueOrNull);
        }
        void setFeasibility(TechnicalOpinion opinion, LocalDate dueDate, LocalDate doneDate, boolean late) {
            this.opinion = opinion; this.feasibilityDueDate = dueDate;
            this.feasibilityDoneDate = doneDate; this.feasibilityLate = late;
        }
        void setFeasibilityDueDate(LocalDate dueDate) { this.feasibilityDueDate = dueDate; }
        void setProject(String projectRef, Set<String> featureIds) {
            this.projectRef = projectRef; this.approvedFeatureIds = Set.copyOf(featureIds);
        }
        void addVisit(SupervisionVisit visit) { visits.add(visit); }
        void setAsBuilt(AsBuiltView asBuilt) { this.asBuilt = asBuilt; }
        void approveAcceptance(Map<String, Boolean> results) {
            this.checklistResults.putAll(results); this.acceptanceApproved = true;
        }
        void setTransfer(TransferTerm term, BigDecimal eligibleNetworkCost) {
            this.transfer = term; this.eligibleNetworkCost = eligibleNetworkCost;
            for (String fid : term.featureIds())
                this.incorporatedAssets.add(new IncorporatedAsset(fid, term.id(), term.signedAt()));
        }
        void addCharge(FeeCharge charge) { charges.add(charge); }
        boolean markChargePaid(UUID chargeId, LocalDateTime at) {
            for (int i = 0; i < charges.size(); i++)
                if (charges.get(i).id().equals(chargeId)) { charges.set(i, charges.get(i).withPaid(at)); return true; }
            return false;
        }
        void markAllChargesPaid(LocalDateTime at) {
            for (int i = 0; i < charges.size(); i++) charges.set(i, charges.get(i).withPaid(at));
        }
        void activateUnitLot() { this.unitLotStatus = "ACTIVE"; }
    }

    // ------------------------------------------------------------------
    // Obras do prestador e recomposicao de pavimento
    // ------------------------------------------------------------------
    public enum PavementType { ASFALTO, CONCRETO, PAVER_BLOQUETE, PARALELEPIPEDO, SOLO_NATURAL }

    public record ProviderWork(UUID id, UUID tenantId, String title, LocalDate plannedStart, LocalDate plannedEnd,
                               BigDecimal budget, List<String> milestones,
                               UUID contractorMeasurementIdOrNull, BigDecimal measuredAmountOrNull,
                               List<SupervisionVisit> supervision) {
        public ProviderWork {
            req(title != null && !title.isBlank(), "WORK_TITLE_REQUIRED", "obra do prestador exige titulo");
            req(plannedStart != null && plannedEnd != null && !plannedEnd.isBefore(plannedStart),
                    "WORK_SCHEDULE_INVALID", "obra do prestador exige cronograma valido");
            req(budget != null && budget.signum() > 0, "WORK_BUDGET_REQUIRED", "obra do prestador exige orcamento positivo");
            milestones = milestones == null ? List.of() : List.copyOf(milestones);
            supervision = supervision == null ? List.of() : List.copyOf(supervision);
        }
        public ProviderWork withMeasurement(UUID measurementId, BigDecimal amount) {
            req(measurementId != null, "MEASUREMENT_REQUIRED", "medicao da ED-37 exige id");
            req(amount != null && amount.signum() >= 0, "MEASUREMENT_AMOUNT_INVALID", "medicao exige valor nao negativo");
            return new ProviderWork(id, tenantId, title, plannedStart, plannedEnd, budget, milestones,
                    measurementId, amount, supervision);
        }
        public ProviderWork withSupervision(SupervisionVisit v) {
            List<SupervisionVisit> s = new ArrayList<>(supervision); s.add(v);
            return new ProviderWork(id, tenantId, title, plannedStart, plannedEnd, budget, milestones,
                    contractorMeasurementIdOrNull, measuredAmountOrNull, s);
        }
    }

    /** OS de recomposicao derivada da OS de reparo (SG-138, SG-139). */
    public record PavementRestoration(UUID id, UUID tenantId, UUID repairOrderId, PavementType pavementType,
                                      BigDecimal areaM2, UUID contractorMeasurementIdOrNull) {
        public PavementRestoration {
            req(repairOrderId != null, "REPAIR_ORDER_REQUIRED", "recomposicao deriva de uma OS de reparo: id obrigatorio");
            req(pavementType != null, "PAVEMENT_TYPE_REQUIRED", "recomposicao exige o tipo de pavimento (SG-138, SG-139)");
            req(areaM2 != null && areaM2.signum() > 0, "PAVEMENT_AREA_INVALID", "recomposicao exige area positiva");
        }
        public PavementRestoration withMeasurement(UUID m) {
            req(m != null, "MEASUREMENT_REQUIRED", "medicao da empreiteira (ED-37) exige id");
            return new PavementRestoration(id, tenantId, repairOrderId, pavementType, areaM2, m);
        }
    }

    /** Licenca de intervencao em via publica registrada na OS. */
    public record RoadInterventionLicense(String licenseNumber, String authority, LocalDate validFrom, LocalDate validTo) {
        public RoadInterventionLicense {
            req(licenseNumber != null && !licenseNumber.isBlank(), "LICENSE_NUMBER_REQUIRED", "licenca de intervencao exige numero");
            req(authority != null && !authority.isBlank(), "LICENSE_AUTHORITY_REQUIRED", "licenca de intervencao exige o orgao emissor");
            req(validFrom != null && validTo != null && !validTo.isBefore(validFrom), "LICENSE_PERIOD_INVALID", "licenca com vigencia invalida");
        }
    }

    /**
     * Consulta de interferencia REGISTRADA na OS. OPE-27: a integracao
     * nacional de consulta antes de escavar NAO foi confirmada — por isso o
     * registro e manual/declarado e nationalIntegrationConfirmed e sempre
     * false; nenhum servico externo e invocado.
     */
    public record InterferenceConsultation(LocalDate consultedOn, List<String> consultedServices, String note,
                                           boolean nationalIntegrationConfirmed) {
        public InterferenceConsultation {
            req(consultedOn != null, "CONSULT_DATE_REQUIRED", "consulta de interferencia exige data");
            req(consultedServices != null && !consultedServices.isEmpty(), "CONSULT_SERVICES_REQUIRED",
                    "consulta de interferencia exige os servicos consultados");
            req(!nationalIntegrationConfirmed, "OPE27_NATIONAL_INTEGRATION_UNCONFIRMED",
                    "integracao nacional de consulta antes de escavar nao confirmada (OPE-27): registro manual apenas");
            consultedServices = List.copyOf(consultedServices);
        }
        public static InterferenceConsultation manual(LocalDate on, List<String> services, String note) {
            return new InterferenceConsultation(on, services, note, false);
        }
    }

    public record FieldOrderAttachments(UUID orderId, RoadInterventionLicense licenseOrNull,
                                        InterferenceConsultation consultationOrNull) {}
}
