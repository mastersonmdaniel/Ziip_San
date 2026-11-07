package br.com.ziipsan.commercial.collection.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Rota de webhook Pix (P3.2, V6_3): o segmento aleatorio da URL identifica
 * tenant, convenio e PSP. O callback so e aceito se o convenio do payload
 * conferir com {@code agreementCode} cadastrado nesta rota (conferencia
 * sempre, inclusive quando o payload omite o campo - correcao M2).
 *
 * <p><b>Entropia do segmento (M2):</b> o cadastro de rota DEVE gerar o
 * segmento por {@code WebhookSegmentPolicy.generate()} (SecureRandom,
 * 48 simbolos de 64 = 288 bits) - nunca sequencial, nunca derivado de dado
 * cadastral. O CHECK da V6_3 so garante formato (32-64 caracteres); a
 * exigencia de entropia esta registrada naquela politica e aplicada pelo
 * processador a cada callback.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados.
 */
@Entity
@Table(name = "pix_webhook_routes")
@Getter
@Setter
public class PixWebhookRouteEntity extends TenantAwareEntity {

    @Column(name = "segment", nullable = false, length = 64)
    private String segment;

    @Column(name = "psp", nullable = false, length = 40)
    private String psp;

    @Column(name = "agreement_code", nullable = false, length = 20)
    private String agreementCode;

    /** CNPJ do recebedor (aceita alfanumerico, P1.39). */
    @Column(name = "receiver_document", nullable = false, length = 14)
    private String receiverDocument;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
