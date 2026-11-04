package br.com.ziipsan.commercial.registry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Alteração campo a campo do cadastro ligada à OS (P1.3, V3_12; contrato da
 * P1.31, mapa do 23b §3.2). Cada linha é um campo alterado, com o tipo de OS
 * do campo (ver {@code CadastralChangeService.FIELD_OS_TYPES}) e a OS que a
 * originou — nula até a OS existir, preenchida quando a escrita abre a OS
 * interna (ver {@code InternalOrderService}).
 *
 * <p>old_value/new_value NUNCA carregam documento em claro (só a máscara).
 * As duas colunas são REDACT na auditoria.
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "cadastral_change_requests")
@Getter
@Setter
public class CadastralChangeRequest extends TenantAwareEntity {

    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "field", nullable = false, length = 100)
    private String field;

    @Column(name = "old_value", columnDefinition = "text")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "text")
    private String newValue;

    @Column(name = "service_order_type", nullable = false, length = 60)
    private String serviceOrderType;

    @Column(name = "service_order_id")
    private UUID serviceOrderId;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

}
