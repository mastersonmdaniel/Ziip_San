package br.com.ziipsan.commercial.billing.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Registro de acesso a lista de domicilios do cashback (V4_12, P2.34): cada acesso registrado como no padrao G2 do aceite — quem, quando, tipo e finalidade.
 *
 * <p>Nao usar @Builder: o Lombok descarta os campos herdados de BaseEntity.
 */
@Entity
@Table(name = "cashback_list_accesses")
@Getter
@Setter
public class CashbackListAccessEntity extends TenantAwareEntity {
    @Column(name = "import_id")
    private UUID importId;

    @Column(name = "domicile_key_hash", length = 64)
    private String domicileKeyHash;

    @Column(name = "accessed_by", nullable = false)
    private UUID accessedBy;

    @Column(name = "accessed_at", nullable = false)
    private OffsetDateTime accessedAt;

    @Column(name = "access_type", nullable = false, length = 30)
    private String accessType;

    @Column(name = "purpose", nullable = false, length = 200)
    private String purpose;
}
