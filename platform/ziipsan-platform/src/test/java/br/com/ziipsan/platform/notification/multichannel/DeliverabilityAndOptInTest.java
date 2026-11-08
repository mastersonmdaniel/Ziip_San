package br.com.ziipsan.platform.notification.multichannel;

import br.com.ziipsan.platform.notification.NotificationCategory;
import br.com.ziipsan.platform.notification.NotificationChannel;
import br.com.ziipsan.platform.notification.NotificationPreference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Entregabilidade calculada à mão: 200 envios, 190 entregues, 7 falhas,
 * 3 rejeitados -> entrega 0,9500 e falha 0,0500. E a regra do opt-in.
 */
class DeliverabilityAndOptInTest {

    private static final UUID TENANT = MultichannelTestSupport.TENANT;
    private static final Instant FROM = Instant.parse("2026-03-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-03-31T23:59:59Z");

    @Test
    @DisplayName("taxas de entrega e de falha das contagens do período")
    void ratesFromCounts() {
        DeliverabilityRule.DeliverabilitySnapshot s = DeliverabilityRule.snapshot(
                TENANT, NotificationChannel.WHATSAPP, FROM, TO, 200, 190, 7, 3);
        assertThat(s.deliveryRate()).isPresent();
        assertThat(s.deliveryRate().get()).isEqualByComparingTo("0.9500");
        assertThat(s.failureRate().get()).isEqualByComparingTo("0.0500");
    }

    @Test
    @DisplayName("sem envios no período, a taxa não existe (não é 0% nem 100%)")
    void zeroSendsHasNoRate() {
        DeliverabilityRule.DeliverabilitySnapshot s = DeliverabilityRule.snapshot(
                TENANT, NotificationChannel.SMS, FROM, TO, 0, 0, 0, 0);
        assertThat(s.deliveryRate()).isEmpty();
        assertThat(s.failureRate()).isEmpty();
    }

    @Test
    @DisplayName("resolvidas acima dos envios é erro explícito")
    void resolvedAboveSentRejected() {
        assertThatThrownBy(() -> DeliverabilityRule.snapshot(
                TENANT, NotificationChannel.SMS, FROM, TO, 10, 8, 2, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("contagem negativa é recusada")
    void negativeCountsRejected() {
        assertThatThrownBy(() -> DeliverabilityRule.snapshot(
                TENANT, NotificationChannel.SMS, FROM, TO, -1, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("opt-in: categoria obrigatória ignora linha desligada")
    void mandatoryIgnoresOffRow() {
        NotificationPreference off = MultichannelTestSupport.preference(
                MultichannelTestSupport.CUSTOMER, "LEGAL", NotificationCategory.LEGAL_NOTICE,
                NotificationChannel.SMS, false);
        assertThat(OptInRule.decide(true, List.of(off)))
                .isEqualTo(OptInRule.OptInDecision.ALLOWED_MANDATORY);
    }

    @Test
    @DisplayName("opt-in: linha ligada permite")
    void enabledRowAllows() {
        NotificationPreference on = MultichannelTestSupport.preference(
                MultichannelTestSupport.CUSTOMER, "BILLING", NotificationCategory.BILLING,
                NotificationChannel.SMS, true);
        assertThat(OptInRule.decide(false, List.of(on)))
                .isEqualTo(OptInRule.OptInDecision.ALLOWED_OPT_IN);
    }

    @Test
    @DisplayName("opt-in: sem linha nenhuma não há opt-in registrado")
    void noRowsMeansMissing() {
        assertThat(OptInRule.decide(false, List.of()))
                .isEqualTo(OptInRule.OptInDecision.OPT_IN_MISSING);
    }

    @Test
    @DisplayName("opt-in: linha desligada vence linha ligada (leitura mais restritiva)")
    void offRowWinsOverOnRow() {
        NotificationPreference on = MultichannelTestSupport.preference(
                MultichannelTestSupport.CUSTOMER, "BILLING", NotificationCategory.BILLING,
                NotificationChannel.WHATSAPP, true);
        NotificationPreference off = MultichannelTestSupport.preference(
                MultichannelTestSupport.CUSTOMER, "BILLING", NotificationCategory.BILLING,
                NotificationChannel.WHATSAPP, false);
        assertThat(OptInRule.decide(false, List.of(on, off)))
                .isEqualTo(OptInRule.OptInDecision.PREFERENCE_OFF);
    }

    @Test
    @DisplayName("opt-in: categoria obrigatória sem linha nenhuma também envia")
    void mandatoryWithoutRowsAllows() {
        assertThat(OptInRule.decide(true, List.of()))
                .isEqualTo(OptInRule.OptInDecision.ALLOWED_MANDATORY);
    }
}
