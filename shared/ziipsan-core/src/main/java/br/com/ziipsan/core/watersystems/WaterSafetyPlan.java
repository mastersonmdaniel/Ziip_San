package br.com.ziipsan.core.watersystems;

import br.com.ziipsan.core.certification.governance.RiskRecord;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Plano de Seguranca da Agua (PSA) — ED-79, sobre o registro de riscos
 * da P3.31 (GES-20). O PSA NAO cria registro de risco proprio: cada
 * perigo e um {@link HazardLink} para um risco REAL da P3.31
 * ({@link RiskRecord}), incluindo o incidente operacional (categoria
 * OPERATIONAL, com o {@code occurrenceId} da ocorrencia da P2.25 que a
 * P3.31 ja guarda) e a resiliencia climatica (categoria
 * CLIMATE_RESILIENCE). Medida de controle e acao corretiva sao textos
 * DECLARADOS pelo tenant; o parametro de monitoramento e a chave do
 * parametro de qualidade da ED-50, referenciada — nenhum limite,
 * frequencia ou conteudo de PSA e inventado aqui [SEM FONTE: as
 * fontes desta tarefa nao trazem roteiro/conteudo do PSA].
 */
public final class WaterSafetyPlan {
    private WaterSafetyPlan() {}

    public enum Status { DRAFT, APPROVED, SUPERSEDED }

    /**
     * Plano (cabecalho/versionamento). <b>Correcao D1 (revisao da
     * ED-79, 08/11/2026 — sonda A9):</b> era um record de construtor
     * publico: um PSA APPROVED sem nenhum vinculo a risco nascia
     * direto, fora do {@code WaterSafetyPlanService}. Agora e classe
     * final SELADA (construtor privado, padrao M-1 da ED-50): nasce
     * por {@link #draft} e so vira APPROVED por {@link #approved}, de
     * ACESSO DE PACOTE, chamada somente pelo servico depois da
     * conferencia de vinculos; SUPERSEDED idem via {@link #superseded}.
     * Os acessores seguem o estilo do record original.
     */
    public static final class Plan {
        private final UUID id;
        private final UUID tenantId;
        private final String supplySystemCode;
        private final int version;
        private final Status status;
        private final String approvedByRef;
        private final Instant approvedAt;
        private final LocalDate reviewDue;

        private Plan(UUID id, UUID tenantId, String supplySystemCode, int version, Status status,
                     String approvedByRef, Instant approvedAt, LocalDate reviewDue) {
            Objects.requireNonNull(id, "id do PSA e obrigatorio");
            Objects.requireNonNull(tenantId, "tenant e obrigatorio");
            if (supplySystemCode == null || supplySystemCode.isBlank())
                throw new IllegalArgumentException("sistema de abastecimento do PSA e obrigatorio");
            if (version < 1) throw new IllegalArgumentException("versao do PSA deve ser >= 1");
            Objects.requireNonNull(status, "situacao do PSA e obrigatoria");
            if (status == Status.APPROVED) {
                if (approvedByRef == null || approvedByRef.isBlank())
                    throw new IllegalArgumentException("PSA aprovado exige o responsavel pela aprovacao");
                Objects.requireNonNull(approvedAt, "PSA aprovado exige o instante da aprovacao");
                Objects.requireNonNull(reviewDue, "PSA aprovado exige o vencimento da revisao");
            }
            if (status == Status.DRAFT && approvedByRef != null)
                throw new IllegalArgumentException("PSA em rascunho nao carrega aprovacao");
            this.id = id;
            this.tenantId = tenantId;
            this.supplySystemCode = supplySystemCode;
            this.version = version;
            this.status = status;
            this.approvedByRef = approvedByRef;
            this.approvedAt = approvedAt;
            this.reviewDue = reviewDue;
        }

        public static Plan draft(UUID id, UUID tenantId, String supplySystemCode, int version) {
            return new Plan(id, tenantId, supplySystemCode, version, Status.DRAFT, null, null, null);
        }

        /** Transicao para APPROVED (acesso de pacote — so o WaterSafetyPlanService). */
        Plan approved(String byRef, Instant at, LocalDate reviewDueDate) {
            if (status != Status.DRAFT)
                throw new IllegalStateException("so PSA em rascunho pode ser aprovado (estado atual: " + status + ")");
            return new Plan(id, tenantId, supplySystemCode, version, Status.APPROVED, byRef, at, reviewDueDate);
        }

        /** Transicao para SUPERSEDED (acesso de pacote). */
        Plan superseded() {
            if (status != Status.APPROVED)
                throw new IllegalStateException("so PSA aprovado pode ser substituido (SUPERSEDED)");
            return new Plan(id, tenantId, supplySystemCode, version, Status.SUPERSEDED, approvedByRef, approvedAt, reviewDue);
        }

        public UUID id() { return id; }
        public UUID tenantId() { return tenantId; }
        public String supplySystemCode() { return supplySystemCode; }
        public int version() { return version; }
        public Status status() { return status; }
        public String approvedByRef() { return approvedByRef; }
        public Instant approvedAt() { return approvedAt; }
        public LocalDate reviewDue() { return reviewDue; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Plan other)) return false;
            return version == other.version
                    && Objects.equals(id, other.id) && Objects.equals(tenantId, other.tenantId)
                    && Objects.equals(supplySystemCode, other.supplySystemCode)
                    && status == other.status
                    && Objects.equals(approvedByRef, other.approvedByRef)
                    && Objects.equals(approvedAt, other.approvedAt)
                    && Objects.equals(reviewDue, other.reviewDue);
        }

        @Override public int hashCode() {
            return Objects.hash(id, tenantId, supplySystemCode, version, status,
                    approvedByRef, approvedAt, reviewDue);
        }

        @Override public String toString() {
            return "Plan[id=" + id + ", tenantId=" + tenantId + ", supplySystemCode=" + supplySystemCode
                    + ", version=" + version + ", status=" + status + ", approvedByRef=" + approvedByRef
                    + ", approvedAt=" + approvedAt + ", reviewDue=" + reviewDue + "]";
        }
    }

    /**
     * Perigo do PSA = vinculo a um risco da P3.31 + medida de controle
     * e acao corretiva declaradas. O parametro de monitoramento, quando
     * declarado, e a chave do parametro da ED-50 (sem limite aqui).
     */
    public record HazardLink(
            UUID tenantId,
            UUID planId,
            UUID riskId,
            String controlMeasure,
            String monitoringParameterKey,
            String correctiveAction) {
        public HazardLink {
            Objects.requireNonNull(tenantId, "tenant e obrigatorio");
            Objects.requireNonNull(planId, "plano do vinculo e obrigatorio");
            Objects.requireNonNull(riskId, "risco da P3.31 do vinculo e obrigatorio (GES-20)");
            if (controlMeasure == null || controlMeasure.isBlank())
                throw new IllegalArgumentException("medida de controle do perigo e obrigatoria (declarada pelo tenant)");
            if (correctiveAction == null || correctiveAction.isBlank())
                throw new IllegalArgumentException("acao corretiva do perigo e obrigatoria (declarada pelo tenant)");
        }
    }

    /**
     * Cobertura do PSA calculada sobre os riscos REAIS vinculados:
     * incidente operacional = risco OPERATIONAL da P3.31 (que carrega o
     * vinculo com a ocorrencia); resiliencia climatica = risco
     * CLIMATE_RESILIENCE. Nada e presumido: sem vinculo da categoria, a
     * cobertura da categoria e falsa e o relatorio de cobertura a expoe.
     */
    public record PlanCoverage(
            int linkedRiskCount,
            boolean coversOperationalIncidents,
            boolean coversClimateResilience,
            List<UUID> linkedRiskIds,
            List<UUID> linkedOccurrenceIds) {
        public PlanCoverage {
            linkedRiskIds = List.copyOf(linkedRiskIds);
            linkedOccurrenceIds = List.copyOf(linkedOccurrenceIds);
        }

        public static PlanCoverage of(List<RiskRecord> linkedRisks) {
            boolean operational = linkedRisks.stream()
                    .anyMatch(r -> r.category() == RiskRecord.Category.OPERATIONAL);
            boolean climate = linkedRisks.stream()
                    .anyMatch(r -> r.category() == RiskRecord.Category.CLIMATE_RESILIENCE);
            List<UUID> occurrenceIds = linkedRisks.stream()
                    .map(RiskRecord::occurrenceId).filter(Objects::nonNull).distinct().toList();
            return new PlanCoverage(linkedRisks.size(), operational, climate,
                    linkedRisks.stream().map(RiskRecord::id).toList(), occurrenceIds);
        }
    }
}
