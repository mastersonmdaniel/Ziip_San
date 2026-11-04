package br.com.ziipsan.commercial.registry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Alerta de óbito do titular (P1.41, V3_8; ADR-0042).
 *
 * <p>O alerta NUNCA gera efeito automático: ele abre uma OS HOLDER_DECEASED
 * para revisão humana. Só a confirmação por um revisor (diferente de quem
 * reportou) fecha o vínculo do usuário por falecimento, abre o vínculo do
 * responsável provisório e marca a unidade conforme o caso. A rejeição
 * encerra a OS sem execução e não muda nada no cadastro. Os bloqueios de
 * corte e de negativação do titular falecido são da P3.4 e da ED-30, que
 * consomem o alerta CONFIRMED daqui — esta frente não os aplica.
 *
 * <p>O alerta é por unidade quando há unidade (um titular pode ter várias
 * unidades; cada sucessão é revista à parte). Sem unidade (ex.: óbito
 * detectado pela validação externa de um cliente sem vínculo vigente), o
 * alerta registra o fato e a confirmação não tem efeito de vínculo.
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de
 * {@code BaseEntity}.
 */
@Entity
@Table(name = "holder_death_alerts")
@Getter
@Setter
public class HolderDeathAlert extends TenantAwareEntity {

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "unit_id")
    private UUID unitId;

    @Column(name = "source", nullable = false, length = 25)
    private String source;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING_REVIEW";

    @Column(name = "service_order_id")
    private UUID serviceOrderId;

    @Column(name = "detected_at", nullable = false)
    private OffsetDateTime detectedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    /** Fontes do alerta. SIRC é valor reservado: o cliente Sirc é frente futura. */
    public static final class Sources {
        public static final String EXTERNAL_VALIDATION = "EXTERNAL_VALIDATION";
        public static final String SIRC = "SIRC";
        public static final String MANUAL_REPORT = "MANUAL_REPORT";

        private Sources() {
        }
    }
}
