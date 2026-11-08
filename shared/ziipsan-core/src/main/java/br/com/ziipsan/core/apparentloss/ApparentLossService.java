package br.com.ziipsan.core.apparentloss;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Servico de perdas aparentes (ED-95; CAD-103, GES-25, OPE-37).
 *
 * <p>Fluxo inteiro, por desenho (ADR-0034, ADR-0042):
 * <ol>
 *   <li>importacao do cadastro municipal valida o modo de
 *       sincronizacao do tenant e grava os registros — registro sem
 *       base legal/finalidade nem se constroi
 *       ({@link PropertyRegistryRecord});</li>
 *   <li>cruzamento so roda em canal BACKOFFICE e so produz achados
 *       na fila de revisao, com prazo do parametro do tenant;</li>
 *   <li>regras de suspeita so produzem RECOMENDACOES;</li>
 *   <li>OS (vistoria, fiscalizacao da ED-24 ou recadastramento em
 *       lote) so nasce de {@code decideFinding}/{@code decideSuspicion}
 *       com {@link CrossMatch.HumanDecision} registrada (quem, quando,
 *       acao) — e a porta de OS e a UNICA porta de efeito do servico:
 *       nao ha porta de debito, corte ou infracao nesta classe;</li>
 *   <li>autodenuncia so com o programa declarado pelo tenant, e abre
 *       caso em revisao humana com a isencao de multa do programa;</li>
 *   <li>ML: apenas a avaliacao do portao ({@link MlGate}); nenhum
 *       modelo e criado nem habilitado aqui.</li>
 * </ol>
 * Item 32 das decisoes do dono (arquitetura tarifaria alvo): esta
 * entrega nao toca conta, tarifa nem billing.
 */
public final class ApparentLossService {

    private final LossPorts.LossStore store;
    private final LossPorts.PropertyRegistrySource registrySource;
    private final LossPorts.UnitFactsPort unitFacts;
    private final LossPorts.WorkOrderPort workOrders;
    private final LossPorts.TenantParameters parameters;

    public ApparentLossService(LossPorts.LossStore store,
                               LossPorts.PropertyRegistrySource registrySource,
                               LossPorts.UnitFactsPort unitFacts,
                               LossPorts.WorkOrderPort workOrders,
                               LossPorts.TenantParameters parameters) {
        this.store = Objects.requireNonNull(store, "servico sem store");
        this.registrySource = registrySource;
        this.unitFacts = unitFacts;
        this.workOrders = workOrders;
        this.parameters = Objects.requireNonNull(parameters, "servico sem parametros do tenant");
    }

    private String requiredParam(UUID tenantId, String key) {
        return parameters.resolve(tenantId, key).orElse(null);
    }

    /**
     * Importa o lote do cadastro municipal pela fonte do hub
     * (ED-62/P3.37). Exige o modo declarado em
     * {@code registry.property_registry_sync}; cada registro ja traz
     * base legal e finalidade (sem elas o record recusa).
     */
    public int importRegistryBatch(UUID tenantId) {
        Objects.requireNonNull(registrySource, "fonte do cadastro municipal nao fiada nesta instancia");
        String syncMode = LossParameters.propertyRegistrySyncMode(requiredParam(tenantId, LossParameters.PROPERTY_REGISTRY_SYNC));
        List<PropertyRegistryRecord> batch = registrySource.fetchBatch(tenantId, syncMode);
        for (PropertyRegistryRecord r : batch) {
            if (!tenantId.equals(r.tenantId()))
                throw new IllegalArgumentException("registro de outro tenant no lote de importacao: " + r.id());
            store.saveProperty(r);
        }
        return batch.size();
    }

    /** Importacao direta de registros ja canonicos (importacao de GIS, OPE-37). */
    public void importRegistryRecords(UUID tenantId, List<PropertyRegistryRecord> records) {
        LossParameters.propertyRegistrySyncMode(requiredParam(tenantId, LossParameters.PROPERTY_REGISTRY_SYNC));
        for (PropertyRegistryRecord r : records) {
            if (!tenantId.equals(r.tenantId()))
                throw new IllegalArgumentException("registro de outro tenant na importacao: " + r.id());
            store.saveProperty(r);
        }
    }

    /**
     * Roda o cruzamento (OPE-37) e enfileira os achados. NENHUMA OS e
     * criada aqui. {@code areaDivergenceThresholdPercent} nulo desliga
     * a checagem de area (tolerancia nao presumida, ver CrossMatch).
     */
    public List<CrossMatch.Finding> runCrossMatch(UUID tenantId, CrossMatch.AccessChannel channel,
                                                  java.math.BigDecimal areaDivergenceThresholdPercent,
                                                  LocalDateTime detectedAt) {
        Objects.requireNonNull(unitFacts, "retratos de unidade nao fiados nesta instancia");
        int reviewDays = LossParameters.unregisteredBuildingReviewDays(
                parameters.resolveInt(tenantId, LossParameters.UNREGISTERED_BUILDING_REVIEW_DAYS).orElse(null));
        CrossMatch.Policy policy = new CrossMatch.Policy(reviewDays, areaDivergenceThresholdPercent);
        List<CrossMatch.Finding> findings = CrossMatch.evaluate(tenantId, channel, policy,
                store.properties(tenantId), unitFacts.factsForTenant(tenantId), detectedAt);
        findings.forEach(store::saveFinding);
        return findings;
    }

    /**
     * Unico caminho de um achado para uma OS: decisao humana
     * registrada. Sem decisao (ou com achado ja decidido/descartado),
     * nenhuma OS nasce.
     */
    public CrossMatch.Finding decideFinding(UUID tenantId, UUID findingId, CrossMatch.HumanDecision decision) {
        Objects.requireNonNull(decision, "OS so nasce de decisao humana registrada (quem, quando, acao)");
        Objects.requireNonNull(workOrders, "porta de OS nao fiada nesta instancia");
        CrossMatch.Finding f = store.finding(tenantId, findingId)
                .orElseThrow(() -> new IllegalArgumentException("achado inexistente neste tenant: " + findingId));
        if (f.status() != CrossMatch.ReviewStatus.PENDING_REVIEW)
            throw new IllegalStateException("achado fora da fila de revisao (situacao " + f.status() + "): " + findingId);
        LossPorts.WorkOrderKind kind = switch (decision.action()) {
            case FIELD_SURVEY -> LossPorts.WorkOrderKind.FIELD_SURVEY;
            case INSPECTION -> LossPorts.WorkOrderKind.INSPECTION;
            case BATCH_RE_REGISTRATION -> LossPorts.WorkOrderKind.BATCH_RE_REGISTRATION;
        };
        UUID orderId = workOrders.openWorkOrder(tenantId, kind, f.unitId(), f.propertyId(),
                "ED-95 cruzamento " + f.kind() + " — decisao humana de " + decision.decidedBy()
                        + " em " + decision.decidedAt() + ": " + f.evidence());
        // Correcao D4: a proveniencia viaja no Finding gravado.
        CrossMatch.Finding decided = f.decided(decision, orderId);
        store.saveFinding(decided);
        return decided;
    }

    public CrossMatch.Finding dismissFinding(UUID tenantId, UUID findingId, CrossMatch.HumanDecision decision) {
        Objects.requireNonNull(decision, "descarte tambem e decisao humana registrada");
        CrossMatch.Finding f = store.finding(tenantId, findingId)
                .orElseThrow(() -> new IllegalArgumentException("achado inexistente neste tenant: " + findingId));
        if (f.status() != CrossMatch.ReviewStatus.PENDING_REVIEW)
            throw new IllegalStateException("achado fora da fila de revisao (situacao " + f.status() + "): " + findingId);
        CrossMatch.Finding dismissed = f.dismissed(decision);
        store.saveFinding(dismissed);
        return dismissed;
    }

    /** Avalia as regras de suspeita de uma unidade: so recomendacoes sao gravadas. */
    public List<FraudRules.Suspicion> evaluateUnit(UUID tenantId, UUID unitId, LocalDateTime evaluatedAt) {
        Objects.requireNonNull(unitFacts, "retratos de unidade nao fiados nesta instancia");
        LossParameters.FraudRuleThresholds thresholds = LossParameters.FraudRuleThresholds.parse(
                requiredParam(tenantId, LossParameters.FRAUD_RULES));
        UnitFacts facts = unitFacts.facts(tenantId, unitId)
                .orElseThrow(() -> new IllegalArgumentException("unidade inexistente neste tenant: " + unitId));
        List<FraudRules.Suspicion> suspicions = FraudRules.evaluate(facts, thresholds, evaluatedAt);
        suspicions.forEach(store::saveSuspicion);
        return suspicions;
    }

    /**
     * Decisao humana sobre uma suspeita: abre a OS de fiscalizacao
     * (fluxo da ED-24). E o maximo que uma suspeita alcanca nesta
     * entrega — auto, debito e corte sao de outros fluxos, com
     * defesa e decisao proprias (ED-24).
     *
     * <p>Correcoes D1/D5a (revisao da ED-95): a acao registrada na
     * decisao tem de ser {@code INSPECTION} explicitamente — outra
     * acao e recusada, nunca convertida em silencio — e a decisao e
     * REGISTRADA sobre a suspeita (id + decisao), recusando a segunda
     * decisao sobre a mesma suspeita, como {@code decideFinding} ja
     * fazia com os achados.
     */
    public UUID decideSuspicionWithInspection(UUID tenantId, UUID unitId, FraudRules.Rule rule,
                                              CrossMatch.HumanDecision decision) {
        Objects.requireNonNull(decision, "OS so nasce de decisao humana registrada (quem, quando, acao)");
        Objects.requireNonNull(workOrders, "porta de OS nao fiada nesta instancia");
        requireInspectionAction(decision);
        List<FraudRules.Suspicion> matches = store.suspicions(tenantId).stream()
                .filter(s -> s.unitId().equals(unitId) && s.rule() == rule).toList();
        if (matches.isEmpty())
            throw new IllegalArgumentException("sem suspeita registrada da regra " + rule + " para a unidade " + unitId);
        if (matches.stream().anyMatch(FraudRules.Suspicion::decided))
            throw new IllegalStateException("ja existe decisao registrada para a regra " + rule
                    + " na unidade " + unitId + ": segunda decisao recusada");
        FraudRules.Suspicion target = matches.get(0);
        return decideSuspicionRecord(tenantId, target, decision);
    }

    /**
     * Decisao humana sobre uma suspeita pelo seu id (correcao D1):
     * mesmo caminho de {@link #decideSuspicionWithInspection}, com a
     * suspeita identificada diretamente.
     */
    public UUID decideSuspicion(UUID tenantId, UUID suspicionId, CrossMatch.HumanDecision decision) {
        Objects.requireNonNull(decision, "OS so nasce de decisao humana registrada (quem, quando, acao)");
        Objects.requireNonNull(workOrders, "porta de OS nao fiada nesta instancia");
        requireInspectionAction(decision);
        FraudRules.Suspicion target = store.suspicion(tenantId, suspicionId)
                .orElseThrow(() -> new IllegalArgumentException("suspeita inexistente neste tenant: " + suspicionId));
        return decideSuspicionRecord(tenantId, target, decision);
    }

    private static void requireInspectionAction(CrossMatch.HumanDecision decision) {
        if (decision.action() != CrossMatch.SuggestedAction.INSPECTION)
            throw new IllegalArgumentException("decisao sobre suspeita exige a acao INSPECTION registrada; "
                    + "acao recebida: " + decision.action());
    }

    private UUID decideSuspicionRecord(UUID tenantId, FraudRules.Suspicion target,
                                       CrossMatch.HumanDecision decision) {
        if (target.decided())
            throw new IllegalStateException("suspeita ja decidida (OS " + target.inspectionWorkOrderId()
                    + "): segunda decisao recusada: " + target.id());
        UUID orderId = workOrders.openWorkOrder(tenantId, LossPorts.WorkOrderKind.INSPECTION,
                target.unitId(), null,
                "ED-95 suspeita " + target.rule() + " (GES-25) — decisao humana de " + decision.decidedBy()
                        + " em " + decision.decidedAt() + "; fiscalizacao segue a ED-24");
        store.saveSuspicion(target.decidedWithInspection(decision, orderId));
        return orderId;
    }

    /** Registra o desfecho agregado de inspecoes de uma regra (fiscalizacao, ED-24). */
    public void recordInspectionOutcome(UUID tenantId, InspectionAccuracy.RuleOutcome outcome) {
        store.saveOutcome(tenantId, outcome);
    }

    public InspectionAccuracy.Report accuracyReport(UUID tenantId) {
        return InspectionAccuracy.build(store.outcomes(tenantId));
    }

    /** Avaliacao do portao de ML (ADR-0042): nunca habilita modelo. */
    public MlGate.Assessment assessMlReadiness(UUID tenantId, String adrDecisionReference) {
        LossParameters.FraudRuleThresholds thresholds = LossParameters.FraudRuleThresholds.parse(
                requiredParam(tenantId, LossParameters.FRAUD_RULES));
        return MlGate.assess(MlGate.enabledRules(thresholds), accuracyReport(tenantId), adrDecisionReference);
    }

    /**
     * Autodenuncia: so com o programa declarado pelo tenant
     * ({@code fraud.self_report_fine_waiver_enabled} = true). Abre
     * caso em revisao humana com a isencao de multa do programa; nao
     * gera debito, corte nem auto.
     */
    public SelfReport.Case fileSelfReport(UUID tenantId, UUID unitId, String declaredIrregularity, LocalDateTime at) {
        Boolean enabled = LossParameters.declaredSelfReportFineWaiverEnabled(
                requiredParam(tenantId, LossParameters.SELF_REPORT_FINE_WAIVER_ENABLED));
        if (enabled == null)
            throw new IllegalStateException("programa de autodenuncia nao declarado pelo tenant ("
                    + LossParameters.SELF_REPORT_FINE_WAIVER_ENABLED + " ausente): autodenuncia recusada, nao presumida");
        if (!enabled)
            throw new IllegalStateException("programa de autodenuncia desligado pelo tenant: autodenuncia recusada");
        // Correcao D5b (revisao da ED-95): a unidade tem de existir no
        // tenant — a porta de fatos e consultada antes de abrir o caso.
        Objects.requireNonNull(unitFacts, "retratos de unidade nao fiados nesta instancia");
        unitFacts.facts(tenantId, unitId)
                .orElseThrow(() -> new IllegalArgumentException("unidade inexistente neste tenant: " + unitId));
        SelfReport.Case c = SelfReport.Case.opened(tenantId, unitId, declaredIrregularity, at);
        store.saveSelfReport(c);
        return c;
    }
}
