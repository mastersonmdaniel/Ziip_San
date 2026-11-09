package br.com.ziipsan.core.certification.lims17025;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Validacao de metodo (aceite da ED-51): o metodo so esta validado
 * para um parametro quando existe o DOSSIÊ da validacao
 * ({@code dossierRef}) e o registro de quem validou e quando — sem
 * dossie, nao ha metodo validado (o registro nem nasce).
 *
 * <p>Caracteristicas de desempenho quando o dossie as declara:
 * limite de deteccao (LD) e limite de quantificacao (LQ) — campos
 * que o levantamento 18g ja atribuia ao metodo de analise
 * ({@code LabAnalysisMethod} com LD, LQ e incerteza). LD/LQ sao
 * opcionais individualmente, mas quando ambos existem vale a
 * coerencia fisica {@code LQ >= LD}: dossie que declara o contrario
 * e erro, nao dado.
 *
 * <p>So com a norma ligada: o registro passa pela porta da
 * {@link Lims17025Activation}.
 */
public record MethodValidation(String methodRef, String parameterKey,
                               BigDecimal detectionLimit, BigDecimal quantificationLimit,
                               String dossierRef, String validatedByRef, LocalDate validatedOn) {

    public MethodValidation {
        if (methodRef == null || methodRef.isBlank())
            throw new IllegalArgumentException("methodRef e obrigatorio");
        if (parameterKey == null || parameterKey.isBlank())
            throw new IllegalArgumentException("parameterKey e obrigatorio");
        if (detectionLimit != null && detectionLimit.signum() < 0)
            throw new IllegalArgumentException("detectionLimit (LD) nao pode ser negativo");
        if (quantificationLimit != null && quantificationLimit.signum() < 0)
            throw new IllegalArgumentException("quantificationLimit (LQ) nao pode ser negativo");
        if (detectionLimit != null && quantificationLimit != null
                && quantificationLimit.compareTo(detectionLimit) < 0) {
            throw new IllegalArgumentException("quantificationLimit (LQ) nao pode ser menor que "
                    + "detectionLimit (LD): dossie incoerente");
        }
        if (dossierRef == null || dossierRef.isBlank())
            throw new IllegalArgumentException("dossierRef e obrigatoria: sem o dossie da validacao, "
                    + "nao ha metodo validado");
        if (validatedByRef == null || validatedByRef.isBlank())
            throw new IllegalArgumentException("validatedByRef e obrigatorio (quem validou o metodo)");
        Objects.requireNonNull(validatedOn, "validatedOn e obrigatorio (quando o metodo foi validado)");
    }

    /** Registro da validacao: so com a norma ligada (porta da ativacao). */
    public static MethodValidation validated(Lims17025Activation activation, String methodRef,
                                             String parameterKey, BigDecimal detectionLimit,
                                             BigDecimal quantificationLimit, String dossierRef,
                                             String validatedByRef, LocalDate validatedOn) {
        Objects.requireNonNull(activation, "activation e obrigatoria");
        activation.requireEnabled();
        return new MethodValidation(methodRef, parameterKey, detectionLimit, quantificationLimit,
                dossierRef, validatedByRef, validatedOn);
    }
}
