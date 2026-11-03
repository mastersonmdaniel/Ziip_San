package br.com.ziipsan.commercial.billing.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Lote importado da lista oficial de tarifa social (V4_2; {@code social.base_import} e
 * {@code social.list_source}). {@code purgeAt} e o prazo de retencao do lote
 * ({@code ret.social_base_days}); nulo ate o tenant definir o parametro.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "social_base_imports")
@Getter
@Setter
public class SocialBaseImportEntity extends TenantAwareEntity {

    @Column(name = "list_source", nullable = false, length = 20)
    private String listSource;

    @Column(name = "layout_version", nullable = false, length = 50)
    private String layoutVersion;

    @Column(name = "received_on", nullable = false)
    private LocalDate receivedOn;

    @Column(name = "record_count", nullable = false)
    private int recordCount;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "purge_at")
    private LocalDate purgeAt;
}
