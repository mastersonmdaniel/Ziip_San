package br.com.ziipsan.core.certification.lims17025;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Ensaio executado por LABORATORIO EXTERNO (aceite da ED-51): quando
 * o ensaio e subcontratado, o laboratorio externo e LIDO do cadastro
 * de fornecedor da P3.31 no papel {@code EXTERNAL_LABORATORY}
 * (JOR-18) e o resultado entra SEMPRE com a referencia do laudo do
 * laboratorio externo ({@code externalReportRef}) — resultado de
 * terceiro sem o laudo dele nao entra.
 *
 * <p>A amostra continua sendo a da ED-50, referenciada por id; a
 * cadeia de custodia ate o laboratorio externo e a da ED-50.
 *
 * <p>So com a norma ligada: o registro passa pela porta da
 * {@link Lims17025Activation} e pela porta do fornecedor.
 */
public record ExternalLabAssay(UUID id, UUID tenantId, UUID sampleId, UUID externalLabSupplierId,
                               String methodRef, String parameterKey, String externalReportRef,
                               LocalDate receivedOn) {

    public ExternalLabAssay {
        if (id == null) throw new IllegalArgumentException("id do ensaio externo e obrigatorio");
        if (tenantId == null) throw new IllegalArgumentException("tenantId e obrigatorio");
        if (sampleId == null) throw new IllegalArgumentException("sampleId e obrigatorio (amostra da ED-50, por id)");
        if (externalLabSupplierId == null)
            throw new IllegalArgumentException("externalLabSupplierId e obrigatorio (laboratorio externo da P3.31, JOR-18)");
        if (methodRef == null || methodRef.isBlank())
            throw new IllegalArgumentException("methodRef e obrigatorio");
        if (parameterKey == null || parameterKey.isBlank())
            throw new IllegalArgumentException("parameterKey e obrigatorio");
        if (externalReportRef == null || externalReportRef.isBlank())
            throw new IllegalArgumentException("externalReportRef e obrigatoria: resultado de "
                    + "laboratorio externo so entra com o laudo dele referenciado");
        Objects.requireNonNull(receivedOn, "receivedOn e obrigatorio (quando o laudo externo foi recebido)");
    }

    /**
     * Registro do ensaio externo: so com a norma ligada e com o
     * laboratorio externo validado no cadastro da P3.31.
     */
    public static ExternalLabAssay register(Lims17025Activation activation,
                                            LimsPorts.SupplierCatalogPort catalog,
                                            UUID id, UUID tenantId, UUID sampleId,
                                            UUID externalLabSupplierId, String methodRef,
                                            String parameterKey, String externalReportRef,
                                            LocalDate receivedOn) {
        Objects.requireNonNull(activation, "activation e obrigatoria");
        activation.requireEnabled();
        LabSuppliers.require(catalog, tenantId, externalLabSupplierId,
                LabSuppliers.Role.EXTERNAL_LABORATORY);
        return new ExternalLabAssay(id, tenantId, sampleId, externalLabSupplierId, methodRef,
                parameterKey, externalReportRef, receivedOn);
    }
}
