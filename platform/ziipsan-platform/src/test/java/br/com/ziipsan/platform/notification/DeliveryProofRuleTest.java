package br.com.ziipsan.platform.notification;

import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regras puras da prova de entrega (P3.5), calculadas à mão:
 * taxa de reclamação — 9 reclamações em 10.000 envios = 0,09% (abaixo de 0,10%, ok);
 * 10 em 10.000 = 0,10% (atingiu o teto: o requisito é ficar ABAIXO, logo viola);
 * 1 em 100 = 1% (viola).
 */
class DeliveryProofRuleTest {

    private static final Instant T0 = Instant.parse("2026-03-10T13:00:00Z");

    private static DeliveryAttempt attempt(DeliveryStatus status) {
        return new DeliveryAttempt(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), null, UUID.randomUUID(), NotificationChannel.EMAIL,
                "destinatario@example.com", "bill.cutoff-notice", 1, status, "provider-1", T0, null);
    }

    @Test
    @DisplayName("REGRA DO ACEITE: aviso de corte sem prova de entrega BLOQUEIA a OS (409) — sem tentativa, só enviada, rejeitada ou reclamada")
    void cutoffBlockedWithoutProof() {
        assertThatThrownBy(() -> DeliveryProofRule.assertCutoffAllowed(List.of()))
                .isInstanceOf(ApiException.Conflict.class);
        assertThatThrownBy(() -> DeliveryProofRule.assertCutoffAllowed(List.of(attempt(DeliveryStatus.SENT))))
                .isInstanceOf(ApiException.Conflict.class);
        // Rejeição e reclamação contam como aviso NÃO entregue.
        assertThatThrownBy(() -> DeliveryProofRule.assertCutoffAllowed(List.of(attempt(DeliveryStatus.BOUNCED))))
                .isInstanceOf(ApiException.Conflict.class);
        assertThatThrownBy(() -> DeliveryProofRule.assertCutoffAllowed(List.of(attempt(DeliveryStatus.COMPLAINED))))
                .isInstanceOf(ApiException.Conflict.class);
    }

    @Test
    @DisplayName("com uma tentativa DELIVERED entre as demais, há prova e o corte é liberado")
    void deliveredAttemptIsProof() {
        List<DeliveryAttempt> attempts = List.of(attempt(DeliveryStatus.BOUNCED), attempt(DeliveryStatus.DELIVERED));
        assertThat(DeliveryProofRule.hasProof(attempts)).isTrue();
        DeliveryProofRule.assertCutoffAllowed(attempts);
    }

    @Test
    @DisplayName("taxa de reclamação calculada à mão: 9/10.000 = 0,000900 (0,09%) não viola; 10/10.000 = 0,001000 (0,10%) viola; 1/100 viola")
    void complaintRateHandCalculated() {
        assertThat(DeliveryProofRule.complaintRate(10_000, 9)).isEqualByComparingTo("0.000900");
        assertThat(DeliveryProofRule.complaintRateBreaches(10_000, 9)).isFalse();
        assertThat(DeliveryProofRule.complaintRate(10_000, 10)).isEqualByComparingTo("0.001000");
        assertThat(DeliveryProofRule.complaintRateBreaches(10_000, 10)).isTrue();
        assertThat(DeliveryProofRule.complaintRateBreaches(100, 1)).isTrue();
        assertThat(DeliveryProofRule.complaintRateBreaches(10_000, 0)).isFalse();
    }

    @Test
    @DisplayName("sem envios no período a taxa não existe: erro explícito, nunca zero presumido")
    void complaintRateRequiresSends() {
        assertThatThrownBy(() -> DeliveryProofRule.complaintRate(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DeliveryProofRule.complaintRate(10, 11))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("domínio alinhado exige SPF, DKIM e DMARC no domínio configurado do tenant; qualquer desalinhamento recusa")
    void domainAlignment() {
        DeliveryProofRule.assertDomainAligned("envio.a.example",
                new SenderDomainStatus("envio.a.example", true, true, true));
        assertThatThrownBy(() -> DeliveryProofRule.assertDomainAligned("envio.a.example",
                new SenderDomainStatus("envio.a.example", true, false, true)))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> DeliveryProofRule.assertDomainAligned("envio.a.example",
                new SenderDomainStatus("envio.a.example", false, true, true)))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> DeliveryProofRule.assertDomainAligned("envio.a.example",
                new SenderDomainStatus("envio.a.example", true, true, false)))
                .isInstanceOf(ApiException.Validation.class);
        // Estado de OUTRO domínio não serve para este tenant.
        assertThatThrownBy(() -> DeliveryProofRule.assertDomainAligned("envio.a.example",
                new SenderDomainStatus("envio.b.example", true, true, true)))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("política de supressão: delivered nunca suprime; bounce/complaint suprimem só se a política do tenant mandar")
    void suppressionPolicySwitches() {
        SuppressionPolicy both = new SuppressionPolicy(true, true);
        assertThat(both.suppresses(EmailEventType.BOUNCED)).isTrue();
        assertThat(both.suppresses(EmailEventType.COMPLAINED)).isTrue();
        assertThat(both.suppresses(EmailEventType.DELIVERED)).isFalse();
        SuppressionPolicy complaintOnly = new SuppressionPolicy(false, true);
        assertThat(complaintOnly.suppresses(EmailEventType.BOUNCED)).isFalse();
        assertThat(complaintOnly.suppresses(EmailEventType.COMPLAINED)).isTrue();
    }
}
