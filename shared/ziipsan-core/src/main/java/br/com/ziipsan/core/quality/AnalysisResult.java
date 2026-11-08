package br.com.ziipsan.core.quality;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Resultado de laboratorio de UMA amostra e UM parametro, como o
 * laboratorio o reporta (o valor NUNCA e presumido nem convertido em
 * silencio: numerico e qualitativo sao formas distintas e excludentes).
 *
 * <p>Regras:
 * <ul>
 *   <li>so nasce sobre amostra RECEBIDA pelo laboratorio (JOR-37);</li>
 *   <li>versao 1 nasce por digitacao/transcricao do boletim; versao
 *       seguinte (correcao por supervisor) exige justificativa e
 *       referencia a versao substituida — o historico nao e apagado
 *       (SG-813 do levantamento 18g);</li>
 *   <li>validacao e por pessoa diferente de quem lancou quando o
 *       tenant assim operar — aqui o nucleo exige apenas o registro
 *       de quem validou; a regra de segregacao e da fiacao/ED-51.</li>
 * </ul>
 *
 * <p><b>Correcao M-1 (revisao da ED-50):</b> esta classe era um
 * {@code record} de construtor publico — o portao JOR-37 vivia so na
 * fabrica {@link #firstVersion} e a sonda B6 da revisao criou um
 * resultado sobre amostra {@code PLANNED} nunca recebida chamando o
 * construtor direto. Correcao: classe final SELADA, com construtor
 * PRIVADO — a unica via de nascimento e {@code firstVersion} (que
 * exige a {@link Sample} recebida), e {@code corrected}/{@code validate}
 * derivam de uma instancia que ja nasceu pelo portao. A API de leitura
 * (acessores no estilo do record, {@code equals}/{@code hashCode} por
 * valor) e preservada, sem quebrar os usos legitimos. A reidratacao
 * pela integracao (JPA) e pendencia P-ED50-02 e deve reconstruir
 * somente resultados ja lancados pelo portao, a partir da linha
 * persistida da propria amostra recebida.
 */
public final class AnalysisResult {

    private final UUID id;
    private final UUID sampleId;
    private final String parameterKey;
    private final BigDecimal numericValue;
    private final String qualitativeValue;
    private final String unit;
    private final String methodRef;
    private final String analystRef;
    private final Instant analyzedAt;
    private final int version;
    private final UUID supersedesId;
    private final String correctionJustification;
    private final boolean validated;
    private final String validatorRef;

    private AnalysisResult(UUID id, UUID sampleId, String parameterKey,
                           BigDecimal numericValue, String qualitativeValue, String unit,
                           String methodRef, String analystRef, Instant analyzedAt,
                           int version, UUID supersedesId, String correctionJustification,
                           boolean validated, String validatorRef) {
        if (id == null) throw new IllegalArgumentException("id do resultado e obrigatorio");
        if (sampleId == null) throw new IllegalArgumentException("sampleId do resultado e obrigatorio");
        if (parameterKey == null || parameterKey.isBlank())
            throw new IllegalArgumentException("parameterKey do resultado e obrigatorio");
        boolean numeric = numericValue != null;
        boolean qualitative = qualitativeValue != null && !qualitativeValue.isBlank();
        if (numeric == qualitative)
            throw new IllegalArgumentException("resultado de " + parameterKey
                    + " exige exatamente uma forma: numerica OU qualitativa, como reportada");
        if (analystRef == null || analystRef.isBlank())
            throw new IllegalArgumentException("analista do resultado e obrigatorio para " + parameterKey);
        if (analyzedAt == null)
            throw new IllegalArgumentException("instante da analise e obrigatorio para " + parameterKey);
        if (version < 1) throw new IllegalArgumentException("versao do resultado comeca em 1");
        if (version == 1 && (supersedesId != null || correctionJustification != null))
            throw new IllegalArgumentException("versao 1 nao substitui versao anterior");
        if (version > 1) {
            if (supersedesId == null)
                throw new IllegalArgumentException("correcao de resultado exige a versao substituida");
            if (correctionJustification == null || correctionJustification.isBlank())
                throw new IllegalArgumentException("correcao de resultado exige justificativa (SG-813)");
        }
        if (validated && (validatorRef == null || validatorRef.isBlank()))
            throw new IllegalArgumentException("resultado validado exige quem validou");
        if (!validated && validatorRef != null)
            throw new IllegalArgumentException("validatorRef so existe em resultado validado");
        this.id = id;
        this.sampleId = sampleId;
        this.parameterKey = parameterKey;
        this.numericValue = numericValue;
        this.qualitativeValue = qualitativeValue;
        this.unit = unit;
        this.methodRef = methodRef;
        this.analystRef = analystRef;
        this.analyzedAt = analyzedAt;
        this.version = version;
        this.supersedesId = supersedesId;
        this.correctionJustification = correctionJustification;
        this.validated = validated;
        this.validatorRef = validatorRef;
    }

    /** Fabrica UNICA com o portao da custodia: a amostra precisa ter sido recebida pelo laboratorio. */
    public static AnalysisResult firstVersion(UUID id, Sample sample, String parameterKey,
                                              BigDecimal numericValue, String qualitativeValue, String unit,
                                              String methodRef, String analystRef, Instant analyzedAt) {
        if (sample == null) throw new IllegalArgumentException("amostra e obrigatoria");
        if (!sample.receivedByLab())
            throw new IllegalStateException("resultado recusado: a amostra " + sample.labelCode()
                    + " ainda nao foi recebida pelo laboratorio (JOR-37)");
        return new AnalysisResult(id, sample.id(), parameterKey, numericValue, qualitativeValue,
                unit, methodRef, analystRef, analyzedAt, 1, null, null, false, null);
    }

    public AnalysisResult corrected(UUID newId, BigDecimal newNumeric, String newQualitative,
                                    String justification, String analystRef, Instant at) {
        return new AnalysisResult(newId, sampleId, parameterKey, newNumeric, newQualitative,
                unit, methodRef, analystRef, at, version + 1, id, justification, false, null);
    }

    public AnalysisResult validate(String validator) {
        if (validated) throw new IllegalStateException("resultado ja validado");
        return new AnalysisResult(id, sampleId, parameterKey, numericValue, qualitativeValue,
                unit, methodRef, analystRef, analyzedAt, version, supersedesId,
                correctionJustification, true, validator);
    }

    public UUID id() { return id; }
    public UUID sampleId() { return sampleId; }
    public String parameterKey() { return parameterKey; }
    public BigDecimal numericValue() { return numericValue; }
    public String qualitativeValue() { return qualitativeValue; }
    public String unit() { return unit; }
    public String methodRef() { return methodRef; }
    public String analystRef() { return analystRef; }
    public Instant analyzedAt() { return analyzedAt; }
    public int version() { return version; }
    public UUID supersedesId() { return supersedesId; }
    public String correctionJustification() { return correctionJustification; }
    public boolean validated() { return validated; }
    public String validatorRef() { return validatorRef; }

    public boolean isNumeric() { return numericValue != null; }

    /** Resultado como texto, na forma reportada (para bloco da conta, portal e SISAGUA). */
    public String resultText() {
        return isNumeric() ? numericValue.toPlainString() : qualitativeValue;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AnalysisResult other)) return false;
        return version == other.version && validated == other.validated
                && Objects.equals(id, other.id) && Objects.equals(sampleId, other.sampleId)
                && Objects.equals(parameterKey, other.parameterKey)
                && Objects.equals(numericValue, other.numericValue)
                && Objects.equals(qualitativeValue, other.qualitativeValue)
                && Objects.equals(unit, other.unit) && Objects.equals(methodRef, other.methodRef)
                && Objects.equals(analystRef, other.analystRef)
                && Objects.equals(analyzedAt, other.analyzedAt)
                && Objects.equals(supersedesId, other.supersedesId)
                && Objects.equals(correctionJustification, other.correctionJustification)
                && Objects.equals(validatorRef, other.validatorRef);
    }

    @Override public int hashCode() {
        return Objects.hash(id, sampleId, parameterKey, numericValue, qualitativeValue, unit,
                methodRef, analystRef, analyzedAt, version, supersedesId,
                correctionJustification, validated, validatorRef);
    }

    @Override public String toString() {
        return "AnalysisResult[id=" + id + ", sampleId=" + sampleId + ", parameterKey=" + parameterKey
                + ", numericValue=" + numericValue + ", qualitativeValue=" + qualitativeValue
                + ", unit=" + unit + ", methodRef=" + methodRef + ", analystRef=" + analystRef
                + ", analyzedAt=" + analyzedAt + ", version=" + version
                + ", supersedesId=" + supersedesId
                + ", correctionJustification=" + correctionJustification
                + ", validated=" + validated + ", validatorRef=" + validatorRef + "]";
    }
}
