package br.com.ziipsan.commercial.registry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Pendência de mudança de ocupação ou de nome social (P1.41, V3_8).
 *
 * <p>Por que não {@code cadastral_change_requests}: o CHECK de status
 * daquela tabela (V3_12) não tem REJECTED, e a V3_8 roda ANTES da V3_12 no
 * fresh install — não há como estendê-lo daqui. Esta tabela tem o mesmo
 * formato (alvo, antes/depois, OS, estado) com o estado REJECTED próprio.
 * old_value/new_value são REDACT na auditoria (o nome social é dado
 * pessoal), como em cadastral_change_requests.
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de
 * {@code BaseEntity}.
 */
@Entity
@Table(name = "holder_change_requests")
@Getter
@Setter
public class HolderChangeRequest extends TenantAwareEntity {

    @Column(name = "kind", nullable = false, length = 20)
    private String kind;

    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "old_value", columnDefinition = "text")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "text")
    private String newValue;

    @Column(name = "service_order_id")
    private UUID serviceOrderId;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING_APPROVAL";
}
