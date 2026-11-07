package br.com.ziipsan.commercial.billing.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Domicilio elegivel de uma importacao da lista da RFB (V4_12, P2.34), por periodo de afericao. O domicilio e identificado SOMENTE pelo indice cego domicileKeyHash (hash HMAC-SHA-256, como na V4_2): nenhum documento em claro.
 *
 * <p>Nao usar @Builder: o Lombok descarta os campos herdados de BaseEntity.
 */
@Entity
@Table(name = "cashback_list_entries")
@Getter
@Setter
public class CashbackListEntryEntity extends TenantAwareEntity {
    @Column(name = "import_id", nullable = false)
    private UUID importId;

    @Column(name = "domicile_key_hash", nullable = false, length = 64)
    private String domicileKeyHash;

    @Column(name = "assessment_month", nullable = false)
    private LocalDate assessmentMonth;

    @Column(name = "matched_unit_id")
    private UUID matchedUnitId;
}
