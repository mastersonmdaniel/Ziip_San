package br.com.ziipsan.core.certification.lims17025;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Ensaio de proficiencia (programa interlaboratorial; aceite da
 * ED-51): a rodada e contratada com um PROVEDOR lido do cadastro de
 * fornecedor da P3.31 no papel {@code PROFICIENCY_PROVIDER} (JOR-18).
 *
 * <p>O escore-z e ARITMETICA sobre os dados da rodada:
 * {@code z = (valor do laboratorio - valor atribuido) / sigma do
 * programa}, onde o valor atribuido e o sigma sao dados DO PROGRAMA
 * (declarados pelo provedor na rodada). A AVALIACAO da rodada
 * ({@code providerEvaluation}) e registrada COMO O PROVEDOR A
 * REPORTOU — dado reportado, nao limiar codificado nesta classe:
 * nenhuma faixa de |z| nasce de fabrica aqui, porque nenhuma fonte
 * do projeto a fixa para este pacote.
 *
 * <p>So com a norma ligada: o registro passa pela porta da
 * {@link Lims17025Activation} e pela porta do fornecedor.
 */
public record ProficiencyTesting(UUID id, UUID tenantId, UUID providerSupplierId,
                                 String schemeRef, String roundRef,
                                 String methodRef, String parameterKey,
                                 BigDecimal assignedValue, BigDecimal schemeSigma,
                                 BigDecimal laboratoryValue,
                                 ProviderEvaluation providerEvaluation, LocalDate reportedOn) {

    /** Avaliacao COMO REPORTADA pelo provedor da rodada (dado, nao limiar desta classe). */
    public enum ProviderEvaluation { SATISFACTORY, QUESTIONABLE, UNSATISFACTORY }

    public ProficiencyTesting {
        if (id == null) throw new IllegalArgumentException("id da rodada de proficiencia e obrigatorio");
        if (tenantId == null) throw new IllegalArgumentException("tenantId e obrigatorio");
        if (providerSupplierId == null)
            throw new IllegalArgumentException("providerSupplierId e obrigatorio (provedor da P3.31, JOR-18)");
        if (schemeRef == null || schemeRef.isBlank())
            throw new IllegalArgumentException("schemeRef e obrigatorio (o programa interlaboratorial)");
        if (roundRef == null || roundRef.isBlank())
            throw new IllegalArgumentException("roundRef e obrigatorio (a rodada do programa)");
        if (methodRef == null || methodRef.isBlank())
            throw new IllegalArgumentException("methodRef e obrigatorio");
        if (parameterKey == null || parameterKey.isBlank())
            throw new IllegalArgumentException("parameterKey e obrigatorio");
        Objects.requireNonNull(assignedValue, "assignedValue (valor atribuido do programa) e obrigatorio");
        Objects.requireNonNull(schemeSigma, "schemeSigma (sigma do programa) e obrigatorio");
        if (schemeSigma.signum() <= 0)
            throw new IllegalArgumentException("schemeSigma deve ser maior que zero");
        Objects.requireNonNull(laboratoryValue, "laboratoryValue (valor do laboratorio) e obrigatorio");
        Objects.requireNonNull(providerEvaluation, "providerEvaluation e obrigatoria "
                + "(a avaliacao como reportada pelo provedor)");
        Objects.requireNonNull(reportedOn, "reportedOn e obrigatorio");
    }

    /**
     * Registro da rodada: so com a norma ligada e com o provedor
     * validado no cadastro da P3.31 no papel de provedor de ensaio
     * de proficiencia.
     */
    public static ProficiencyTesting register(Lims17025Activation activation,
                                              LimsPorts.SupplierCatalogPort catalog,
                                              UUID id, UUID tenantId, UUID providerSupplierId,
                                              String schemeRef, String roundRef,
                                              String methodRef, String parameterKey,
                                              BigDecimal assignedValue, BigDecimal schemeSigma,
                                              BigDecimal laboratoryValue,
                                              ProviderEvaluation providerEvaluation,
                                              LocalDate reportedOn) {
        Objects.requireNonNull(activation, "activation e obrigatoria");
        activation.requireEnabled();
        LabSuppliers.require(catalog, tenantId, providerSupplierId,
                LabSuppliers.Role.PROFICIENCY_PROVIDER);
        return new ProficiencyTesting(id, tenantId, providerSupplierId, schemeRef, roundRef,
                methodRef, parameterKey, assignedValue, schemeSigma, laboratoryValue,
                providerEvaluation, reportedOn);
    }

    /** Escore-z da rodada: (valor do laboratorio - valor atribuido) / sigma do programa. */
    public BigDecimal zScore() {
        return laboratoryValue.subtract(assignedValue)
                .divide(schemeSigma, 4, RoundingMode.HALF_UP);
    }
}
