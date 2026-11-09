package br.com.ziipsan.core.certification.lims17025;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * O ENSAIO demonstrado (aceite da ED-51): a execucao de um metodo
 * sobre uma amostra da ED-50 (referenciada por id — a amostra, a
 * cadeia de custodia e o resultado operacional sao da ED-50; este
 * ensaio e a camada de apoio a ISO/IEC 17025 SOBRE essa execucao).
 *
 * <p>O nascimento do ensaio exige, TODOS demonstrados no ato:
 * <ol>
 *   <li>a norma ligada ({@link Lims17025Activation}, ADR-0041);</li>
 *   <li>o par (metodo, parametro) dentro do ESCOPO declarado do
 *       laboratorio ({@link Laboratory});</li>
 *   <li>o metodo VALIDADO para o parametro ({@link MethodValidation}
 *       — sem dossie de validacao, nao ha ensaio);</li>
 *   <li>o analista AUTORIZADO para aquele metodo na data
 *       ({@link AnalystAuthorization});</li>
 *   <li>o equipamento UTILIZAVEL na data — calibracao vigente, com
 *       historico; equipamento vencido BLOQUEIA o ensaio
 *       ({@link LabEquipment#requireUsableOn});</li>
 *   <li>as condicoes ambientais avaliadas: veredito
 *       {@code OUT_OF_LIMITS} BLOQUEIA o ensaio; {@code NOT_DECLARED}
 *       nao bloqueia, mas fica registrado no ensaio
 *       ({@link EnvironmentalControl});</li>
 *   <li>o controle de WESTGARD da corrida liberando resultados:
 *       {@code OUT_OF_CONTROL} BLOQUEIA o ensaio; {@code WARNING}
 *       (1_2s) libera e fica registrado ({@link WestgardControl});</li>
 *   <li>o orcamento de INCERTEZA do par (metodo, parametro), com
 *       fator k e regra de decisao declarados
 *       ({@link MeasurementUncertainty});</li>
 *   <li>material de referencia, quando usado, dentro da validade
 *       ({@link ReferenceMaterial#requireUsableOn}).</li>
 * </ol>
 *
 * <p>O resultado e registrado como reportado (numerico OU
 * qualitativo, formas excludentes — mesma disciplina da ED-50) e a
 * VALIDACAO e por SEGUNDA PESSOA: quem validou nao pode ser quem
 * executou (a ED-50 registrava o validador e adiava expressamente a
 * regra de segregacao para a ED-51 — ela vive aqui). Ensaio nao
 * validado nao entra em laudo ({@link LabReport}).
 *
 * <p>Maquina de estados: IN_PROGRESS -&gt; RESULT_RECORDED -&gt;
 * VALIDATED, com marcacao de trabalho nao conforme possivel a partir
 * de RESULT_RECORDED (o registro do trabalho nao conforme em si e o
 * {@link NonconformingWork}).
 */
public final class AssayExecution {

    public enum Status { IN_PROGRESS, RESULT_RECORDED, VALIDATED }

    private final UUID id;
    private final UUID tenantId;
    private final UUID sampleId;
    private final UUID laboratoryId;
    private final String methodRef;
    private final String parameterKey;
    private final String analystRef;
    private final String equipmentCode;
    private final LocalDate analysisDate;
    private final EnvironmentalControl.Verdict environmentalVerdict;
    private final WestgardControl.Status qcStatus;
    private final List<WestgardControl.Rule> qcViolatedRules;
    private final MeasurementUncertainty uncertainty;
    private final UUID referenceMaterialId;

    private Status status = Status.IN_PROGRESS;
    private BigDecimal numericValue;
    private String qualitativeValue;
    private String unit;
    private Instant recordedAt;
    private String validatorRef;
    private String nonconformityNote;

    private AssayExecution(UUID id, UUID tenantId, UUID sampleId, UUID laboratoryId,
                           String methodRef, String parameterKey, String analystRef,
                           String equipmentCode, LocalDate analysisDate,
                           EnvironmentalControl.Verdict environmentalVerdict,
                           WestgardControl.Evaluation qc,
                           MeasurementUncertainty uncertainty, UUID referenceMaterialId) {
        this.id = id;
        this.tenantId = tenantId;
        this.sampleId = sampleId;
        this.laboratoryId = laboratoryId;
        this.methodRef = methodRef;
        this.parameterKey = parameterKey;
        this.analystRef = analystRef;
        this.equipmentCode = equipmentCode;
        this.analysisDate = analysisDate;
        this.environmentalVerdict = environmentalVerdict;
        this.qcStatus = qc.status();
        this.qcViolatedRules = qc.violatedRules();
        this.uncertainty = uncertainty;
        this.referenceMaterialId = referenceMaterialId;
    }

    /**
     * Nascimento do ensaio com TODAS as portas do aceite verificadas
     * no ato (ver javadoc da classe). Qualquer porta fechada recusa o
     * nascimento com a causa nomeada — nao existe ensaio "parcial".
     */
    public static AssayExecution start(Lims17025Activation activation, Laboratory laboratory,
                                       MethodValidation methodValidation,
                                       List<AnalystAuthorization> authorizations,
                                       LabEquipment equipment,
                                       EnvironmentalControl.Evaluation environmental,
                                       WestgardControl.Evaluation qc,
                                       MeasurementUncertainty uncertainty,
                                       ReferenceMaterial referenceMaterial,
                                       UUID id, UUID tenantId, UUID sampleId,
                                       String methodRef, String parameterKey,
                                       String analystRef, LocalDate analysisDate) {
        Objects.requireNonNull(activation, "activation e obrigatoria");
        activation.requireEnabled();
        Objects.requireNonNull(laboratory, "laboratory e obrigatorio");
        Objects.requireNonNull(id, "id do ensaio e obrigatorio");
        Objects.requireNonNull(tenantId, "tenantId e obrigatorio");
        Objects.requireNonNull(sampleId, "sampleId e obrigatorio (amostra da ED-50, por id)");
        if (methodRef == null || methodRef.isBlank())
            throw new IllegalArgumentException("methodRef e obrigatorio");
        if (parameterKey == null || parameterKey.isBlank())
            throw new IllegalArgumentException("parameterKey e obrigatorio");
        if (analystRef == null || analystRef.isBlank())
            throw new IllegalArgumentException("analystRef e obrigatorio");
        Objects.requireNonNull(analysisDate, "analysisDate e obrigatoria");

        if (!laboratory.tenantId().equals(tenantId))
            throw new IllegalArgumentException("laboratorio de outro tenant");
        laboratory.requireInScope(methodRef, parameterKey);

        Objects.requireNonNull(methodValidation, "methodValidation e obrigatoria: "
                + "sem validacao de metodo nao ha ensaio");
        if (!methodValidation.methodRef().equals(methodRef)
                || !methodValidation.parameterKey().equals(parameterKey)) {
            throw new IllegalArgumentException("a validacao apresentada nao e do par do ensaio: "
                    + methodValidation.methodRef() + " / " + methodValidation.parameterKey()
                    + " != " + methodRef + " / " + parameterKey);
        }

        boolean authorized = authorizations != null && authorizations.stream()
                .anyMatch(a -> a.covers(analystRef, methodRef, analysisDate));
        if (!authorized) {
            throw new IllegalStateException("analista " + analystRef + " sem autorizacao vigente "
                    + "para o metodo " + methodRef + " em " + analysisDate
                    + " (autorizacao e por metodo)");
        }

        Objects.requireNonNull(equipment, "equipment e obrigatorio");
        if (!equipment.tenantId().equals(tenantId))
            throw new IllegalArgumentException("equipamento de outro tenant");
        equipment.requireUsableOn(analysisDate);

        Objects.requireNonNull(environmental, "avaliacao ambiental e obrigatoria");
        if (environmental.verdict() == EnvironmentalControl.Verdict.OUT_OF_LIMITS) {
            throw new IllegalStateException("ensaio bloqueado: condicoes ambientais fora dos "
                    + "limites declarados — " + environmental.violations());
        }

        Objects.requireNonNull(qc, "avaliacao de Westgard da corrida e obrigatoria");
        if (!qc.releasesResults()) {
            throw new IllegalStateException("ensaio bloqueado: controle de qualidade fora de "
                    + "controle pelas regras de Westgard — " + qc.violatedRules());
        }

        Objects.requireNonNull(uncertainty, "orcamento de incerteza e obrigatorio: "
                + "sem incerteza nao ha ensaio demonstrado");
        if (!uncertainty.methodRef().equals(methodRef)
                || !uncertainty.parameterKey().equals(parameterKey)) {
            throw new IllegalArgumentException("o orcamento de incerteza nao e do par do ensaio: "
                    + uncertainty.methodRef() + " / " + uncertainty.parameterKey()
                    + " != " + methodRef + " / " + parameterKey);
        }

        UUID refMatId = null;
        if (referenceMaterial != null) {
            if (!referenceMaterial.tenantId().equals(tenantId))
                throw new IllegalArgumentException("material de referencia de outro tenant");
            referenceMaterial.requireUsableOn(analysisDate);
            refMatId = referenceMaterial.id();
        }

        return new AssayExecution(id, tenantId, sampleId, laboratory.id(), methodRef, parameterKey,
                analystRef, equipment.code(), analysisDate, environmental.verdict(), qc,
                uncertainty, refMatId);
    }

    /** Registro do resultado como reportado: numerico OU qualitativo, nunca os dois. */
    public void recordResult(BigDecimal numeric, String qualitative, String unit, Instant at) {
        if (status != Status.IN_PROGRESS)
            throw new IllegalStateException("ensaio " + id + ": resultado exige estado IN_PROGRESS, "
                    + "estado atual " + status);
        // R-01(i) (correcao 2026-11-09): qualitativo em branco conta como
        // ausente — mesma direcao do R-01 (btrim(...) <> '' no SQL). Sem
        // esta normalizacao, recordResult(numeric, "   ", ...) passava na
        // guarda (isBlank) e gravava o branco verbatim, e o CHECK XOR do
        // SQL lia a string nao-nula como resultado presente. O branco e
        // normalizado para null ANTES da guarda e do armazenamento, por
        // isso nunca chega a qualitativeValue nem a base.
        String normalizedQualitative =
                (qualitative != null && qualitative.isBlank()) ? null : qualitative;
        boolean isNumeric = numeric != null;
        boolean isQualitative = normalizedQualitative != null;
        if (isNumeric == isQualitative)
            throw new IllegalArgumentException("resultado do ensaio exige exatamente uma forma: "
                    + "numerica OU qualitativa, como reportada");
        Objects.requireNonNull(at, "instante do registro do resultado e obrigatorio");
        this.numericValue = numeric;
        this.qualitativeValue = normalizedQualitative;
        this.unit = unit;
        this.recordedAt = at;
        this.status = Status.RESULT_RECORDED;
    }

    /**
     * Validacao por SEGUNDA PESSOA: quem validou nao pode ser quem
     * executou o ensaio. E a regra que a ED-50 adiou para esta tarefa.
     */
    public void validateBySecondPerson(String validator) {
        if (status != Status.RESULT_RECORDED)
            throw new IllegalStateException("ensaio " + id + ": validacao exige resultado "
                    + "registrado, estado atual " + status);
        if (validator == null || validator.isBlank())
            throw new IllegalArgumentException("validatorRef e obrigatorio (a segunda pessoa)");
        if (validator.equals(analystRef))
            throw new IllegalArgumentException("validacao por segunda pessoa: quem validou ("
                    + validator + ") nao pode ser quem executou o ensaio");
        this.validatorRef = validator;
        this.status = Status.VALIDATED;
    }

    /** Marcacao de trabalho nao conforme sobre este ensaio (o registro formal e o NonconformingWork). */
    public void flagNonconforming(String note) {
        if (status == Status.IN_PROGRESS)
            throw new IllegalStateException("ensaio " + id + ": trabalho nao conforme so se "
                    + "marca sobre ensaio com resultado registrado");
        if (note == null || note.isBlank())
            throw new IllegalArgumentException("a marcacao de nao conformidade exige a descricao");
        this.nonconformityNote = note;
    }

    public boolean validated() { return status == Status.VALIDATED; }
    public boolean isNumeric() { return numericValue != null; }
    /** Resultado como texto, na forma reportada (para o laudo). */
    public String resultText() {
        if (status == Status.IN_PROGRESS) return null;
        return isNumeric() ? numericValue.toPlainString() : qualitativeValue;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID sampleId() { return sampleId; }
    public UUID laboratoryId() { return laboratoryId; }
    public String methodRef() { return methodRef; }
    public String parameterKey() { return parameterKey; }
    public String analystRef() { return analystRef; }
    public String equipmentCode() { return equipmentCode; }
    public LocalDate analysisDate() { return analysisDate; }
    public EnvironmentalControl.Verdict environmentalVerdict() { return environmentalVerdict; }
    public WestgardControl.Status qcStatus() { return qcStatus; }
    public List<WestgardControl.Rule> qcViolatedRules() { return qcViolatedRules; }
    public MeasurementUncertainty uncertainty() { return uncertainty; }
    public UUID referenceMaterialId() { return referenceMaterialId; }
    public Status status() { return status; }
    public BigDecimal numericValue() { return numericValue; }
    public String qualitativeValue() { return qualitativeValue; }
    public String unit() { return unit; }
    public Instant recordedAt() { return recordedAt; }
    public String validatorRef() { return validatorRef; }
    public String nonconformityNote() { return nonconformityNote; }
}
