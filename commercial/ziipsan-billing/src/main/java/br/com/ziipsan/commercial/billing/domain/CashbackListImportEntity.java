package br.com.ziipsan.commercial.billing.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Importacao da lista de domicilios elegiveis da RFB (V4_12, P2.34): origem, data e versao declaradas de cada importacao (Decreto 12.955/2026, art. 511). Sem proveniencia nao ha importacao: as colunas de origem sao NOT NULL na migration.
 *
 * <p>Nao usar @Builder: o Lombok descarta os campos herdados de BaseEntity.
 */
@Entity
@Table(name = "cashback_list_imports")
@Getter
@Setter
public class CashbackListImportEntity extends TenantAwareEntity {
    @Column(name = "source_origin", nullable = false, length = 100)
    private String sourceOrigin;

    @Column(name = "source_reference", length = 200)
    private String sourceReference;

    @Column(name = "imported_on", nullable = false)
    private LocalDate importedOn;

    @Column(name = "list_version", nullable = false, length = 50)
    private String listVersion;

    @Column(name = "legal_basis", nullable = false, length = 200)
    private String legalBasis;

    @Column(name = "record_count", nullable = false)
    private int recordCount;

    @Column(name = "status", nullable = false, length = 20)
    private String status;
}
