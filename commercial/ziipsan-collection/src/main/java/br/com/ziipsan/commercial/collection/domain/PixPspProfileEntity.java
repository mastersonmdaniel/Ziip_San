package br.com.ziipsan.commercial.collection.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Matriz de comportamento por PSP (P3.2, V6_3), lida pelo hub da P3.37.
 * Sem linha para o PSP, a aplicacao falha com erro explicito: comportamento
 * de PSP nunca e presumido.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados.
 */
@Entity
@Table(name = "pix_psp_profiles")
@Getter
@Setter
public class PixPspProfileEntity extends TenantAwareEntity {

    @Column(name = "psp", nullable = false, length = 40)
    private String psp;

    @Column(name = "callback_resend_policy", nullable = false, length = 200)
    private String callbackResendPolicy;

    @Column(name = "callback_sla_seconds", nullable = false)
    private int callbackSlaSeconds;

    @Column(name = "certificate_delivery", nullable = false, length = 40)
    private String certificateDelivery;

    @Column(name = "supports_additional_secret", nullable = false)
    private boolean supportsAdditionalSecret;

    /** Mecanismo equivalente ao GET /pix/{e2eid}; nulo = PSP nao oferece. */
    @Column(name = "equivalent_confirmation", length = 80)
    private String equivalentConfirmation;

    /** Capacidade do provedor: iniciacao Open Finance com JSR (nao torna ninguem participante). */
    @Column(name = "open_finance_jsr", nullable = false)
    private boolean openFinanceJsr;

    /** Capacidade do provedor: Pix por aproximacao. */
    @Column(name = "pix_proximity", nullable = false)
    private boolean pixProximity;
}
