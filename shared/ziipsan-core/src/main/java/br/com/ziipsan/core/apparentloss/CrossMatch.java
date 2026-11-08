package br.com.ziipsan.core.apparentloss;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Cruzamento OPE-37 (aceite da ED-95): edificacoes e lotes do
 * cadastro municipal (CAD-103) ou da importacao de GIS x unidades
 * consumidoras e o ramal/ligacao.
 *
 * <p><b>Restrito ao backoffice</b> (ADR-0034, GIS-CADASTRO §12):
 * {@link #evaluate} exige {@link AccessChannel#BACKOFFICE}; qualquer
 * outro canal (portal, API publica, app de campo) e RECUSADO — o
 * cruzamento expoe vinculo imovel x ligacao, que fora do backoffice
 * so pode aparecer agregado (regra do GIS, fora desta entrega).
 *
 * <p>A saida e um {@link Finding} na FILA DE REVISAO, com prazo
 * derivado de {@code audit.unregistered_building_review_days} e uma
 * ACAO SUGERIDA (vistoria, fiscalizacao da ED-24 ou recadastramento
 * em lote). Finding NAO cria OS, debito, corte nem infracao: a OS so
 * nasce da decisao humana registrada (ver
 * {@code ApparentLossService.decideFinding}).
 *
 * <p>[A DEFINIR] Tolerancia da divergencia de area: o aceite pede o
 * cruzamento "area divergente da economia" sem fixar tolerancia nem
 * chave de parametro. A tolerancia chega EXPLICITA no
 * {@link Policy} do chamador; ausente ({@code null}), a checagem de
 * area NAO roda e o chamador registra a ausencia — nenhuma tolerancia
 * e presumida aqui (mesmo tratamento dos limiares sem chave na ED-24).
 */
public final class CrossMatch {

    private CrossMatch() {
    }

    /** Canal de acesso ao cruzamento (ADR-0034 §12: so backoffice). */
    public enum AccessChannel {
        BACKOFFICE, PORTAL, PUBLIC_API, FIELD_APP
    }

    /** Divergencias do cruzamento (as quatro do aceite, OPE-37). */
    public enum FindingKind {
        /** Imovel edificado sem ligacao/unidade vinculada. */
        BUILT_WITHOUT_CONNECTION,
        /** Consumo nulo (ciclo mais recente) com ligacao ativa. */
        ZERO_CONSUMPTION_ACTIVE_CONNECTION,
        /** Area do cadastro diverge da area declarada da economia alem da tolerancia declarada. */
        AREA_DIVERGENCE,
        /** Uso do cadastro diverge da categoria da unidade. */
        USE_DIVERGENCE
    }

    /** Acao sugerida ao revisor humano (a decisao final e dele). */
    public enum SuggestedAction {
        /** OS de vistoria (conferir o imovel/ligacao em campo). */
        FIELD_SURVEY,
        /** OS de fiscalizacao (fluxo da ED-24). */
        INSPECTION,
        /** Recadastramento em lote (corrigir cadastro sem ida individual). */
        BATCH_RE_REGISTRATION
    }

    public enum ReviewStatus {
        PENDING_REVIEW, DECIDED, DISMISSED
    }

    /**
     * Politica do cruzamento: prazo da fila (do parametro do tenant) e
     * tolerancia de area declarada (nula = checagem de area desligada,
     * ver javadoc da classe).
     */
    public record Policy(int reviewDays, BigDecimal areaDivergenceThresholdPercent) {
        public Policy {
            if (reviewDays <= 0)
                throw new IllegalArgumentException("prazo da fila de revisao deve ser maior que zero: " + reviewDays);
            if (areaDivergenceThresholdPercent != null && areaDivergenceThresholdPercent.signum() < 0)
                throw new IllegalArgumentException("tolerancia de area nao pode ser negativa");
        }
    }

    /** Decisao humana registrada sobre um finding (quem, quando, o que). */
    public record HumanDecision(UUID decidedBy, LocalDateTime decidedAt, SuggestedAction action) {
        public HumanDecision {
            Objects.requireNonNull(decidedBy, "decisao humana exige o responsavel registrado");
            Objects.requireNonNull(decidedAt, "decisao humana exige data/hora registrada");
            Objects.requireNonNull(action, "decisao humana exige a acao escolhida");
        }
    }

    /**
     * Achado do cruzamento na fila de revisao (recomendacao, nao efeito).
     *
     * <p>Correcao D4 (revisao da ED-95): a proveniencia da decisao mora
     * no modelo — {@code decidedBy}/{@code decidedAt} acompanham o
     * achado decidido OU descartado (ambos sao decisao humana
     * registrada), e o {@code evaluationChannel} e campo do modelo,
     * travado em {@link AccessChannel#BACKOFFICE} (a coluna
     * {@code evaluation_channel} do SQL e NOT NULL com CHECK
     * {@code = 'BACKOFFICE'}; o adaptador grava este campo, nunca um
     * valor solto). Sem isso, o SQL exige {@code decided_by/decided_at
     * NOT NULL} quando ha OS e a proveniencia se perderia na fiacao.
     */
    public record Finding(
            UUID id, UUID tenantId, FindingKind kind, UUID propertyId, UUID unitId,
            String evidence, SuggestedAction suggestedAction, LocalDateTime detectedAt,
            LocalDate reviewDueDate, ReviewStatus status, UUID workOrderId,
            UUID decidedBy, LocalDateTime decidedAt, AccessChannel evaluationChannel) {

        public Finding {
            Objects.requireNonNull(id, "id do achado e obrigatorio");
            Objects.requireNonNull(tenantId, "tenant e obrigatorio");
            Objects.requireNonNull(kind, "tipo da divergencia e obrigatorio");
            if (evidence == null || evidence.isBlank())
                throw new IllegalArgumentException("achado sem evidencia nao entra na fila de revisao");
            Objects.requireNonNull(suggestedAction, "acao sugerida e obrigatoria");
            Objects.requireNonNull(detectedAt, "deteccao e obrigatoria");
            Objects.requireNonNull(reviewDueDate, "prazo de revisao e obrigatorio");
            Objects.requireNonNull(status, "situacao na fila e obrigatoria");
            Objects.requireNonNull(evaluationChannel, "canal da avaliacao que gerou o achado e obrigatorio");
            if (evaluationChannel != AccessChannel.BACKOFFICE)
                throw new IllegalArgumentException("achado so nasce de avaliacao em backoffice (ADR-0034); canal: "
                        + evaluationChannel);
            if (status == ReviewStatus.PENDING_REVIEW && workOrderId != null)
                throw new IllegalArgumentException("achado pendente nao pode ter OS: OS so nasce de decisao humana registrada");
            if (status == ReviewStatus.PENDING_REVIEW && (decidedBy != null || decidedAt != null))
                throw new IllegalArgumentException("achado pendente nao pode carregar decisao: proveniencia so com a decisao");
            if (status == ReviewStatus.DECIDED && workOrderId == null)
                throw new IllegalArgumentException("achado decidido exige a OS gerada pela decisao");
            if (status == ReviewStatus.DECIDED && (decidedBy == null || decidedAt == null))
                throw new IllegalArgumentException("achado decidido exige a proveniencia da decisao (decidedBy/decidedAt)");
            if (status == ReviewStatus.DISMISSED && workOrderId != null)
                throw new IllegalArgumentException("achado descartado nao pode ter OS");
            if (status == ReviewStatus.DISMISSED && (decidedBy == null || decidedAt == null))
                throw new IllegalArgumentException("descarte exige a proveniencia da decisao (decidedBy/decidedAt)");
        }

        /** Construtor da fila (pre-D4): achado pendente, sem decisao, canal backoffice. */
        public Finding(UUID id, UUID tenantId, FindingKind kind, UUID propertyId, UUID unitId,
                       String evidence, SuggestedAction suggestedAction, LocalDateTime detectedAt,
                       LocalDate reviewDueDate, ReviewStatus status, UUID workOrderId) {
            this(id, tenantId, kind, propertyId, unitId, evidence, suggestedAction, detectedAt,
                    reviewDueDate, status, workOrderId, null, null, AccessChannel.BACKOFFICE);
        }

        /** Transicao unica para decidido: so com decisao humana e a OS que ela gerou. */
        public Finding decided(HumanDecision decision, UUID generatedWorkOrderId) {
            Objects.requireNonNull(decision, "decisao humana registrada e obrigatoria");
            Objects.requireNonNull(generatedWorkOrderId, "OS gerada pela decisao e obrigatoria");
            return new Finding(id, tenantId, kind, propertyId, unitId, evidence, suggestedAction,
                    detectedAt, reviewDueDate, ReviewStatus.DECIDED, generatedWorkOrderId,
                    decision.decidedBy(), decision.decidedAt(), evaluationChannel);
        }

        /** Descarte: tambem e decisao humana registrada; a proveniencia acompanha o achado. */
        public Finding dismissed(HumanDecision decision) {
            Objects.requireNonNull(decision, "descarte exige decisao humana registrada");
            return new Finding(id, tenantId, kind, propertyId, unitId, evidence, suggestedAction,
                    detectedAt, reviewDueDate, ReviewStatus.DISMISSED, null,
                    decision.decidedBy(), decision.decidedAt(), evaluationChannel);
        }
    }

    /**
     * Avalia o cruzamento. Recusa canal nao-backoffice ANTES de olhar
     * qualquer dado. Vinculo imovel x unidade pelo
     * {@code linkedPropertyId} do retrato da unidade.
     */
    public static List<Finding> evaluate(UUID tenantId, AccessChannel channel, Policy policy,
                                         List<PropertyRegistryRecord> properties,
                                         List<UnitFacts> units, LocalDateTime detectedAt) {
        Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        if (channel != AccessChannel.BACKOFFICE)
            throw new IllegalStateException("cruzamento de edificacoes com unidades e restrito ao backoffice "
                    + "(ADR-0034, GIS-CADASTRO §12); canal recusado: " + channel);
        Objects.requireNonNull(policy, "politica do cruzamento e obrigatoria");
        Objects.requireNonNull(detectedAt, "data/hora da deteccao e obrigatoria");
        List<PropertyRegistryRecord> props = properties == null ? List.of() : properties;
        List<UnitFacts> us = units == null ? List.of() : units;
        // Correcao D3 (revisao da ED-95): a defesa de tenant mora tambem no
        // dominio — entrada de outro tenant e recusada, nunca carimbada com
        // o tenant do chamador (mesma guarda de importRegistryBatch).
        for (PropertyRegistryRecord p : props) {
            if (!tenantId.equals(p.tenantId()))
                throw new IllegalArgumentException("imovel de outro tenant no cruzamento: " + p.id());
        }
        for (UnitFacts u : us) {
            if (!tenantId.equals(u.tenantId()))
                throw new IllegalArgumentException("unidade de outro tenant no cruzamento: " + u.unitId());
        }
        LocalDate due = detectedAt.toLocalDate().plusDays(policy.reviewDays());
        List<Finding> out = new ArrayList<>();
        for (PropertyRegistryRecord p : props) {
            List<UnitFacts> linked = us.stream()
                    .filter(u -> p.id().equals(u.linkedPropertyId())).toList();
            if (p.built() && linked.isEmpty()) {
                out.add(new Finding(UUID.randomUUID(), tenantId, FindingKind.BUILT_WITHOUT_CONNECTION,
                        p.id(), null,
                        "imovel edificado (area construida " + p.builtAreaSquareMeters()
                                + " m2, inscricao " + p.municipalRegistration() + ") sem unidade/ligacao vinculada",
                        SuggestedAction.FIELD_SURVEY, detectedAt, due, ReviewStatus.PENDING_REVIEW, null,
                        null, null, channel));
                continue;
            }
            for (UnitFacts u : linked) {
                u.lastConsumptionM3().ifPresent(last -> {
                    if (u.connectionStatus() == UnitFacts.ConnectionStatus.ACTIVE
                            && last.signum() == 0) {
                        out.add(new Finding(UUID.randomUUID(), tenantId,
                                FindingKind.ZERO_CONSUMPTION_ACTIVE_CONNECTION, p.id(), u.unitId(),
                                "consumo nulo no ciclo mais recente com ligacao ativa (unidade " + u.unitId() + ")",
                                SuggestedAction.INSPECTION, detectedAt, due, ReviewStatus.PENDING_REVIEW, null,
                                        null, null, channel));
                    }
                });
                if (policy.areaDivergenceThresholdPercent() != null
                        && u.declaredBuiltAreaSquareMeters() != null
                        && p.builtAreaSquareMeters().signum() > 0) {
                    BigDecimal diffPct = u.declaredBuiltAreaSquareMeters()
                            .subtract(p.builtAreaSquareMeters()).abs()
                            .multiply(BigDecimal.valueOf(100))
                            .divide(p.builtAreaSquareMeters(), 4, RoundingMode.HALF_UP);
                    if (diffPct.compareTo(policy.areaDivergenceThresholdPercent()) > 0) {
                        out.add(new Finding(UUID.randomUUID(), tenantId, FindingKind.AREA_DIVERGENCE,
                                p.id(), u.unitId(),
                                "area do cadastro " + p.builtAreaSquareMeters() + " m2 diverge "
                                        + diffPct + "% da area declarada da economia "
                                        + u.declaredBuiltAreaSquareMeters() + " m2 (tolerancia declarada "
                                        + policy.areaDivergenceThresholdPercent() + "%)",
                                SuggestedAction.BATCH_RE_REGISTRATION, detectedAt, due,
                                ReviewStatus.PENDING_REVIEW, null, null, null, channel));
                    }
                }
                if (u.categoryUse() != p.declaredUse()) {
                    out.add(new Finding(UUID.randomUUID(), tenantId, FindingKind.USE_DIVERGENCE,
                            p.id(), u.unitId(),
                            "uso do cadastro (" + p.declaredUse() + ") diverge da categoria da unidade ("
                                    + u.categoryUse() + ")",
                            SuggestedAction.BATCH_RE_REGISTRATION, detectedAt, due,
                            ReviewStatus.PENDING_REVIEW, null, null, null, channel));
                }
            }
        }
        return List.copyOf(out);
    }
}
