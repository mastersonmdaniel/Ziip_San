package br.com.ziipsan.commercial.registry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Historico de mudancas de situacao de agua ou esgoto (somente insercao).
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "unit_status_history")
@Getter
@Setter
public class UnitStatusHistory extends TenantAwareEntity {

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_kind", nullable = false, length = 5)
    private ServiceKind serviceKind;

    @Column(name = "from_status_id")
    private UUID fromStatusId;

    @Column(name = "to_status_id", nullable = false)
    private UUID toStatusId;

    @Column(name = "changed_at", nullable = false)
    private java.time.OffsetDateTime changedAt;

    @Column(name = "reason", length = 200)
    private String reason;

    /**
     * P1.31 (V9_1): FK para service_orders, NULL admitido ate a P1.3.
     * ADR-0039 item 6 quer todo historico nascido da OS que causou a
     * mudanca, mas o plano reserva o service_order_id obrigatorio para a
     * V3_6/P1.3, quando todo registro direto passa a rotear por OS
     * interna. Ate la, a escrita direta (ex.: criar o imovel escreve o
     * historico inicial sem OS) continua valida; o FK ja recusa uma OS
     * inexistente quando o historico carrega uma. A obrigatoriedade
     * (NOT NULL) chega na P1.3 junto com o roteamento — nao aqui.
     */
    @Column(name = "service_order_id")
    private UUID serviceOrderId;

    public enum ServiceKind { WATER, SEWER }

}
