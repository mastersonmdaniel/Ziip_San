package br.com.ziipsan.core.certification.lims17025;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Laudo de laboratorio (aceite da ED-51): emitido por MODELO do motor
 * de modelos da P2.30 (JOR-06), via a porta
 * {@link LimsPorts.LabReportTemplatePort} — nenhum texto de laudo e
 * redigido por motor proprio aqui. A chave do modelo e o parametro
 * {@code lims.lab_report_template_key} do tenant, [SEM FONTE], sem
 * valor de fabrica ({@link LimsParameters}): sem chave configurada,
 * a emissao recusa antes de qualquer renderizacao.
 *
 * <p>Regras:
 * <ul>
 *   <li>so entram no laudo ensaios VALIDADOS por segunda pessoa, do
 *       mesmo tenant e do laboratorio do laudo;</li>
 *   <li>o conteudo entregue ao motor e deterministico: por ensaio,
 *       metodo, parametro, resultado como reportado, incerteza
 *       expandida com o fator k, estado do controle de Westgard e
 *       quem executou/validou;</li>
 *   <li>EMENDA RASTREAVEL: emendar nao edita o laudo — nasce a versao
 *       seguinte, com o motivo obrigatorio e a referencia a versao
 *       substituida, e a versao anterior fica {@code SUPERSEDED},
 *       preservada (mesma disciplina de versao do resultado da
 *       ED-50, SG-813); laudo ja substituido nao se emenda de novo —
 *       emenda-se a versao vigente.</li>
 * </ul>
 *
 * <p>So com a norma ligada: emissao e emenda passam pela porta da
 * {@link Lims17025Activation}.
 */
public final class LabReport {

    public enum Status { ISSUED, SUPERSEDED }

    private final UUID id;
    private final UUID tenantId;
    private final UUID laboratoryId;
    private final List<UUID> assayIds;
    private final String templateKey;
    private final String renderedRef;
    private final String issuedByRef;
    private final Instant issuedAt;
    private final int version;
    private final UUID supersedesId;
    private final String amendmentReason;
    private Status status = Status.ISSUED;

    private LabReport(UUID id, UUID tenantId, UUID laboratoryId, List<UUID> assayIds,
                      String templateKey, String renderedRef, String issuedByRef, Instant issuedAt,
                      int version, UUID supersedesId, String amendmentReason) {
        this.id = id;
        this.tenantId = tenantId;
        this.laboratoryId = laboratoryId;
        this.assayIds = List.copyOf(assayIds);
        this.templateKey = templateKey;
        this.renderedRef = renderedRef;
        this.issuedByRef = issuedByRef;
        this.issuedAt = issuedAt;
        this.version = version;
        this.supersedesId = supersedesId;
        this.amendmentReason = amendmentReason;
    }

    /** Linhas deterministicas do conteudo entregue ao motor de modelos (P2.30). */
    static List<String> payloadOf(List<AssayExecution> assays, String amendmentReason) {
        List<String> lines = new ArrayList<>();
        lines.add("TIPO=LAUDO");
        if (amendmentReason != null) lines.add("EMENDA=" + amendmentReason);
        for (AssayExecution a : assays) {
            MeasurementUncertainty u = a.uncertainty();
            lines.add("ENSAIO id=" + a.id()
                    + " amostra=" + a.sampleId()
                    + " metodo=" + a.methodRef()
                    + " parametro=" + a.parameterKey()
                    + " resultado=" + a.resultText()
                    + " unidade=" + (a.unit() == null ? "" : a.unit())
                    + " incertezaExpandida=" + u.expandedUncertainty().toPlainString()
                    + " k=" + u.coverageFactor().toPlainString()
                    + " regraDecisao=" + u.decisionRule()
                    + " westgard=" + a.qcStatus()
                    + " ambiente=" + a.environmentalVerdict()
                    + " equipamento=" + a.equipmentCode()
                    + " analista=" + a.analystRef()
                    + " validador=" + a.validatorRef()
                    + (a.nonconformityNote() == null ? "" : " naoConforme=" + a.nonconformityNote()));
        }
        return lines;
    }

    private static void requireReportable(UUID tenantId, UUID laboratoryId, List<AssayExecution> assays) {
        if (assays == null || assays.isEmpty())
            throw new IllegalArgumentException("laudo sem ensaio nao se emite");
        for (AssayExecution a : assays) {
            Objects.requireNonNull(a, "ensaio do laudo nao pode ser nulo");
            if (!a.tenantId().equals(tenantId))
                throw new IllegalArgumentException("ensaio " + a.id() + " de outro tenant");
            if (!a.laboratoryId().equals(laboratoryId))
                throw new IllegalArgumentException("ensaio " + a.id() + " de outro laboratorio");
            if (!a.validated())
                throw new IllegalStateException("ensaio " + a.id() + " nao validado por segunda "
                        + "pessoa: nao entra em laudo");
        }
    }

    /**
     * Emissao do laudo (versao 1) pelo motor da P2.30. A chave do
     * modelo e resolvida de {@code rawTemplateKey} por
     * {@link LimsParameters} — ausente, recusa com
     * {@code MissingParameterException} antes de renderizar.
     */
    public static LabReport issue(Lims17025Activation activation,
                                  LimsPorts.LabReportTemplatePort engine, String rawTemplateKey,
                                  UUID id, UUID tenantId, UUID laboratoryId,
                                  List<AssayExecution> assays, String issuedByRef, Instant issuedAt) {
        Objects.requireNonNull(activation, "activation e obrigatoria");
        activation.requireEnabled();
        Objects.requireNonNull(engine, "engine (motor da P2.30) e obrigatorio");
        String templateKey = LimsParameters.labReportTemplateKey(rawTemplateKey);
        Objects.requireNonNull(id, "id do laudo e obrigatorio");
        Objects.requireNonNull(tenantId, "tenantId e obrigatorio");
        Objects.requireNonNull(laboratoryId, "laboratoryId e obrigatorio");
        if (issuedByRef == null || issuedByRef.isBlank())
            throw new IllegalArgumentException("issuedByRef e obrigatorio (quem emite o laudo)");
        Objects.requireNonNull(issuedAt, "issuedAt e obrigatorio");
        requireReportable(tenantId, laboratoryId, assays);
        List<String> payload = payloadOf(assays, null);
        String renderedRef = engine.render(tenantId, templateKey, payload);
        if (renderedRef == null || renderedRef.isBlank())
            throw new IllegalStateException("o motor da P2.30 nao devolveu a referencia do laudo gerado");
        List<UUID> ids = new ArrayList<>();
        for (AssayExecution a : assays) ids.add(a.id());
        return new LabReport(id, tenantId, laboratoryId, ids, templateKey, renderedRef,
                issuedByRef, issuedAt, 1, null, null);
    }

    /**
     * Emenda rastreavel: nasce a versao seguinte deste laudo (mesmos
     * ensaios, salvo lista nova validada quando fornecida), com
     * motivo obrigatorio; esta versao fica SUPERSEDED, preservada.
     * A emenda tambem e renderizada pelo motor da P2.30, com a linha
     * EMENDA no conteudo.
     */
    public LabReport amend(Lims17025Activation activation,
                           LimsPorts.LabReportTemplatePort engine,
                           UUID newId, List<AssayExecution> assays,
                           String reason, String amendedByRef, Instant amendedAt) {
        Objects.requireNonNull(activation, "activation e obrigatoria");
        activation.requireEnabled();
        Objects.requireNonNull(engine, "engine (motor da P2.30) e obrigatorio");
        if (status != Status.ISSUED)
            throw new IllegalStateException("laudo " + id + " ja substituido (SUPERSEDED): "
                    + "emenda-se a versao vigente, nao uma versao historica");
        Objects.requireNonNull(newId, "id da nova versao e obrigatorio");
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("emenda de laudo exige o motivo (rastreabilidade)");
        if (amendedByRef == null || amendedByRef.isBlank())
            throw new IllegalArgumentException("amendedByRef e obrigatorio (quem emenda)");
        Objects.requireNonNull(amendedAt, "amendedAt e obrigatorio");
        requireReportable(tenantId, laboratoryId, assays);
        List<String> payload = payloadOf(assays, reason);
        String renderedRef = engine.render(tenantId, templateKey, payload);
        if (renderedRef == null || renderedRef.isBlank())
            throw new IllegalStateException("o motor da P2.30 nao devolveu a referencia da emenda gerada");
        List<UUID> ids = new ArrayList<>();
        for (AssayExecution a : assays) ids.add(a.id());
        LabReport next = new LabReport(newId, tenantId, laboratoryId, ids, templateKey, renderedRef,
                amendedByRef, amendedAt, version + 1, id, reason);
        this.status = Status.SUPERSEDED;
        return next;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID laboratoryId() { return laboratoryId; }
    public List<UUID> assayIds() { return assayIds; }
    public String templateKey() { return templateKey; }
    public String renderedRef() { return renderedRef; }
    public String issuedByRef() { return issuedByRef; }
    public Instant issuedAt() { return issuedAt; }
    public int version() { return version; }
    public UUID supersedesId() { return supersedesId; }
    public String amendmentReason() { return amendmentReason; }
    public Status status() { return status; }
}
