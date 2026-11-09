package br.com.ziipsan.core.certification.lims17025;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Padrao / material de referencia (aceite da ED-51): material com
 * valor certificado e incerteza certificada (como o certificado dele
 * declara — dados do certificado, nunca presumidos), lote, validade
 * e fornecedor LIDO do cadastro da P3.31 no papel
 * {@code REFERENCE_MATERIAL_SUPPLIER} (JOR-18).
 *
 * <p>Material vencido esta bloqueado para uso como referencia
 * ({@link #requireUsableOn}): rastreabilidade com material expirado
 * nao sustenta ensaio. A validade e data-limite inclusiva.
 *
 * <p>So com a norma ligada: o registro passa pela porta da
 * {@link Lims17025Activation} e pela porta do fornecedor
 * ({@link LabSuppliers}).
 */
public record ReferenceMaterial(UUID id, UUID tenantId, String code, String description,
                                String lotRef, BigDecimal certifiedValue,
                                BigDecimal certifiedUncertainty, String unit,
                                LocalDate expiresOn, UUID supplierId, String certificateRef) {

    public ReferenceMaterial {
        if (id == null) throw new IllegalArgumentException("id do material de referencia e obrigatorio");
        if (tenantId == null) throw new IllegalArgumentException("tenantId e obrigatorio");
        if (code == null || code.isBlank())
            throw new IllegalArgumentException("code do material de referencia e obrigatorio");
        if (description == null || description.isBlank())
            throw new IllegalArgumentException("description do material de referencia e obrigatoria");
        if (lotRef == null || lotRef.isBlank())
            throw new IllegalArgumentException("lotRef e obrigatorio (o lote do material)");
        Objects.requireNonNull(certifiedValue, "certifiedValue e obrigatorio (valor do certificado)");
        Objects.requireNonNull(certifiedUncertainty, "certifiedUncertainty e obrigatoria "
                + "(incerteza do certificado)");
        if (certifiedUncertainty.signum() < 0)
            throw new IllegalArgumentException("certifiedUncertainty nao pode ser negativa");
        Objects.requireNonNull(expiresOn, "expiresOn e obrigatorio (validade do material)");
        if (supplierId == null)
            throw new IllegalArgumentException("supplierId e obrigatorio (fornecedor da P3.31, JOR-18)");
        if (certificateRef == null || certificateRef.isBlank())
            throw new IllegalArgumentException("certificateRef e obrigatoria (referencia do certificado)");
    }

    /**
     * Registro do material: so com a norma ligada e com o fornecedor
     * validado no cadastro da P3.31 no papel de fornecedor de material
     * de referencia.
     */
    public static ReferenceMaterial register(Lims17025Activation activation,
                                             LimsPorts.SupplierCatalogPort catalog,
                                             UUID id, UUID tenantId, String code, String description,
                                             String lotRef, BigDecimal certifiedValue,
                                             BigDecimal certifiedUncertainty, String unit,
                                             LocalDate expiresOn, UUID supplierId,
                                             String certificateRef) {
        Objects.requireNonNull(activation, "activation e obrigatoria");
        activation.requireEnabled();
        LabSuppliers.require(catalog, tenantId, supplierId,
                LabSuppliers.Role.REFERENCE_MATERIAL_SUPPLIER);
        return new ReferenceMaterial(id, tenantId, code, description, lotRef, certifiedValue,
                certifiedUncertainty, unit, expiresOn, supplierId, certificateRef);
    }

    /** Verdadeiro quando o material esta dentro da validade na data (limite inclusivo). */
    public boolean usableOn(LocalDate date) {
        Objects.requireNonNull(date, "date e obrigatoria");
        return !date.isAfter(expiresOn);
    }

    /** Porta de uso: material de referencia vencido nao sustenta ensaio. */
    public void requireUsableOn(LocalDate date) {
        if (!usableOn(date)) {
            throw new IllegalStateException("material de referencia " + code + " (lote " + lotRef
                    + ") BLOQUEADO em " + date + ": validade vencida em " + expiresOn);
        }
    }
}
