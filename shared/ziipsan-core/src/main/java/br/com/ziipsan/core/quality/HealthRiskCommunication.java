package br.com.ziipsan.core.quality;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Comunicacao de nao conformidade com risco a saude (JOR-22): a
 * autoridade de saude publica e a populacao sao comunicadas por MODELO
 * do motor da P2.30 (JOR-06), com registro de envio e prazo.
 *
 * <p>PORTAO JOR-22 (do aceite): "os artigos da Portaria GM/MS 888/2021
 * que fixam essa comunicacao sao conferidos com o responsavel tecnico
 * ANTES da carga do modelo". Sem a conferencia registrada
 * ({@code articlesCheckedWithTechnicalResponsible}) e sem a chave do
 * modelo carregada ({@code quality.risk_template_key}, via
 * {@link QualityParameters}), NENHUM disparo acontece — a recusa e
 * nomeada, nunca contornada com texto livre.
 *
 * <p>O prazo de comunicacao NAO tem numero nas fontes do projeto:
 * entra como {@code deadline} declarado por quem abre a comunicacao
 * (do catalogo/plano do tenant, apos a conferencia do responsavel
 * tecnico) — [SEM FONTE], sem valor de fabrica. Atraso e fato
 * registrado ({@link #dispatchedLate()}), nunca escondido.
 */
public final class HealthRiskCommunication {

    private final UUID id;
    private final UUID tenantId;
    private final UUID sampleId;
    private final String parameterKey;
    private final LocalDate deadline;
    private final boolean articlesCheckedWithTechnicalResponsible;
    private final String checkerRef;
    private String authorityDispatchRef;
    private Instant authorityDispatchedAt;
    private String populationDispatchRef;
    private Instant populationDispatchedAt;

    private HealthRiskCommunication(UUID id, UUID tenantId, UUID sampleId, String parameterKey,
                                    LocalDate deadline, boolean checked, String checkerRef) {
        this.id = id;
        this.tenantId = tenantId;
        this.sampleId = sampleId;
        this.parameterKey = parameterKey;
        this.deadline = deadline;
        this.articlesCheckedWithTechnicalResponsible = checked;
        this.checkerRef = checkerRef;
    }

    /**
     * Abre a comunicacao a partir de uma avaliacao NAO CONFORME com a
     * marcacao de risco a saude DA LINHA REVISADA do catalogo. Avaliacao
     * conforme, indeterminada ou sem marcacao de risco nao abre
     * comunicacao por esta regra.
     */
    public static HealthRiskCommunication open(UUID id, UUID tenantId, Sample sample,
                                               ConformityEvaluator.Assessment assessment,
                                               LocalDate deadline,
                                               boolean articlesChecked, String checkerRef) {
        if (id == null) throw new IllegalArgumentException("id da comunicacao e obrigatorio");
        if (tenantId == null) throw new IllegalArgumentException("tenantId e obrigatorio");
        if (sample == null) throw new IllegalArgumentException("amostra e obrigatoria");
        if (assessment == null) throw new IllegalArgumentException("avaliacao e obrigatoria");
        if (assessment.verdict() != ConformityEvaluator.Verdict.NON_CONFORMING)
            throw new IllegalArgumentException("comunicacao de risco exige avaliacao NAO CONFORME");
        if (!assessment.healthRiskRelevant())
            throw new IllegalArgumentException("comunicacao de risco exige a marcacao de risco a saude"
                    + " da linha revisada do catalogo para " + assessment.parameterKey());
        if (deadline == null)
            throw new IllegalArgumentException("prazo da comunicacao e obrigatorio [SEM FONTE:"
                    + " declarado pelo tenant apos a conferencia do responsavel tecnico]");
        if (articlesChecked && (checkerRef == null || checkerRef.isBlank()))
            throw new IllegalArgumentException("conferencia dos artigos (JOR-22) exige quem conferiu");
        return new HealthRiskCommunication(id, tenantId, sample.id(), assessment.parameterKey(),
                deadline, articlesChecked, checkerRef);
    }

    private void requireDispatchable(String templateKeyRaw) {
        if (!articlesCheckedWithTechnicalResponsible)
            throw new IllegalStateException("disparo recusado: os artigos da Portaria GM/MS 888/2021"
                    + " ainda nao foram conferidos com o responsavel tecnico (JOR-22)");
        // Sem chave de modelo carregada, QualityParameters lanca MissingParameterException nomeada.
        QualityParameters.riskTemplateKey(templateKeyRaw);
    }

    public void dispatchToAuthority(QualityPorts.LegalTemplateDispatchPort port, String templateKeyRaw,
                                    Instant at) {
        if (authorityDispatchRef != null)
            throw new IllegalStateException("autoridade ja comunicada nesta ocorrencia");
        requireDispatchable(templateKeyRaw);
        if (port == null) throw new IllegalArgumentException("porta do motor de modelos (P2.30) e obrigatoria");
        if (at == null) throw new IllegalArgumentException("instante do envio e obrigatorio");
        this.authorityDispatchRef = port.dispatchByTemplate(tenantId,
                QualityParameters.riskTemplateKey(templateKeyRaw),
                QualityPorts.RiskRecipient.HEALTH_AUTHORITY, sampleId);
        if (authorityDispatchRef == null || authorityDispatchRef.isBlank())
            throw new IllegalStateException("motor de modelos nao devolveu a prova de envio a autoridade");
        this.authorityDispatchedAt = at;
    }

    public void dispatchToPopulation(QualityPorts.LegalTemplateDispatchPort port, String templateKeyRaw,
                                     Instant at) {
        if (populationDispatchRef != null)
            throw new IllegalStateException("populacao ja comunicada nesta ocorrencia");
        requireDispatchable(templateKeyRaw);
        if (port == null) throw new IllegalArgumentException("porta do motor de modelos (P2.30) e obrigatoria");
        if (at == null) throw new IllegalArgumentException("instante do envio e obrigatorio");
        this.populationDispatchRef = port.dispatchByTemplate(tenantId,
                QualityParameters.riskTemplateKey(templateKeyRaw),
                QualityPorts.RiskRecipient.POPULATION, sampleId);
        if (populationDispatchRef == null || populationDispatchRef.isBlank())
            throw new IllegalStateException("motor de modelos nao devolveu a prova de envio a populacao");
        this.populationDispatchedAt = at;
    }

    public boolean fullyDispatched() {
        return authorityDispatchRef != null && populationDispatchRef != null;
    }

    /** Algum envio ocorreu depois do prazo declarado? (fato registrado, nao escondido) */
    public boolean dispatchedLate() {
        return (authorityDispatchedAt != null
                && authorityDispatchedAt.atZone(java.time.ZoneOffset.UTC).toLocalDate().isAfter(deadline))
                || (populationDispatchedAt != null
                && populationDispatchedAt.atZone(java.time.ZoneOffset.UTC).toLocalDate().isAfter(deadline));
    }

    public boolean overdueOn(LocalDate date) {
        return !fullyDispatched() && date.isAfter(deadline);
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID sampleId() { return sampleId; }
    public String parameterKey() { return parameterKey; }
    public LocalDate deadline() { return deadline; }
    public String authorityDispatchRef() { return authorityDispatchRef; }
    public Instant authorityDispatchedAt() { return authorityDispatchedAt; }
    public String populationDispatchRef() { return populationDispatchRef; }
    public Instant populationDispatchedAt() { return populationDispatchedAt; }
}
